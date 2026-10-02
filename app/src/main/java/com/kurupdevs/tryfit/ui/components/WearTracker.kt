package com.kurupdevs.tryfit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Cost-per-wear tracker (lite). Worker E: place on the wardrobe item detail
 * screen. [itemId] accepts catalog product ids and closet item ids alike.
 */
@Composable
fun WearTrackerCard(
    itemId: String,
    priceInr: Int,
    modifier: Modifier = Modifier
) {
    val wear = rememberAppContainer().wear
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val counts by wear.wearCounts.collectAsState(initial = emptyMap())
    val wears = counts[itemId] ?: 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TryFitColors.BgCanvas, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = if (wears == 0) "Not worn yet" else "Worn $wears ${if (wears == 1) "time" else "times"}",
                style = MaterialTheme.typography.titleSmall,
                color = TryFitColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Cost per wear: ${wear.costPerWearLabel(priceInr, wears)}",
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextSecondary
            )
        }
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch { wear.markWorn(itemId) }
            },
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto
            )
        ) {
            Text("+1 Wore it", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Outfit calendar (lite): simple month grid, dots on worn dates, tap to
 * toggle. Compact — sits under [WearTrackerCard] on the item detail.
 */
@Composable
fun OutfitCalendar(
    itemId: String,
    modifier: Modifier = Modifier
) {
    val wear = rememberAppContainer().wear
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var month by remember { mutableStateOf(YearMonth.now()) }
    val datesByItem by wear.wornDates.collectAsState(initial = emptyMap())
    val worn = datesByItem[itemId] ?: emptySet()

    val firstDow = month.atDay(1).dayOfWeek.value % 7 // Sunday-first columns
    val daysInMonth = month.lengthOfMonth()
    val cells = buildList {
        repeat(firstDow) { add(null) }
        for (d in 1..daysInMonth) add(month.atDay(d))
    }
    val today = LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TryFitColors.BgCanvas, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleLarge,
                color = TryFitColors.TextSecondary,
                modifier = Modifier
                    .clickable { month = month.minusMonths(1) }
                    .padding(8.dp)
            )
            Text(
                text = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.year,
                style = MaterialTheme.typography.titleSmall,
                color = TryFitColors.TextPrimary
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = TryFitColors.TextSecondary,
                modifier = Modifier
                    .clickable { month = month.plusMonths(1) }
                    .padding(8.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = TryFitColors.TextSecondary,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            userScrollEnabled = false
        ) {
            items(cells, key = { it?.toString() ?: "pad-${cells.indexOf(it)}" }) { date ->
                if (date == null) {
                    Spacer(Modifier.aspectRatio(1f))
                } else {
                    val key = date.toString()
                    val isWorn = key in worn
                    val isToday = date == today
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isWorn -> TryFitColors.OrbViolet.copy(alpha = 0.18f)
                                    isToday -> Color.White
                                    else -> Color.Transparent
                                }
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { wear.toggleDate(itemId, key) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isToday) TryFitColors.OrbViolet else TryFitColors.TextPrimary
                            )
                            if (isWorn) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(TryFitColors.OrbViolet, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = "Tap a date to mark when you wore it",
            style = MaterialTheme.typography.bodySmall,
            color = TryFitColors.TextSecondary
        )
    }
}
