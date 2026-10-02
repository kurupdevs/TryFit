package com.kurupdevs.tryfit.data

import com.kurupdevs.tryfit.data.db.TryFitDatabase
import com.kurupdevs.tryfit.data.db.WardrobeItemEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Wardrobe repository: Room-first, Supabase write-through when online.
 *
 * Toggles are OPTIMISTIC — the local row flips immediately so the UI never
 * waits on the network; the server call is best-effort inside try/catch and
 * a later [syncFromServer] reconciles. Mirrors the `toggleWardrobe` Edge
 * Function contract (SPEC §7); the function remains the path for
 * authenticated multi-device writes.
 */
class WardrobeRepository(
    private val db: TryFitDatabase,
    private val supabase: () -> SupabaseClient?,
    private val currentUserId: suspend () -> String?,
) {

    fun observeItems(): Flow<List<WardrobeItemEntity>> = db.wardrobeDao().observeAll()

    suspend fun isSaved(productId: String): Boolean = db.wardrobeDao().exists(productId)

    /**
     * Toggles the saved state. Returns the NEW state (true = saved).
     * Local write first (optimistic), server write-through best-effort.
     */
    suspend fun toggle(productId: String, notes: String? = null): Boolean {
        val dao = db.wardrobeDao()
        val nowSaved = if (dao.exists(productId)) {
            dao.deleteByProductId(productId)
            false
        } else {
            dao.upsert(WardrobeItemEntity(productId = productId, notes = notes))
            true
        }
        writeThrough(productId, nowSaved, notes)
        return nowSaved
    }

    suspend fun remove(productId: String) {
        db.wardrobeDao().deleteByProductId(productId)
        writeThrough(productId, false, null)
    }

    suspend fun clearLocal() = db.wardrobeDao().clearAll()

    /** Best-effort pull: replaces local rows with the server's list. */
    suspend fun syncFromServer() {
        val client = supabase() ?: return
        val uid = currentUserId() ?: return
        try {
            val rows = client.from("wardrobe_items")
                .select { filter { eq("user_id", uid) } }
                .decodeList<WardrobeRow>()
            val dao = db.wardrobeDao()
            dao.clearAll()
            rows.forEach { dao.upsert(WardrobeItemEntity(productId = it.product_id, notes = it.notes)) }
        } catch (_: Exception) {
            // Offline-first: keep the local cache; sync retries next launch.
        }
    }

    private suspend fun writeThrough(productId: String, saved: Boolean, notes: String?) {
        val client = supabase() ?: return
        val uid = currentUserId() ?: return
        try {
            if (saved) {
                client.from("wardrobe_items").upsert(WardrobeRow(uid, productId, notes))
            } else {
                client.from("wardrobe_items").delete {
                    filter {
                        eq("user_id", uid)
                        eq("product_id", productId)
                    }
                }
            }
        } catch (_: Exception) {
            // Offline: local state already flipped (optimistic). The next
            // syncFromServer reconciles; no user-visible error for a toggle.
        }
    }

    @Serializable
    private data class WardrobeRow(
        val user_id: String,
        val product_id: String,
        val notes: String? = null,
    )
}
