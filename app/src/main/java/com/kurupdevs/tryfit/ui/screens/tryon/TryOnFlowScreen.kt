package com.kurupdevs.tryfit.ui.screens.tryon

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.tryon.GarmentSlot
import com.kurupdevs.tryfit.tryon.CameraCaptureScreen
import com.kurupdevs.tryfit.tryon.loadProductImageBytes
import com.kurupdevs.tryfit.tryon.toGarmentSlot
import com.kurupdevs.tryfit.ui.components.containerViewModel
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full try-on flow (SPEC §3.4): source picker → garment confirm →
 * processing → result. Also handles the notification deep-link restore
 * (`resultSessionId`) and screenshot-garment entry (`screenshot`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnFlowScreen(
    productId: String?,
    screenshot: Boolean,
    resultSessionId: String?,
    onBack: () -> Unit,
) {
    val container = rememberAppContainer()
    val context = LocalContext.current
    val vm: TryOnFlowViewModel = containerViewModel { TryOnFlowViewModel(it) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var screenshotBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun readUriBytes(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            if (vm.step == FlowStep.Camera) vm.goToSource()
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val bytes = readUriBytes(uri)
            withContext(Dispatchers.Main) {
                if (bytes != null) vm.setPhoto(context, bytes)
                else scope.launch { snackbar.showSnackbar("Couldn't read that image — try another.") }
            }
        }
    }

    val screenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            vm.goToSource()
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            val bytes = readUriBytes(uri)
            withContext(Dispatchers.Main) {
                if (bytes != null) screenshotBytes = bytes
                else {
                    scope.launch { snackbar.showSnackbar("Couldn't read that image — try another.") }
                    vm.goToSource()
                }
            }
        }
    }

    fun launchScreenshotPicker() {
        vm.goToCrop()
        screenshotLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // ---- Entry wiring ----
    LaunchedEffect(resultSessionId) {
        if (!resultSessionId.isNullOrBlank()) {
            vm.restoreSession(context, resultSessionId)
        }
    }
    LaunchedEffect(productId) {
        if (!productId.isNullOrBlank() && vm.garments.isEmpty()) {
            val product: Product? = runCatching { container.products.byId(productId) }.getOrNull()
            if (product != null) {
                val bytes = withContext(Dispatchers.IO) {
                    loadProductImageBytes(context, product)
                }
                if (bytes != null) {
                    vm.addGarment(
                        SelectedGarment(
                            productId = product.id,
                            name = "${product.brand} ${product.name}",
                            slot = product.category.toGarmentSlot().let {
                                // Bags drape like tops in the demo composite.
                                if (it == GarmentSlot.OTHER) GarmentSlot.TOP else it
                            },
                            imageBytes = bytes,
                        )
                    )
                }
            }
        }
    }
    LaunchedEffect(screenshot) {
        if (screenshot && screenshotBytes == null && vm.step == FlowStep.Source) {
            launchScreenshotPicker()
        }
    }
    LaunchedEffect(vm.guardMessage) {
        vm.guardMessage?.let { scope.launch { snackbar.showSnackbar(it) } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val step = vm.step) {
            FlowStep.Source -> TryOnSourceStep(
                vm = vm,
                onTakePhoto = { vm.goToCamera() },
                onGallery = { galleryLauncher.launch("image/*") },
                onScreenshot = { launchScreenshotPicker() },
                onBack = onBack,
            )
            FlowStep.Camera -> CameraCaptureScreen(
                onPhotoCaptured = { file ->
                    scope.launch(Dispatchers.IO) {
                        val bytes = runCatching { file.readBytes() }.getOrNull()
                        withContext(Dispatchers.Main) {
                            if (bytes != null) vm.setPhoto(context, bytes)
                            else vm.goToSource()
                        }
                    }
                },
                onFallbackToGallery = { galleryLauncher.launch("image/*") },
                onClose = { vm.goToSource() },
            )
            FlowStep.CropScreenshot -> {
                val bytes = screenshotBytes
                if (bytes != null) {
                    CropGarmentScreen(
                        imageBytes = bytes,
                        onCropped = { cropped ->
                            vm.addGarment(
                                SelectedGarment(
                                    productId = null,
                                    name = "Screenshot find",
                                    slot = GarmentSlot.TOP,
                                    imageBytes = cropped,
                                )
                            )
                            screenshotBytes = null
                            vm.goToSource()
                        },
                        onCancel = {
                            screenshotBytes = null
                            vm.goToSource()
                        },
                    )
                } else {
                    // Picker dismissed without a result — back to source options.
                    vm.goToSource()
                }
            }
            FlowStep.QualityCoach -> QualityCoachStep(vm = vm, onBack = onBack)
            FlowStep.Confirm -> TryOnConfirmStep(
                vm = vm,
                onStart = { vm.startTryOn(context) },
                onBack = { vm.goToSource() },
                onExit = onBack,
            )
            FlowStep.Processing -> TryOnProcessingStep(
                vm = vm,
                onCancel = { vm.cancel() },
                onRetry = { vm.retry(context) },
                onExit = onBack,
            )
            FlowStep.Result -> TryOnResultStep(
                vm = vm,
                onRetry = { vm.retry(context) },
                onExit = onBack,
            )
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
