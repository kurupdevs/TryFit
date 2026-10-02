package com.kurupdevs.tryfit.tryon

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CameraX photo capture for the try-on source picker.
 *
 * GRACEFUL FALLBACK (per task spec): every CameraX interaction is wrapped so
 * a missing camera, denied permission, or binding failure NEVER strands the
 * user — [onFallbackToGallery] switches to the gallery picker instead. This
 * screen is only reached after the privacy consent sheet (research §3).
 */
@Composable
fun CameraCaptureScreen(
    onPhotoCaptured: (File) -> Unit,
    onFallbackToGallery: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionState by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var bindError by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) permissionState = true else onFallbackToGallery()
    }

    LaunchedEffect(Unit) {
        if (!permissionState) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Any CameraX failure -> gallery fallback (never a stranded black screen).
    LaunchedEffect(bindError) {
        if (bindError) onFallbackToGallery()
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get()
                    .unbindAll()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (permissionState && !bindError) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        bindCamera(
                            context = ctx,
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            onCaptureReady = { imageCapture = it },
                            onError = { bindError = true },
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(color = TryFitColors.TextOnPhoto)
        }

        // Close
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close camera",
                tint = Color.White,
            )
        }

        // Shutter
        IconButton(
            onClick = {
                val capture = imageCapture ?: run {
                    bindError = true
                    return@IconButton
                }
                if (capturing) return@IconButton
                capturing = true
                val outputDir = File(context.cacheDir, "tryon_flow").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(outputDir, "capture_$stamp.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            capturing = false
                            onPhotoCaptured(file)
                        }

                        override fun onError(exc: ImageCaptureException) {
                            capturing = false
                            // Capture failed -> gallery fallback, not a dead end.
                            onFallbackToGallery()
                        }
                    },
                )
            },
            enabled = !capturing,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(76.dp)
                .background(Color.White.copy(alpha = 0.25f), CircleShape)
                .padding(6.dp)
                .background(Color.White, CircleShape),
        ) {
            if (capturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = TryFitColors.TextPrimary,
                    strokeWidth = 3.dp,
                )
            }
        }

        Text(
            text = "Full-body, facing the camera",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp),
        )
    }
}

private fun bindCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onCaptureReady: (ImageCapture) -> Unit,
    onError: () -> Unit,
) {
    try {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    onCaptureReady(capture)
                } catch (_: Exception) {
                    onError()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    } catch (_: Exception) {
        onError()
    }
}
