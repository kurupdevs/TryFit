package com.kurupdevs.tryfit.ui.screens.assistant

import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.stylist.ChatMessage
import com.kurupdevs.tryfit.data.stylist.ChatStore
import com.kurupdevs.tryfit.data.stylist.StylistBrain
import com.kurupdevs.tryfit.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI message types for the stylist sheet. Only [Chat] rows persist to
 * [ChatStore] (the ai_chats mirror); quiz/scorer/palette rows are ephemeral.
 */
sealed interface UiMessage {
    data class Chat(val message: ChatMessage) : UiMessage
    data class Quiz(val step: Int, val question: String, val options: List<String>) : UiMessage
    data class Scorer(val products: List<Product>) : UiMessage
    data class ScoreCard(val score: StylistBrain.OutfitScore, val names: List<String>) : UiMessage
    data class PaletteCard(val result: StylistBrain.PaletteResult) : UiMessage
}

private data class QuizQuestion(val text: String, val options: List<String>)

private val QUIZ = listOf(
    QuizQuestion(
        "First: your skin undertone. Check the veins on your wrist — greenish means warm, bluish means cool, a mix means neutral.",
        listOf("Warm", "Cool", "Neutral")
    ),
    QuizQuestion(
        "Your eye color?",
        listOf("Brown", "Green/Hazel", "Blue/Grey")
    ),
    QuizQuestion(
        "Your natural hair color?",
        listOf("Black/Dark", "Brown", "Light/Blonde")
    )
)

/**
 * Owns stylist-sheet state: chat history, thinking indicator, palette quiz,
 * and the outfit scorer. Plain class (no ViewModel) — scoped to the sheet.
 */
class AssistantController(
    private val container: AppContainer,
    private val scope: CoroutineScope
) {
    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _catalog = MutableStateFlow<List<Product>>(emptyList())
    val catalog: StateFlow<List<Product>> = _catalog.asStateFlow()

    private val _scorerPicks = MutableStateFlow<Set<String>>(emptySet())
    val scorerPicks: StateFlow<Set<String>> = _scorerPicks.asStateFlow()

    private val quizAnswers = mutableListOf<String>()
    private var quizStep = -1

    init {
        scope.launch {
            val history = container.chatStore.current()
            _catalog.value = container.products.all()
            _messages.value = history.map { UiMessage.Chat(it) }
            if (history.isEmpty()) {
                val hello = ChatMessage(
                    role = "ai",
                    text = "Hey, I'm your AI stylist. Tell me the occasion + budget — " +
                        "\"style me for a wedding under ₹5,000\" — and I'll build the look from the catalog."
                )
                container.chatStore.append(hello)
                _messages.value = listOf(UiMessage.Chat(hello))
            }
        }
    }

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _thinking.value) return
        scope.launch {
            val user = ChatMessage(role = "user", text = clean)
            container.chatStore.append(user)
            push(UiMessage.Chat(user))
            _thinking.value = true
            delay(650) // beat so the reply feels considered, not instant
            val reply = container.stylist.answer(clean)
            val ai = ChatMessage(role = "ai", text = reply.text, productRefs = reply.productRefs)
            container.chatStore.append(ai)
            _thinking.value = false
            push(UiMessage.Chat(ai))
            if (clean.contains("rate my fit", true) || clean.contains("rate my outfit", true)) {
                push(UiMessage.Scorer(_catalog.value.shuffled().take(12)))
            }
        }
    }

    fun quickPrompt(text: String) {
        when {
            text.equals("Find my colors", true) -> startQuiz()
            else -> send(text)
        }
    }

    // ---------- Palette quiz ----------

    private fun startQuiz() {
        quizAnswers.clear()
        quizStep = 0
        val q = QUIZ[0]
        val user = ChatMessage(role = "user", text = "Find my colors")
        val ai = ChatMessage(role = "ai", text = "Let's find your personal palette — 3 quick questions.")
        scope.launch {
            container.chatStore.append(user)
            container.chatStore.append(ai)
            push(UiMessage.Chat(user))
            push(UiMessage.Chat(ai))
            push(UiMessage.Quiz(0, q.text, q.options))
        }
    }

    fun answerQuiz(option: String) {
        if (quizStep < 0) return
        quizAnswers.add(option)
        val user = ChatMessage(role = "user", text = option)
        scope.launch { container.chatStore.append(user) }
        push(UiMessage.Chat(user))
        // Remove the answered quiz card.
        _messages.value = _messages.value.filterNot { it is UiMessage.Quiz }
        quizStep++
        if (quizStep < QUIZ.size) {
            val q = QUIZ[quizStep]
            push(UiMessage.Quiz(quizStep, q.text, q.options))
        } else {
            quizStep = -1
            val undertone = when (quizAnswers.getOrNull(0)) {
                "Warm" -> StylistBrain.Undertone.WARM
                "Cool" -> StylistBrain.Undertone.COOL
                else -> StylistBrain.Undertone.NEUTRAL
            }
            val eye = when (quizAnswers.getOrNull(1)) {
                "Brown" -> StylistBrain.EyeColor.BROWN
                "Green/Hazel" -> StylistBrain.EyeColor.GREEN_HAZEL
                else -> StylistBrain.EyeColor.BLUE_GREY
            }
            val hair = when (quizAnswers.getOrNull(2)) {
                "Black/Dark" -> StylistBrain.HairColor.BLACK_DARK
                "Brown" -> StylistBrain.HairColor.BROWN_MEDIUM
                else -> StylistBrain.HairColor.LIGHT_BLONDE
            }
            val result = container.stylist.paletteQuiz(undertone, eye, hair)
            scope.launch { container.chatStore.savePalette(result.paletteName) }
            val ai = ChatMessage(
                role = "ai",
                text = "Your palette: ${result.paletteName}. ${result.blurb}"
            )
            scope.launch { container.chatStore.append(ai) }
            push(UiMessage.Chat(ai))
            push(UiMessage.PaletteCard(result))
        }
    }

    // ---------- Outfit scorer ----------

    fun toggleScorerPick(productId: String) {
        val picks = _scorerPicks.value.toMutableSet()
        if (productId in picks) picks.remove(productId)
        else if (picks.size < 4) picks.add(productId)
        _scorerPicks.value = picks
    }

    fun runScorer() {
        val picks = _catalog.value.filter { it.id in _scorerPicks.value }
        if (picks.size < 2) return
        val score = container.stylist.scoreOutfit(picks)
        push(UiMessage.ScoreCard(score, picks.map { it.name }))
        _scorerPicks.value = emptySet()
    }

    fun clearChat() {
        scope.launch {
            container.chatStore.clear()
            _messages.value = emptyList()
            quizStep = -1
        }
    }

    private fun push(message: UiMessage) {
        _messages.value = _messages.value + message
    }
}
