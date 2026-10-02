package com.kurupdevs.tryfit.data.favorites

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kurupdevs.tryfit.data.store.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Favorites + price-drop targets ("ping me under ₹999").
 * favorites: set of product ids. targets: productId -> target price in ₹.
 * notified: productId -> price we already notified at (no repeat pings).
 */
class PriceAlertStore(private val context: Context) {

    data class Favorite(val productId: String, val targetInr: Int?, val notifiedAtInr: Int?)

    val favorites: Flow<List<Favorite>> = context.appDataStore.data.map { prefs ->
        val ids = prefs[FAV_IDS] ?: emptySet()
        val targets = decodeMap(prefs[TARGETS])
        val notified = decodeMap(prefs[NOTIFIED])
        ids.map { id -> Favorite(id, targets[id], notified[id]) }.sortedBy { it.productId }
    }

    suspend fun isFavorite(productId: String): Boolean =
        favorites.first().any { it.productId == productId }

    suspend fun toggleFavorite(productId: String) {
        context.appDataStore.edit { prefs ->
            val ids = (prefs[FAV_IDS] ?: emptySet()).toMutableSet()
            if (productId in ids) {
                ids.remove(productId)
                val targets = decodeMap(prefs[TARGETS]).toMutableMap().also { it.remove(productId) }
                val notified = decodeMap(prefs[NOTIFIED]).toMutableMap().also { it.remove(productId) }
                prefs[TARGETS] = encodeMap(targets)
                prefs[NOTIFIED] = encodeMap(notified)
            } else {
                ids.add(productId)
            }
            prefs[FAV_IDS] = ids
        }
    }

    /** Set/clear the "ping me under ₹X" target. Null target = favorite only. */
    suspend fun setTarget(productId: String, targetInr: Int?) {
        context.appDataStore.edit { prefs ->
            val targets = decodeMap(prefs[TARGETS]).toMutableMap()
            if (targetInr == null) targets.remove(productId) else targets[productId] = targetInr
            prefs[TARGETS] = encodeMap(targets)
            // Reset notified state when the target changes.
            val notified = decodeMap(prefs[NOTIFIED]).toMutableMap().also { it.remove(productId) }
            prefs[NOTIFIED] = encodeMap(notified)
        }
    }

    suspend fun markNotified(productId: String, priceInr: Int) {
        context.appDataStore.edit { prefs ->
            val notified = decodeMap(prefs[NOTIFIED]).toMutableMap()
            notified[productId] = priceInr
            prefs[NOTIFIED] = encodeMap(notified)
        }
    }

    private fun decodeMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { part ->
            val kv = part.split("=")
            if (kv.size == 2) kv[0] to (kv[1].toIntOrNull() ?: return@mapNotNull null) else null
        }.toMap()
    }

    private fun encodeMap(map: Map<String, Int>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    companion object {
        private val FAV_IDS = stringSetPreferencesKey("fav_ids")
        private val TARGETS = stringPreferencesKey("price_targets")
        private val NOTIFIED = stringPreferencesKey("price_notified")
    }
}
