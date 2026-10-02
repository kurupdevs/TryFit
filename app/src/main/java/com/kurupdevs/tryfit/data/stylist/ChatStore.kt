package com.kurupdevs.tryfit.data.stylist

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kurupdevs.tryfit.data.store.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local chat persistence — mirrors the ai_chats table shape
 * {role, text, productRef, ts} so a later sync is a straight upload.
 */
@Serializable
data class ChatMessage(
    val role: String, // "user" | "ai"
    val text: String,
    val productRefs: List<String> = emptyList(),
    val ts: Long = System.currentTimeMillis()
)

class ChatStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val messages: Flow<List<ChatMessage>> =
        context.appDataStore.data.map { prefs ->
            prefs[MESSAGES_KEY]?.let { raw ->
                runCatching { json.decodeFromString<List<ChatMessage>>(raw) }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun current(): List<ChatMessage> = messages.first()

    suspend fun append(message: ChatMessage) {
        val updated = current() + message
        // Cap history at 200 messages; ai_chats is a conversation, not a log.
        val trimmed = updated.takeLast(200)
        context.appDataStore.edit { it[MESSAGES_KEY] = json.encodeToString(trimmed) }
    }

    suspend fun clear() {
        context.appDataStore.edit { it.remove(MESSAGES_KEY) }
    }

    suspend fun palette(): String? =
        context.appDataStore.data.map { it[PALETTE_KEY] }.first()

    suspend fun savePalette(paletteName: String) {
        context.appDataStore.edit { it[PALETTE_KEY] = paletteName }
    }

    companion object {
        private val MESSAGES_KEY = stringPreferencesKey("ai_chats_messages")
        private val PALETTE_KEY = stringPreferencesKey("ai_palette_name")
    }
}
