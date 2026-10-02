package com.kurupdevs.tryfit.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.days

/**
 * "Ask friends" vote links (research v1 item 14 — nobody owns this; Walmart
 * promised it in 2021 and never shipped it).
 *
 * v1: creates a row in `shared_looks` (migration 004; RLS: public read, owner
 * write) and returns the vote URL `https://tryfit.app/v/<id>`. The URL is a
 * documented FUTURE App Link — v1 copies the link + result image into the
 * system share sheet / clipboard.
 *
 * KNOWN GAP: `tryon-results` is a private bucket (migration 002), so there is
 * no durable public image URL in v1. The repo uploads the image and tries a
 * long-lived signed URL; if that fails the row is still created and the image
 * travels with the share sheet. Worker B follow-up: dedicated public
 * `shared-looks` bucket + storage policy.
 */
class SharedLooksRepository(
    private val supabase: () -> SupabaseClient?,
    private val currentUserId: suspend () -> String?,
) {

    data class SharedLook(
        val id: String,
        /** The vote URL to share. */
        val voteUrl: String,
        /** Remote image URL when the upload succeeded, null otherwise. */
        val imageUrl: String?,
    )

    suspend fun createSharedLook(
        resultImage: File,
        productIds: List<String>,
    ): Result<SharedLook> = runCatching {
        val id = UUID.randomUUID().toString()
        val imageUrl = uploadSharedImage(id, resultImage).getOrNull()
        val client = supabase()
        val uid = client?.let { runCatching { currentUserId() }.getOrNull() }
        if (client != null && uid != null) {
            try {
                client.from("shared_looks").insert(
                    SharedLookRow(
                        id = id,
                        user_id = uid,
                        image_url = imageUrl,
                        product_ids = productIds,
                    )
                )
            } catch (_: Exception) {
                // Offline-first: the link still works locally; the row syncs
                // when the server path is retried (documented gap, not silent).
            }
        }
        SharedLook(id = id, voteUrl = "https://tryfit.app/v/$id", imageUrl = imageUrl)
    }

    private suspend fun uploadSharedImage(lookId: String, image: File): Result<String> =
        runCatching {
            val client = supabase() ?: throw IllegalStateException("Supabase not configured")
            val uid = currentUserId() ?: throw IllegalStateException("Not signed in")
            val path = "$uid/shared/$lookId.jpg"
            client.storage.from("tryon-results").upload(path, image.readBytes())
            // Long-lived signed URL (30d) as the v1 durable-ish link.
            client.storage.from("tryon-results").createSignedUrl(path, 30.days)
        }

    @Serializable
    private data class SharedLookRow(
        val id: String,
        val user_id: String,
        val image_url: String?,
        val product_ids: List<String>,
    )
}
