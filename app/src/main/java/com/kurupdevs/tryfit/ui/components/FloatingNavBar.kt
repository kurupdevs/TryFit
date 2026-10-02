package com.kurupdevs.tryfit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kurupdevs.tryfit.navigation.BottomTab
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing

/**
 * Dark floating pill bottom nav — SPEC §2/§3/§4.
 *
 * - [TryFitColors.NavBg] (#141416), 68dp tall, 16dp side margins, 12dp above
 *   the home indicator (offset applied by the caller).
 * - Shadow 0 10 28 rgba(0,0,0,0.28); never edge-docked.
 * - Center slot is the AI orb (gradient, breathing idle animation).
 * - Tab press: light haptic tick + icon pop 1.0 -> 1.18 -> 1.0 (spring 0.5).
 * - [visible] drives hide-on-scroll-down (callers observe scroll direction and
 *   pass false past the 12dp threshold); show on scroll-up.
 */
@Composable
fun FloatingNavBar(
    selectedTab: BottomTab,
    visible: Boolean,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TryFitSpacing.NavSideMargin)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .background(TryFitColors.NavBg, CircleShape)
                .height(TryFitSpacing.NavHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavIconButton(
                tab = BottomTab.HOME,
                icon = Icons.Filled.Home,
                contentDescription = "Home",
                selected = selectedTab == BottomTab.HOME,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTabSelected(BottomTab.HOME) }
            )
            NavIconButton(
                tab = BottomTab.TRY_ON,
                icon = Icons.Filled.Person,
                contentDescription = "Try on",
                selected = selectedTab == BottomTab.TRY_ON,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTabSelected(BottomTab.TRY_ON) }
            )
            OrbButton(
                selected = selectedTab == BottomTab.ASSISTANT,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTabSelected(BottomTab.ASSISTANT) }
            )
            NavIconButton(
                tab = BottomTab.WARDROBE,
                icon = Icons.Filled.ShoppingBag,
                contentDescription = "Wardrobe",
                selected = selectedTab == BottomTab.WARDROBE,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTabSelected(BottomTab.WARDROBE) }
            )
            NavIconButton(
                tab = BottomTab.SETTINGS,
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                selected = selectedTab == BottomTab.SETTINGS,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTabSelected(BottomTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun NavIconButton(
    tab: BottomTab,
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Icon pop 1.0 -> 1.18 -> 1.0 (spring damping 0.5) on selection.
    val scale = remember(tab) { Animatable(1f) }
    var firstRun by remember(tab) { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (firstRun) {
            firstRun = false
            return@LaunchedEffect
        }
        if (selected) {
            scale.animateTo(1.18f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
        }
    }

    val tint = if (selected) Color.White else Color.White.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .size(TryFitSpacing.NavIconTouchTarget) // 48dp touch target (a11y)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // Default LocalIndication (ripple) — no explicit indication needed.
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(TryFitSpacing.NavIconSize)
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
        )
    }
}

/**
 * Center AI orb. Idle: breathing scale 1.0 <-> 1.06 (2600ms) + glow pulse +
 * 8s gradient-rotation sheen, per SPEC §3.6. Press: ripple + haptic, then the
 * orb stretches into the stylist bottom sheet (320ms, handled in the nav
 * host). Loading state (1000ms conic sweep) lives in [OrbLoading].
 * One shared infinite transition for the whole nav bar (SPEC §5).
 */
@Composable
private fun OrbButton(
    selected: Boolean,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "orbIdle")
    val breathe by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(animation = tween(2600), repeatMode = RepeatMode.Reverse),
        label = "orbBreathe"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(animation = tween(2600), repeatMode = RepeatMode.Reverse),
        label = "orbGlow"
    )
    val sheenRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbSheen"
    )

    Box(
        modifier = Modifier
            .size(TryFitSpacing.NavIconTouchTarget)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // Default ripple on press (SPEC §3.6: orb press -> ripple).
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(TryFitSpacing.OrbSize - 8.dp)
                .graphicsLayer(scaleX = breathe, scaleY = breathe)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    spotColor = TryFitColors.OrbGlow.copy(alpha = glow)
                )
                .background(TryFitColors.OrbGradient, CircleShape)
                .clip(CircleShape)
        ) {
            // 8s gradient-rotation sheen: a rotating sweep highlight.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = sheenRotation }
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }
    }
}
