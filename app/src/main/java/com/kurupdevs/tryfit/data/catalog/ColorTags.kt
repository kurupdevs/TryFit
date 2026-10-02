package com.kurupdevs.tryfit.data.catalog

/**
 * Simple palette tags derived from product names/tags — the heuristic behind
 * the outfit scorer (FEATURE-RESEARCH v1.5 #28). Pure keyword matching, no ML.
 */
object ColorTags {

    /** Neutral: safe to pair with anything. */
    private val neutral = listOf(
        "white", "black", "grey", "gray", "beige", "cream", "ivory", "navy",
        "tan", "khaki", "denim", "indigo", "charcoal"
    )

    /** Warm family. */
    private val warm = listOf(
        "red", "maroon", "pink", "orange", "mustard", "yellow", "rust",
        "terracotta", "coral", "olive"
    )

    /** Cool family. */
    private val cool = listOf(
        "blue", "teal", "green", "lavender", "purple", "aqua", "mint", "cyan"
    )

    /** Bold: works best as a single accent over neutrals. */
    private val bold = listOf(
        "red", "maroon", "pink", "neon", "floral", "embroidered", "gold"
    )

    data class Tags(val neutral: Boolean, val warm: Boolean, val cool: Boolean, val bold: Boolean)

    fun of(product: Product): Tags {
        val hay = (product.name + " " + product.tags.joinToString(" ")).lowercase()
        return Tags(
            neutral = neutral.any { hay.contains(it) },
            warm = warm.any { hay.contains(it) },
            cool = cool.any { hay.contains(it) },
            bold = bold.any { hay.contains(it) }
        )
    }

    /** Two warm/cool mixes clash; neutral pairs with anything; one bold accent is fine. */
    fun harmonyScore(items: List<Product>): Int {
        if (items.size < 2) return 8
        val tags = items.map { of(it) }
        val hasWarm = tags.any { it.warm }
        val hasCool = tags.any { it.cool }
        val boldCount = tags.count { it.bold }
        val allNeutral = tags.all { it.neutral && !it.warm && !it.cool }
        var score = 10
        if (hasWarm && hasCool) score -= 6            // warm+cool clash
        if (boldCount >= 2) score -= 5                // too many loud pieces
        if (boldCount == 1 && tags.all { it.neutral || it.bold }) score += 3
        if (allNeutral) score += 2
        return score.coerceIn(0, 15)
    }
}
