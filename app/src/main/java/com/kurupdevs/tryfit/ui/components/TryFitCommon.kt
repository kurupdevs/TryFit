package com.kurupdevs.tryfit.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurupdevs.tryfit.TryFitApplication
import com.kurupdevs.tryfit.di.AppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import java.io.File

/** Grab the [AppContainer] from any composable. */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember {
        (context.applicationContext as TryFitApplication).container
    }
}

/** ViewModel factory bound to the [AppContainer] (manual DI, no Hilt). */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer = rememberAppContainer(),
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            create(container) as T
    }
)

/**
 * Shimmer sweep modifier (SPEC §4 missing-states). ONE shared infinite
 * transition per screen — call sites share the caller's transition where
 * possible; this helper is for standalone shimmer blocks.
 */
fun Modifier.tryFitShimmer(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this
    val transition = rememberInfiniteTransition(label = "tryfitShimmer")
    val x = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    background(
        Brush.linearGradient(
            colors = listOf(
                TryFitColors.BgCanvas,
                Color.White,
                TryFitColors.BgCanvas,
            ),
            start = Offset(x.value * 300f, 0f),
            end = Offset(x.value * 300f + 300f, 300f),
        )
    )
}

/**
 * The honest label — shown on EVERY try-on surface (research v1 item 9,
 * bloat-trap #1). Never "this will fit you".
 */
@Composable
fun AiPreviewLabel(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "AI preview — style preview, not a fit guarantee",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/**
 * Honest result header — overlaid on the try-on result (research v1 item 9).
 * Shows the garment title plus the honest "AI preview" framing. Never claims fit.
 */
@Composable
fun HonestResultHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        AiPreviewLabel()
    }
}

/**
 * Privacy consent sheet shown BEFORE the camera the first time (research §3:
 * on-device-first, 7-day auto-delete, never train, one-tap delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyConsentSheet(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TryFitColors.BgScreen,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Your photos stay yours",
                style = MaterialTheme.typography.titleLarge,
                color = TryFitColors.TextPrimary,
            )
            Spacer(Modifier.height(16.dp))
            ConsentRow(
                icon = Icons.Filled.CheckCircle,
                text = "Processed on your phone first — nothing leaves the device until you start a try-on.",
            )
            ConsentRow(
                icon = Icons.Filled.Timer,
                text = "Raw uploads auto-delete after 7 days. Your avatar stays until you remove it.",
            )
            ConsentRow(
                icon = Icons.Filled.Delete,
                text = "Never used for training, never shared. Delete everything anytime in Settings → Privacy.",
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAccept,
                shape = TryFitRadii.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TryFitColors.TextPrimary,
                    contentColor = TryFitColors.TextOnPhoto,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(text = "I understand — open camera", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(
                    text = "Not now",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ConsentRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TryFitColors.TextPrimary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Shares an image file + text through the system share sheet (FileProvider). */
fun shareImageFile(context: Context, file: File, text: String, title: String = "Share") {
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

/** Copies [file] into the FileProvider-shared cache dir and returns it. */
fun copyToSharedCache(context: Context, file: File, name: String): File {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    return File(dir, name).also { dst ->
        file.inputStream().use { input -> dst.outputStream().use { input.copyTo(it) } }
    }
}
