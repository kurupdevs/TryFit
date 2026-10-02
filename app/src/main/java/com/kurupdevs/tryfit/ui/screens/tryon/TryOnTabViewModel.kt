package com.kurupdevs.tryfit.ui.screens.tryon

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurupdevs.tryfit.data.catalog.Product
import com.kurupdevs.tryfit.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Backing state for the try-on tab (avatar presence, quota pill, product picker). */
class TryOnTabViewModel(private val container: AppContainer) : ViewModel() {

    var hasAvatar by mutableStateOf(false)
        private set

    var quotaText by mutableStateOf<String?>(null)
        private set

    var products by mutableStateOf<List<Product>>(emptyList())
        private set

    var loadingProducts by mutableStateOf(true)
        private set

    fun load(context: Context) {
        viewModelScope.launch {
            hasAvatar = container.avatarStore.avatar.first() != null
            quotaText = runCatching {
                val q = container.tryOnEngine.getQuota()
                // Honest free-tier copy (research v1 item 17): no card, no paywall.
                "${q.remaining}/${q.limit} free left today · no card required"
            }.getOrNull()
            products = runCatching { container.products.all() }.getOrDefault(emptyList())
            loadingProducts = false
        }
    }
}
