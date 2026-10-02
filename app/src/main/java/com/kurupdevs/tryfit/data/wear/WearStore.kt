package com.kurupdevs.tryfit.data.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kurupdevs.tryfit.data.catalog.formatInr
import com.kurupdevs.tryfit.data.store.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Cost-per-wear + outfit calendar (lite). Keyed by item id — works for both
 * catalog products (in wardrobe) and closet items, since both have unique ids.
 */
class WearStore(private val context: Context) {

    /** itemId -> times worn */
    val wearCounts: Flow<Map<String, Int>> =
        context.appDataStore.data.map { decodeMap(it[COUNTS]) }

    /** itemId -> set of worn dates (yyyy-MM-dd) */
    val wornDates: Flow<Map<String, Set<String>>> =
        context.appDataStore.data.map { decodeDates(it[DATES]) }

    suspend fun wears(itemId: String): Int = wearCounts.first()[itemId] ?: 0

    suspend fun dates(itemId: String): Set<String> = wornDates.first()[itemId] ?: emptySet()

    /** +1 worn today. */
    suspend fun markWorn(itemId: String) {
        val today = LocalDate.now().toString()
        context.appDataStore.edit { prefs ->
            val counts = decodeMap(prefs[COUNTS]).toMutableMap()
            counts[itemId] = (counts[itemId] ?: 0) + 1
            prefs[COUNTS] = encodeMap(counts)
            val dates = decodeDates(prefs[DATES]).toMutableMap()
            dates[itemId] = (dates[itemId] ?: emptySet()) + today
            prefs[DATES] = encodeDates(dates)
        }
    }

    /** Toggle a specific date on the calendar. */
    suspend fun toggleDate(itemId: String, date: String) {
        context.appDataStore.edit { prefs ->
            val dates = decodeDates(prefs[DATES]).toMutableMap()
            val set = (dates[itemId] ?: emptySet()).toMutableSet()
            if (date in set) set.remove(date) else set.add(date)
            if (set.isEmpty()) dates.remove(itemId) else dates[itemId] = set
            prefs[DATES] = encodeDates(dates)
        }
    }

    /** Cost per wear = price / max(1, wears). */
    fun costPerWearLabel(priceInr: Int, wears: Int): String =
        formatInr(priceInr / maxOf(1, wears))

    private fun decodeMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { part ->
            val kv = part.split("=")
            if (kv.size == 2) kv[0] to (kv[1].toIntOrNull() ?: return@mapNotNull null) else null
        }.toMap()
    }

    private fun encodeMap(map: Map<String, Int>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeDates(raw: String?): Map<String, Set<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";").mapNotNull { part ->
            val kv = part.split("=")
            if (kv.size == 2) kv[0] to kv[1].split(",").filter { it.isNotBlank() }.toSet() else null
        }.toMap()
    }

    private fun encodeDates(map: Map<String, Set<String>>): String =
        map.entries.joinToString(";") { "${it.key}=${it.value.joinToString(",")}" }

    companion object {
        private val COUNTS = stringPreferencesKey("wear_counts")
        private val DATES = stringPreferencesKey("worn_dates")
    }
}
