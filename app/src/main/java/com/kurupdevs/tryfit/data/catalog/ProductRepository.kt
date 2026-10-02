package com.kurupdevs.tryfit.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private const val CATALOG_ASSET = "products.json"

/**
 * Catalog repository. Loads the bundled seed catalog from assets
 * (products.json, assembled by the build from /tmp/tryfit_catalog_g*.json).
 * Offline-first by construction; Supabase becomes the source of truth for
 * merchant/admin catalogs in a later phase.
 */
class ProductRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private var cache: List<Product>? = null

    /** Clears the in-memory cache; the next [all] re-reads the asset file. */
    fun invalidate() {
        cache = null
    }

    suspend fun all(): List<Product> = withContext(Dispatchers.IO) {
        cache ?: load().also { cache = it }
    }

    suspend fun byId(id: String): Product? = all().firstOrNull { it.id == id }

    suspend fun byIds(ids: List<String>): List<Product> {
        val map = all().associateBy { it.id }
        return ids.mapNotNull { map[it] }
    }

    suspend fun byCategory(category: ProductCategory): List<Product> =
        all().filter { it.category == category }

    suspend fun underBudget(maxInr: Int, categories: List<ProductCategory>? = null): List<Product> =
        all().filter { (categories == null || it.category in categories) && it.priceInr <= maxInr }

    /** Resolve the bundled drawable id for a product; 0 if the asset is missing. */
    fun drawableRes(product: Product): Int =
        context.resources.getIdentifier(product.imageResName, "drawable", context.packageName)

    private fun load(): List<Product> {
        val text = context.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }
        val raw = json.decodeFromString<List<CatalogEntry>>(text)
        return raw.mapNotNull { entry ->
            val resName = entry.image.removePrefix("asset://")
            if (resName.isBlank()) return@mapNotNull null
            // Drop entries with no matching bundled drawable (spec: catalog assembly).
            if (context.resources.getIdentifier(resName, "drawable", context.packageName) == 0) {
                return@mapNotNull null
            }
            Product(
                id = entry.id,
                brand = entry.brand,
                name = entry.name,
                category = ProductCategory.from(entry.category),
                priceInr = entry.price_inr,
                sizes = entry.sizes,
                tags = entry.tags,
                imageResName = resName
            )
        }
    }

    @kotlinx.serialization.Serializable
    private data class CatalogEntry(
        val id: String,
        val brand: String,
        val name: String,
        val category: String,
        val price_inr: Int,
        val sizes: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val image: String = ""
    )
}
