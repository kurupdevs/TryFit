package com.kurupdevs.tryfit.data.closet

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kurupdevs.tryfit.data.store.appDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * "Try with my stuff" lite: user's own garments digitized via camera/gallery.
 * Photos stored app-private (filesDir/closet/); manual category only, no ML.
 * Serialized with kotlinx.serialization so the list survives reinstall via
 * backup (allowBackup=true) while photos stay on-device.
 */
@Serializable
data class ClosetItem(
    val id: String,
    val name: String,
    val category: String,
    val photoFileName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    /** Multi-garment try-on token: closet items travel as "closet:<id>". */
    val tryOnToken: String get() = "closet:$id"
}

/** Manual categories — same vocabulary as the catalog. */
val CLOSET_CATEGORIES = listOf("Tops", "Casual", "Jackets", "Ethnic", "Shoes", "Bags", "Other")

class ClosetRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val items: Flow<List<ClosetItem>> = context.appDataStore.data.map { prefs ->
        prefs[CLOSET_KEY]?.let { raw ->
            runCatching { json.decodeFromString<List<ClosetItem>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    fun photoFile(item: ClosetItem): File =
        File(context.filesDir, "closet/${item.photoFileName}")

    /** Copy a picked/captured photo into app-private storage and register the item. */
    suspend fun add(sourceUri: Uri, name: String, category: String, note: String = ""): ClosetItem =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "closet").also { it.mkdirs() }
            val id = UUID.randomUUID().toString()
            val dest = File(dir, "$id.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("Could not read photo")
            val item = ClosetItem(
                id = id,
                name = name.ifBlank { "My item" },
                category = category.ifBlank { "Other" },
                photoFileName = dest.name,
                note = note
            )
            context.appDataStore.edit { prefs ->
                val current = prefs[CLOSET_KEY]?.let { raw ->
                    runCatching { json.decodeFromString<List<ClosetItem>>(raw) }.getOrDefault(emptyList())
                } ?: emptyList()
                prefs[CLOSET_KEY] = json.encodeToString(current + item)
            }
            item
        }

    suspend fun delete(item: ClosetItem) {
        withContext(Dispatchers.IO) {
            runCatching { photoFile(item).delete() }
            context.appDataStore.edit { prefs ->
                val current = prefs[CLOSET_KEY]?.let { raw ->
                    runCatching { json.decodeFromString<List<ClosetItem>>(raw) }.getOrDefault(emptyList())
                } ?: emptyList()
                prefs[CLOSET_KEY] = json.encodeToString(current.filter { it.id != item.id })
            }
        }
    }

    companion object {
        private val CLOSET_KEY = stringPreferencesKey("closet_items")
    }
}
