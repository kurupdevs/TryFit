package com.kurupdevs.tryfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing

/**
 * Phase 0 screen shell.
 *
 * Every Phase-0 stub screen uses this: real layout structure (top bar +
 * content area) with the feature's real content explicitly marked as
 * pending its build phase. This is honest scaffolding, not placeholder copy —
 * each screen's worker replaces [StubBody] with the real UI.
 */
@Composable
fun Phase0StubScreen(
    title: String,
    phaseNote: String,
    onBack: (() -> Unit)? = null,
    topBarActions: @Composable () -> Unit = {},
    content: @Composable () -> Unit = { StubBody(phaseNote) }
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = TryFitSpacing.ScreenPadding,
                        end = TryFitSpacing.ScreenPadding,
                        top = 12.dp,
                        bottom = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TryFitColors.TextPrimary
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TryFitColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                topBarActions()
            }
        },
        containerColor = TryFitColors.BgScreen
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            content()
        }
    }
}

/** Default stub body: a clearly-marked card, never lorem ipsum. */
@Composable
fun StubBody(phaseNote: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(TryFitSpacing.ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TryFitColors.BgCanvas, RoundedCornerShape(TryFitRadii.GridCard))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Phase 0 scaffold",
                style = MaterialTheme.typography.labelLarge,
                color = TryFitColors.TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = phaseNote,
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextSecondary
            )
        }
    }
}
