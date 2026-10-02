package com.kurupdevs.tryfit.ui.screens.assistant

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.catalog.formatInr
import com.kurupdevs.tryfit.data.stylist.StylistBrain
import com.kurupdevs.tryfit.ui.components.OrbLoading
import com.kurupdevs.tryfit.ui.components.rememberAppContainer
import com.kurupdevs.tryfit.ui.theme.TryFitColors
import com.kurupdevs.tryfit.ui.theme.TryFitRadii
import com.kurupdevs.tryfit.ui.theme.TryFitSpacing

private val QUICK_PROMPTS = listOf(
    "Style me for a wedding under ₹5,000",
    "What goes with this?",
    "Rate my fit",
    "Find my colors"
)

/**
 * AI stylist chat — SPEC §3.6. Shown as a bottom sheet stretched from the
 * center orb (320ms). Message list + quick-prompt chips + suggestion cards
 * that deep-link into Try-On.
 */
@Composable
fun AssistantScreen(
    onTryOnClick: (productId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    val controller = remember { AssistantController(container, scope) }
    val haptics = LocalHapticFeedback.current

    val messages by controller.messages.collectAsState()
    val thinking by controller.thinking.collectAsState()
    val catalog by controller.catalog.collectAsState()
    val productsById = remember(catalog) { catalog.associateBy { it.id } }
    val resById = remember(catalog) {
        catalog.associate { it.id to container.products.drawableRes(it) }
    }

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, thinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                messages.size - 1,
                scrollOffset = 0,
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TryFitColors.BgScreen)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TryFitSpacing.ScreenPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(TryFitColors.OrbGradient, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "AI Stylist",
                    style = MaterialTheme.typography.titleMedium,
                    color = TryFitColors.TextPrimary
                )
                Text(
                    text = "Style ideas — previews, not fit guarantees",
                    style = MaterialTheme.typography.bodySmall,
                    color = TryFitColors.TextSecondary
                )
            }
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                controller.clearChat()
            }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Clear chat",
                    tint = TryFitColors.TextSecondary
                )
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = TryFitSpacing.ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.hashCode() }) { message ->
                when (message) {
                    is UiMessage.Chat -> ChatBubble(message.message.text, message.message.role == "user")
                    is UiMessage.Quiz -> QuizCard(
                        question = message.question,
                        options = message.options,
                        onPick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.answerQuiz(it)
                        }
                    )
                    is UiMessage.Scorer -> ScorerPicker(
                        products = message.products,
                        resById = resById,
                        picks = controller.scorerPicks.collectAsState().value,
                        onToggle = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.toggleScorerPick(it)
                        },
                        onScore = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.runScorer()
                        }
                    )
                    is UiMessage.ScoreCard -> ScoreCard(message.score, message.names)
                    is UiMessage.PaletteCard -> PaletteCard(message.result)
                }
            }
            // Suggestion cards under the latest AI reply that carries product refs.
            val lastRefs = messages.filterIsInstance<UiMessage.Chat>()
                .lastOrNull { it.message.role == "ai" && it.message.productRefs.isNotEmpty() }
                ?.message?.productRefs.orEmpty()
            if (lastRefs.isNotEmpty()) {
                item(key = "suggestions") {
                    SuggestionRow(
                        products = lastRefs.mapNotNull { productsById[it] },
                        resById = resById,
                        onTryOnClick = onTryOnClick
                    )
                }
            }
            if (thinking) {
                item(key = "thinking") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OrbLoading(size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Putting the look together…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TryFitColors.TextSecondary
                        )
                    }
                }
            }
        }

        // Quick-prompt chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = TryFitSpacing.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(QUICK_PROMPTS) { prompt ->
                FilterChip(
                    selected = false,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.quickPrompt(prompt)
                    },
                    label = {
                        Text(text = prompt, style = MaterialTheme.typography.labelMedium)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = TryFitColors.SurfaceChipSelected,
                        labelColor = TryFitColors.TextPrimary
                    ),
                    border = null,
                    shape = TryFitRadii.Pill
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TryFitSpacing.ScreenPadding)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Ask for a look…", color = TryFitColors.TextSecondary)
                },
                singleLine = true,
                shape = TryFitRadii.Pill,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TryFitColors.BgCanvas,
                    unfocusedContainerColor = TryFitColors.BgCanvas,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    controller.send(draft)
                    draft = ""
                })
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    controller.send(draft)
                    draft = ""
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(TryFitColors.TextPrimary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = TryFitColors.TextOnPhoto
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, isUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) TryFitColors.TextOnPhoto else TryFitColors.TextPrimary,
            modifier = Modifier
                .background(
                    if (isUser) TryFitColors.TextPrimary else TryFitColors.BgCanvas,
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun SuggestionRow(
    products: List<Product>,
    resById: Map<String, Int>,
    onTryOnClick: (String) -> Unit
) {
    Column {
        Text(
            text = "Tap Try-On to preview",
            style = MaterialTheme.typography.labelMedium,
            color = TryFitColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products, key = { it.id }) { product ->
                SuggestionCard(product, resById[product.id] ?: 0, onTryOnClick)
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    product: Product,
    imageRes: Int,
    onTryOnClick: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, TryFitColors.BgCanvas, RoundedCornerShape(20.dp))
            .padding(8.dp)
    ) {
        if (imageRes != 0) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = imageRes),
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TryFitColors.SurfaceGridImg)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = product.brand,
            style = MaterialTheme.typography.labelSmall,
            color = TryFitColors.TextSecondary
        )
        Text(
            text = product.name,
            style = MaterialTheme.typography.labelMedium,
            color = TryFitColors.TextPrimary,
            maxLines = 1
        )
        Text(
            text = formatInr(product.priceInr),
            style = MaterialTheme.typography.labelMedium,
            color = TryFitColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTryOnClick(product.id)
            },
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Try-On", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun QuizCard(question: String, options: List<String>, onPick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TryFitColors.BgCanvas, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = false,
                    onClick = { onPick(option) },
                    label = { Text(option, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White,
                        labelColor = TryFitColors.TextPrimary
                    ),
                    shape = TryFitRadii.Pill
                )
            }
        }
    }
}

@Composable
private fun ScorerPicker(
    products: List<Product>,
    resById: Map<String, Int>,
    picks: Set<String>,
    onToggle: (String) -> Unit,
    onScore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TryFitColors.BgCanvas, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "Tap 2–4 pieces to score the combo",
            style = MaterialTheme.typography.labelLarge,
            color = TryFitColors.TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { product ->
                val selected = product.id in picks
                val res = resById[product.id] ?: 0
                Column(
                    modifier = Modifier
                        .width(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) TryFitColors.OrbViolet else TryFitColors.BgCanvas,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onToggle(product.id) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (res != 0) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = res),
                            contentDescription = product.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = TryFitColors.TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onScore,
            enabled = picks.size >= 2,
            shape = TryFitRadii.Pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = TryFitColors.TextPrimary,
                contentColor = TryFitColors.TextOnPhoto
            )
        ) {
            Text("Score my fit (${picks.size})", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ScoreCard(score: StylistBrain.OutfitScore, names: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TryFitColors.BgCanvas, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${score.score}",
                style = MaterialTheme.typography.displaySmall,
                color = TryFitColors.TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "/100",
                    style = MaterialTheme.typography.labelMedium,
                    color = TryFitColors.TextSecondary
                )
                Text(
                    text = names.joinToString(" + "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TryFitColors.TextSecondary,
                    maxLines = 2
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        score.tips.forEach { tip ->
            Text(
                text = "• $tip",
                style = MaterialTheme.typography.bodyMedium,
                color = TryFitColors.TextPrimary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PaletteCard(result: StylistBrain.PaletteResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TryFitColors.BgCanvas, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Text(
            text = result.paletteName,
            style = MaterialTheme.typography.titleMedium,
            color = TryFitColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Wear more:",
            style = MaterialTheme.typography.labelMedium,
            color = TryFitColors.TextSecondary
        )
        Text(
            text = result.bestColors.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = TryFitColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Go easy on: ${result.avoid.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = TryFitColors.TextSecondary
        )
    }
}
