package com.kurupdevs.tryfit.data.stylist

import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.catalog.ProductCategory
import com.kurupdevs.tryfit.data.catalog.ColorTags
import com.kurupdevs.tryfit.data.catalog.formatInr

/**
 * Local, rule-based stylist brain — no network. Keyword matching on occasion
 * (wedding/office/date/winter) + budget parsing ("under ₹X"), looks assembled
 * from the catalog, heuristic outfit scoring, and a 3-question personal
 * palette quiz (FEATURE-RESEARCH v1.5 #27/#28/#29).
 *
 * The products provider is a suspend lambda so the brain never touches
 * Android — unit-testable pure logic.
 */
class StylistBrain(private val products: suspend () -> List<Product>) {

    data class Reply(val text: String, val productRefs: List<String>)

    // ---------- Chat ----------

    suspend fun answer(message: String): Reply {
        val lower = message.lowercase()
        val budget = parseBudget(lower)
        val occasion = detectOccasion(lower)

        if (lower.contains("rate my fit") || lower.contains("rate my outfit") || lower.contains("score my")) {
            return Reply(
                "Pick 2–4 pieces below and I'll score the combo — color harmony, coordination, and what to fix.",
                emptyList()
            )
        }
        if (lower.contains("what goes with") || lower.contains("goes with this")) {
            val staples = products().filter {
                it.name.contains("white", true) || it.name.contains("denim", true) || it.name.contains("black", true)
            }.take(3)
            return Reply(
                "Safe bets that go with almost everything: neutrals. " +
                    "A white staple + denim + black shoes never misses. Tap any to try it on.",
                staples.map { it.id }
            )
        }
        if (occasion != null) {
            return assembleLook(occasion, budget)
        }
        // Fallback: keyword category search, else trending picks.
        val hits = products().filter { p ->
            lower.split(" ").any { word ->
                word.length > 3 && (p.name.contains(word, true) || p.tags.any { it.contains(word, true) })
            }
        }.take(4)
        return if (hits.isNotEmpty()) {
            Reply(
                "Found ${hits.size} that match \"${message.trim()}\". Tap to preview on the try-on screen.",
                hits.map { it.id }
            )
        } else {
            val picks = products().sortedBy { it.priceInr }.take(3)
            Reply(
                "Tell me the occasion (wedding, office, date, winter) and a budget like \"under ₹3000\" " +
                    "and I'll build the full look. Meanwhile, these budget staples are solid:",
                picks.map { it.id }
            )
        }
    }

    private enum class Occasion(val label: String, val categories: List<ProductCategory>) {
        WEDDING("wedding", listOf(ProductCategory.ethnic, ProductCategory.shoes)),
        OFFICE("office", listOf(ProductCategory.tops, ProductCategory.jackets, ProductCategory.shoes)),
        DATE("date night", listOf(ProductCategory.tops, ProductCategory.casual, ProductCategory.shoes)),
        WINTER("winter", listOf(ProductCategory.jackets, ProductCategory.tops))
    }

    private fun detectOccasion(lower: String): Occasion? = when {
        listOf("wedding", "shaadi", "marriage", "baraat").any { lower.contains(it) } -> Occasion.WEDDING
        listOf("office", "work", "formal", "interview", "meeting").any { lower.contains(it) } -> Occasion.OFFICE
        listOf("date", "dinner", "party", "club").any { lower.contains(it) } -> Occasion.DATE
        listOf("winter", "cold", "sweater weather").any { lower.contains(it) } -> Occasion.WINTER
        else -> null
    }

    private val budgetRegex = Regex("(?:under|below|within|max)\\s*₹?\\s?([\\d,]{3,})")

    private fun parseBudget(lower: String): Int? =
        budgetRegex.find(lower)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()

    private suspend fun assembleLook(occasion: Occasion, budget: Int?): Reply {
        val catalog = products()
        val picks = mutableListOf<Product>()
        var remaining = budget ?: Int.MAX_VALUE

        for (category in occasion.categories) {
            val options = catalog
                .filter { it.category == category && it.priceInr <= remaining && it.id !in picks.map { p -> p.id } }
                .sortedBy { it.priceInr }
            val choice = options.firstOrNull() ?: continue
            picks.add(choice)
            remaining -= choice.priceInr
        }

        if (picks.isEmpty()) {
            val cheapest = catalog.sortedBy { it.priceInr }.take(3)
            return Reply(
                "Nothing in ${occasion.label} wear fits ${budget?.let { formatInr(it) } ?: "that"} — " +
                    "try raising the budget a little. Closest budget picks:",
                cheapest.map { it.id }
            )
        }

        val total = picks.sumOf { it.priceInr }
        val budgetLine = if (budget != null) " Total ${formatInr(total)}, inside your ${formatInr(budget)} budget." else " Total ${formatInr(total)}."
        val text = buildString {
            append("Here's your ${occasion.label} look:\n")
            picks.forEach { append("• ${it.brand} ${it.name} — ${it.priceLabel}\n") }
            append(budgetLine)
            append(" Tap Try-On on any card to preview it on yourself.")
        }
        return Reply(text.trimEnd(), picks.map { it.id })
    }

    // ---------- Outfit scorer ----------

    data class OutfitScore(val score: Int, val tips: List<String>)

    /**
     * Heuristic scorer /100: coverage (a real outfit needs 2+ pieces) 40,
     * color harmony 35, category coordination 25. Returns 2–3 actionable tips.
     */
    fun scoreOutfit(items: List<Product>): OutfitScore {
        val tips = mutableListOf<String>()
        var score = 0

        // Coverage (40)
        val categories = items.map { it.category }.toSet()
        val coverage = when {
            categories.size >= 3 -> 40.also { }
            categories.size == 2 -> 30
            else -> 12
        }
        score += coverage
        if (categories.size < 2) tips.add("One piece isn't a fit — add shoes or a layer to complete it.")

        // Color harmony (35): map 0–15 ColorTags scale to 0–35.
        val harmony = ColorTags.harmonyScore(items)
        score += (harmony * 35 / 15)
        val tags = items.map { ColorTags.of(it) }
        if (tags.any { it.warm } && tags.any { it.cool }) {
            tips.add("Warm + cool tones are clashing here — swap one piece for a neutral (white, black, beige).")
        }
        if (tags.count { it.bold } >= 2) {
            tips.add("Two loud pieces fight each other — keep one statement item, make the rest neutral.")
        }

        // Coordination (25)
        var coord = 15 // base: pieces exist together
        val cats = categories
        if (ProductCategory.shoes in cats) coord += 5
        if (ProductCategory.jackets in cats || ProductCategory.ethnic in cats) coord += 5
        score += coord
        if (ProductCategory.shoes !in cats && items.size >= 2) {
            tips.add("No footwear in the mix — shoes make or break the look.")
        }
        if (tips.size < 2) {
            tips.add(
                if (score >= 75) "This is working. Try it on to check the real drape before you buy."
                else "Try swapping the loudest piece first — small changes fix most fits."
            )
        }

        return OutfitScore(score.coerceIn(0, 100), tips.take(3))
    }

    // ---------- Personal palette quiz (v1.5 #27) ----------

    enum class Undertone { WARM, COOL, NEUTRAL }
    enum class EyeColor { BROWN, GREEN_HAZEL, BLUE_GREY }
    enum class HairColor { BLACK_DARK, BROWN_MEDIUM, LIGHT_BLONDE }

    data class PaletteResult(
        val paletteName: String,
        val bestColors: List<String>,
        val avoid: List<String>,
        val blurb: String
    )

    /** 3 manual questions -> palette. No face scan, no biometrics — just a quiz. */
    fun paletteQuiz(undertone: Undertone, eye: EyeColor, hair: HairColor): PaletteResult {
        val warmVotes = listOf(
            undertone == Undertone.WARM,
            eye == EyeColor.GREEN_HAZEL,
            hair == HairColor.BROWN_MEDIUM || hair == HairColor.LIGHT_BLONDE
        ).count { it }
        val coolVotes = listOf(
            undertone == Undertone.COOL,
            eye == EyeColor.BLUE_GREY,
            hair == HairColor.BLACK_DARK
        ).count { it }

        return when {
            warmVotes >= 2 -> PaletteResult(
                paletteName = "Warm Autumn",
                bestColors = listOf("Olive", "Mustard", "Rust", "Terracotta", "Warm beige", "Chocolate brown"),
                avoid = listOf("Icy pastels", "Neon brights"),
                blurb = "Earthy, rich tones light you up. Golds and warm browns > silver and black."
            )
            coolVotes >= 2 -> PaletteResult(
                paletteName = "Cool Winter",
                bestColors = listOf("Black", "Navy", "White", "Burgundy", "Emerald", "Royal blue"),
                avoid = listOf("Orange", "Mustardy yellows"),
                blurb = "High-contrast cool tones. Crisp white, deep jewel tones, and true black are your friends."
            )
            else -> PaletteResult(
                paletteName = "Soft Neutral",
                bestColors = listOf("Cream", "Taupe", "Soft grey", "Dusty rose", "Sage", "Navy"),
                avoid = listOf("Neon brights", "Harsh black head-to-toe"),
                blurb = "You sit in the middle — muted, blended tones work best. Avoid extremes in either direction."
            )
        }
    }
}
