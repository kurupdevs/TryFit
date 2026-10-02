package com.kurupdevs.tryfit.ui.screens.wardrobe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.catalog.ProductCategory
import com.kurupdevs.tryfit.data.db.TryOnHistoryEntity
import com.kurupdevs.tryfit.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One wardrobe grid row: the saved product (null while resolving). */
data class WardrobeRowUi(
    val productId: String,
    val product: Product?,
    val addedAt: Long,
)

/** Wardrobe backing state: saved items + try-on history, products resolved. */
class WardrobeViewModel(private val container: AppContainer) : ViewModel() {

    var items by mutableStateOf<List<WardrobeRowUi>>(emptyList())
        private set
    var history by mutableStateOf<List<TryOnHistoryEntity>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    private var _categoryFilter by mutableStateOf<ProductCategory?>(null)
    val categoryFilter: ProductCategory? get() = _categoryFilter

    init {
        viewModelScope.launch {
            combine(
                container.wardrobeRepository.observeItems(),
                container.historyRepository.observeHistory(),
            ) { wardrobe, hist -> wardrobe to hist }
                .collect { (wardrobe, hist) ->
                    val resolved = withContext(Dispatchers.IO) {
                        val products = runCatching { container.products.byIds(wardrobe.map { it.productId }) }
                            .getOrDefault(emptyList())
                            .associateBy { it.id }
                        wardrobe.map { entity ->
                            WardrobeRowUi(
                                productId = entity.productId,
                                product = products[entity.productId],
                                addedAt = entity.addedAt,
                            )
                        }
                    }
                    items = resolved
                    history = hist
                    loading = false
                }
        }
    }

    fun setCategoryFilter(category: ProductCategory?) {
        _categoryFilter = category
    }

    fun filteredItems(): List<WardrobeRowUi> {
        val f = categoryFilter ?: return items
        return items.filter { it.product?.category == f }
    }

    fun remove(productId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.wardrobeRepository.remove(productId)
        }
    }

    fun deleteHistory(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.historyRepository.delete(sessionId)
        }
    }
}
