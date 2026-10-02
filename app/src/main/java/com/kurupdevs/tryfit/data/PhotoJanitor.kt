package com.kurupdevs.tryfit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Auto-delete janitor for raw user uploads (research §3 trust playbook).
 *
 * Deletes files under `filesDir/uploads/` older than [maxAgeDays]. Runs once
 * per app start from [com.kurupdevs.tryfit.TryFitApplication] on an IO thread.
 * Try-on RESULT images are kept until the user deletes them (or clears the
 * session) — only raw inputs expire automatically.
 */
object PhotoJanitor {

    suspend fun runCleanup(context: Context, maxAgeDays: Int) = withContext(Dispatchers.IO) {
        val uploads = context.filesDir.resolve("uploads")
        if (!uploads.isDirectory) return@withContext
        val cutoff = System.currentTimeMillis() - maxAgeDays * 86_400_000L
        uploads.walkTopDown()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { runCatching { it.delete() } }
    }

    /** Clears ALL user photos immediately (avatar + raw uploads). One-tap privacy action. */
    suspend fun deleteAllUserPhotos(
        context: Context,
        avatarStore: AvatarStore,
    ) = withContext(Dispatchers.IO) {
        avatarStore.clearAvatar()
        context.filesDir.resolve("uploads").deleteRecursively()
        context.cacheDir.resolve("tryon_flow").deleteRecursively()
    }
}
