package com.kurupdevs.tryfit.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kurupdevs.tryfit.data.catalog.formatInr
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import kotlinx.coroutines.launch

/**
 * Favorite toggle + "ping me under ₹X" target-price control.
 * Worker D: place on the product detail screen; the 24h WorkManager check
 * (PriceDropWorker) and notification channel are already wired in
 * TryFitApplication.
 */
@Composable
fun PriceAlertRow(
    productId: String,
    currentPriceInr: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = rememberAppContainer().priceAlerts
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val favorites by store.favorites.collectAsState(initial = emptyList())
    val fav = favorites.firstOrNull { it.productId == productId }
    val isFav = fav != null
    var alertsOn by remember(fav) { mutableStateOf(fav?.targetInr != null) }
    var targetText by remember(fav) { mutableStateOf(fav?.targetInr?.toString() ?: "") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; worker stays silent without it */ }

    LaunchedEffect(alertsOn) {
        if (alertsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch { store.toggleFavorite(productId) }
                }) {
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFav) "Remove from favorites" else "Add to favorites",
                        tint = if (isFav) Color(0xFFFF3B30) else TryFitColors.TextSecondary
                    )
                }
                Text(
                    text = if (isFav) "Saved" else "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = TryFitColors.TextPrimary
                )
            }
            if (isFav) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Price alert",
                        style = MaterialTheme.typography.labelMedium,
                        color = TryFitColors.TextSecondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = alertsOn,
                        onCheckedChange = { on ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            alertsOn = on
                            if (!on) scope.launch { store.setTarget(productId, null) }
                        }
                    )
                }
            }
        }

        if (isFav && alertsOn) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ping me under ₹",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TryFitColors.TextPrimary
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { c -> c.isDigit() }.take(6) },
                    modifier = Modifier.width(110.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TryFitColors.BgCanvas,
                        unfocusedContainerColor = TryFitColors.BgCanvas,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("999") }
                )
                Spacer(Modifier.width(8.dp))
                val target = targetText.toIntOrNull()
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { store.setTarget(productId, target) }
                    },
                    enabled = target != null && target > 0 && target < currentPriceInr,
                    shape = TryFitRadii.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TryFitColors.TextPrimary,
                        contentColor = TryFitColors.TextOnPhoto
                    )
                ) {
                    Text("Set", style = MaterialTheme.typography.labelMedium)
                }
            }
            val saved = fav?.targetInr
            if (saved != null) {
                Text(
                    text = "We'll ping you when it drops under ${formatInr(saved)} (now ${formatInr(currentPriceInr)}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TryFitColors.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
