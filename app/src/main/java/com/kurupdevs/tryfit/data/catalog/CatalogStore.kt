package com.kurupdevs.tryfit.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Reactive storefront facade over [ProductRepository].
 *
 * The catalog ships bundled (`assets/products.json` + drawable-nodpi), so it
 * is offline by construction — no Room mirror needed for v1. This class adds
 * the reactive [Flow] API the home feed needs: category filtering, search,
 * and pull-to-refresh re-reads.
 */
class CatalogStore(private val repo: ProductRepository) {

    /** Bumped by [refresh] to re-emit every stream. */
    private val generation = MutableStateFlow(0)

    /** All products, or one [ProductCategory] when non-null. */
    fun products(category: ProductCategory? = null): Flow<List<Product>> =
        generation.map {
            // Defensive: the seed asset may not be packaged yet (Worker F) —
            // serve an empty feed instead of crashing the collector.
            val all = runCatching { repo.all() }.getOrDefault(emptyList())
            if (category == null) all else all.filter { it.category == category }
        }.flowOn(Dispatchers.Default)

    /** Case-insensitive match on name, brand and tags. */
    fun search(query: String): Flow<List<Product>> =
        generation.map {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return@map emptyList<Product>()
            runCatching { repo.all() }.getOrDefault(emptyList()).filter { product ->
                product.name.lowercase().contains(q) ||
                    product.brand.lowercase().contains(q) ||
                    product.tags.any { tag -> tag.lowercase().contains(q) }
            }
        }.flowOn(Dispatchers.Default)

    suspend fun byId(id: String): Product? = runCatching { repo.byId(id) }.getOrNull()

    /**
     * Re-reads `assets/products.json` and re-emits all streams
     * (pull-to-refresh target).
     */
    suspend fun refresh() {
        repo.invalidate()
        // Force a fresh read so a swapped asset file actually shows up.
        repo.all()
        generation.value += 1
    }
}
