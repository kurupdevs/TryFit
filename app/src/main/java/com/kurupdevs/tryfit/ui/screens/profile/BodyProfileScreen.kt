package com.kurupdevs.tryfit.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kurupdevs.tryfit.data.BodyProfile
import com.kurupdevs.tryfit.data.BodyBuild
import com.kurupdevs.tryfit.data.FitIntent
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Body profile — height, build, chest, waist + fit intent.
 *
 * BLOAT TRAP COMPLIANCE: this "helps size suggestions — never a fit
 * guarantee". No slimming/beautify claims anywhere; the values only tune
 * size-chip suggestions, not visuals.
 */
@Composable
fun BodyProfileScreen(onBack: () -> Unit) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saved by container.bodyProfileStore.profile
        .collectAsState(initial = BodyProfile())

    var heightCm by remember(saved) { mutableFloatStateOf((saved.heightCm ?: 165).toFloat()) }
    var chestCm by remember(saved) { mutableFloatStateOf((saved.chestCm ?: 95).toFloat()) }
    var waistCm by remember(saved) { mutableFloatStateOf((saved.waistCm ?: 80).toFloat()) }
    var build by remember(saved) { mutableStateOf(saved.build) }
    var fitIntent by remember(saved) { mutableStateOf(saved.fitIntent) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TryFitColors.BgScreen,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = TryFitSpacing.ScreenPadding, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TryFitColors.TextPrimary,
                    )
                }
                Text(
                    text = "Body profile",
                    style = MaterialTheme.typography.titleLarge,
                    color = TryFitColors.TextPrimary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TryFitSpacing.ScreenPadding),
        ) {
            // Honest framing: suggestions only, never a fit guarantee.
            Text(
                text = "Helps size suggestions — never a fit guarantee.",
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))

            ProfileSlider(
                label = "Height",
                value = heightCm,
                range = 140f..200f,
                valueText = "${heightCm.roundToInt()} cm",
                onChange = { heightCm = it },
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Build",
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            ChipRow(
                options = listOf("Slim", "Average", "Athletic", "Plus"),
                selected = build.label,
                onSelect = { build = BodyBuild.from(it) },
            )
            Spacer(Modifier.height(16.dp))

            ProfileSlider(
                label = "Chest",
                value = chestCm,
                range = 70f..140f,
                valueText = "${chestCm.roundToInt()} cm",
                onChange = { chestCm = it },
            )
            Spacer(Modifier.height(12.dp))
            ProfileSlider(
                label = "Waist",
                value = waistCm,
                range = 55f..130f,
                valueText = "${waistCm.roundToInt()} cm",
                onChange = { waistCm = it },
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = "How do you like your fit?",
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            ChipRow(
                options = listOf("Slim", "Regular", "Oversized"),
                selected = fitIntent.label,
                onSelect = { fitIntent = FitIntent.from(it) },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Fit intent is used only to pre-select a size chip — you can change it every time.",
                style = MaterialTheme.typography.bodySmall,
                color = TryFitColors.TextSecondary,
            )
            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    saving = true
                    scope.launch(Dispatchers.IO) {
                        container.bodyProfileStore.save(
                            BodyProfile(
                                heightCm = heightCm.roundToInt(),
                                build = build,
                                chestCm = chestCm.roundToInt(),
                                waistCm = waistCm.roundToInt(),
                                fitIntent = fitIntent,
                            )
                        )
                        launch(Dispatchers.Main) {
                            saving = false
                            scope.launch { snackbar.showSnackbar("Body profile saved") }
                        }
                    }
                },
                enabled = !saving,
                shape = TryFitRadii.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TryFitColors.TextPrimary,
                    contentColor = TryFitColors.TextOnPhoto,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = if (saving) "Saving…" else "Save",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextSecondary,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = TryFitColors.TextPrimary,
                activeTrackColor = TryFitColors.TextPrimary,
                inactiveTrackColor = TryFitColors.BgCanvas,
            ),
        )
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.equals(selected, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) TryFitColors.TextPrimary else TryFitColors.TextSecondary,
                    )
                },
                shape = TryFitRadii.Pill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TryFitColors.SurfaceChipSelected,
                    containerColor = Color.Transparent,
                ),
                border = null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
