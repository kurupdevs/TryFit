package com.kurupdevs.tryfit.ui.screens.home

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurupdevs.tryfit.data.SettingsStore
import com.kurupdevs.tryfit.data.WardrobeRepository
import com.kurupdevs.tryfit.data.catalog.CatalogStore
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.data.catalog.ProductCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home feed ViewModel — SPEC §3.2.
 *
 * Products stream from [CatalogStore] (bundled catalog, offline by
 * construction). Pull-to-refresh re-reads the asset with a 600ms minimum
 * spinner (SPEC §4). Saved-ids stream from the Room-backed
 * [WardrobeRepository]; the display name from [SettingsStore].
 */
class HomeViewModel(
    private val catalog: CatalogStore,
    wardrobe: WardrobeRepository,
    settings: SettingsStore
) : ViewModel() {

    private val _category = MutableStateFlow<ProductCategory?>(null)
    val category: StateFlow<ProductCategory?> = _category.asStateFlow()

    val products: StateFlow<List<Product>> =
        _category.flatMapLatest { catalog.products(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userName: StateFlow<String> =
        settings.settings.map { it.userName }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val savedIds: StateFlow<Set<String>> =
        wardrobe.observeItems().map { items -> items.map { it.productId }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _booted = MutableStateFlow(false)

    /** Shimmer skeletons while the first load is in flight and empty. */
    val showShimmer: StateFlow<Boolean> =
        combine(_booted, products) { booted, list -> !booted && list.isEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _loadFailed = MutableStateFlow(false)

    /** True when the catalog failed to load AND there is nothing to show. */
    val showError: StateFlow<Boolean> =
        combine(_loadFailed, products) { failed, list -> failed && list.isEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        refresh()
    }

    fun selectCategory(category: ProductCategory?) {
        _category.value = category
    }

    /** Pull-to-refresh: re-read assets, minimum 600ms spinner (SPEC §4). */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val start = SystemClock.uptimeMillis()
            val ok = runCatching { catalog.refresh() }.isSuccess
            _loadFailed.value = !ok
            val elapsed = SystemClock.uptimeMillis() - start
            if (elapsed < 600) delay(600 - elapsed)
            _refreshing.value = false
            _booted.value = true
        }
    }
}
