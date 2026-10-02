package com.kurupdevs.tryfit.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val KEY_AVATAR_SET = booleanPreferencesKey("avatar_set")
private val KEY_AVATAR_UPDATED_AT = longPreferencesKey("avatar_updated_at_ms")

/**
 * One-photo avatar store (research v1 item 1 — Genlook's winning UX).
 *
 * The user sets one full-body photo ("My Avatar") once from camera/gallery;
 * it is reused as the default try-on input forever, and can be changed or
 * deleted anytime. Stored in app-private files (never in shared storage);
 * DataStore only tracks presence + timestamp.
 *
 * Privacy: deleting the avatar is one tap (Settings → Privacy → Delete my
 * photos clears this too). Raw uploads auto-expire via [PhotoJanitor].
 */
class AvatarStore(
    private val context: Context,
    private val prefs: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
) {

    private fun avatarFile(): File =
        File(context.filesDir, "avatar/avatar.jpg").also { it.parentFile?.mkdirs() }

    /** Emits the avatar file when set, null otherwise. */
    val avatar: Flow<File?> = prefs.data.map { p ->
        if (p[KEY_AVATAR_SET] == true) avatarFile().takeIf { it.exists() } else null
    }

    suspend fun hasAvatar(): Boolean = prefs.data.first()[KEY_AVATAR_SET] == true &&
        avatarFile().exists()

    /** Compresses [jpegBytes] into the private avatar slot. */
    suspend fun saveAvatar(jpegBytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = avatarFile()
        file.writeBytes(jpegBytes)
        prefs.edit {
            it[KEY_AVATAR_SET] = true
            it[KEY_AVATAR_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun updatedAtMs(): Long? =
        prefs.data.first()[KEY_AVATAR_UPDATED_AT]

    /** One-tap delete. The file is removed immediately. */
    suspend fun clearAvatar() = withContext(Dispatchers.IO) {
        runCatching { avatarFile().delete() }
        prefs.edit {
            it[KEY_AVATAR_SET] = false
            it.remove(KEY_AVATAR_UPDATED_AT)
        }
    }
}
