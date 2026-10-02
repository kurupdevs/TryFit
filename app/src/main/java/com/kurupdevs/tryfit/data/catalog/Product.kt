package com.kurupdevs.tryfit.data.catalog

import kotlinx.serialization.Serializable

/**
 * Canonical product model for TryFit. Shared by storefront (Worker D),
 * try-on (Worker E), stylist (Worker F) and wardrobe — do not duplicate.
 *
 * [imageResName] is the bundled drawable name (e.g. "product_001") resolved
 * at runtime via Resources.getIdentifier; the catalog JSON carries
 * `asset://product_NNN` URIs.
 */
@Serializable
data class Product(
    val id: String,
    val brand: String,
    val name: String,
    val category: ProductCategory,
    val priceInr: Int,
    val sizes: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val imageResName: String
) {
    /** Display price, Indian grouping: ₹1,499 */
    val priceLabel: String get() = formatInr(priceInr)
}

/** Catalog categories. Mirrors the products table enum + ethnic day-one. */
@Serializable
enum class ProductCategory(val label: String) {
    casual("Casual"),
    jackets("Jackets"),
    shoes("Shoes"),
    bags("Bags"),
    tops("Tops"),
    ethnic("Ethnic");

    companion object {
        fun from(raw: String): ProductCategory =
            entries.firstOrNull { it.name == raw.lowercase() } ?: casual
    }
}

/** ₹ formatting with Indian digit grouping (1,00,000 style). */
fun formatInr(amount: Int): String {
    val s = amount.toString()
    if (s.length <= 3) return "₹$s"
    val last3 = s.takeLast(3)
    var rest = s.dropLast(3)
    val groups = StringBuilder()
    while (rest.length > 2) {
        groups.insert(0, "," + rest.takeLast(2))
        rest = rest.dropLast(2)
    }
    return "₹$rest$groups,$last3"
}
