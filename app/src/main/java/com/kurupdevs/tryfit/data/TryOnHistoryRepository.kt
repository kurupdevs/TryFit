package com.kurupdevs.tryfit.data

import com.kurupdevs.tryfit.data.db.TryFitDatabase
import com.kurupdevs.tryfit.data.db.TryOnHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Local try-on session history. Result/input images live in app-private
 * files (`filesDir/tryon_results/`); this repo only tracks their paths plus
 * status and the user's thumbs rating.
 */
class TryOnHistoryRepository(private val db: TryFitDatabase) {

    fun observeHistory(): Flow<List<TryOnHistoryEntity>> = db.historyDao().observeAll()

    suspend fun byId(sessionId: String): TryOnHistoryEntity? =
        db.historyDao().byId(sessionId)

    suspend fun recordStarted(
        sessionId: String,
        productId: String?,
        productName: String?,
        inputPath: String?,
    ) {
        db.historyDao().upsert(
            TryOnHistoryEntity(
                sessionId = sessionId,
                productId = productId,
                productName = productName,
                status = "PROCESSING",
                resultPath = null,
                inputPath = inputPath,
            )
        )
    }

    suspend fun recordDone(sessionId: String, resultPath: String) {
        db.historyDao().byId(sessionId)?.let {
            db.historyDao().update(it.copy(status = "DONE", resultPath = resultPath))
        }
    }

    suspend fun recordFailed(sessionId: String) {
        db.historyDao().byId(sessionId)?.let {
            db.historyDao().update(it.copy(status = "FAILED"))
        }
    }

    suspend fun recordCancelled(sessionId: String) {
        db.historyDao().byId(sessionId)?.let {
            db.historyDao().update(it.copy(status = "CANCELLED"))
        }
    }

    /** Thumbs rating: 1 = down, 2 = up. */
    suspend fun rate(sessionId: String, rating: Int?) {
        db.historyDao().byId(sessionId)?.let {
            db.historyDao().update(it.copy(rating = rating?.coerceIn(1, 2)))
        }
    }

    suspend fun delete(sessionId: String) {
        db.historyDao().byId(sessionId)?.let { entry ->
            runCatching { entry.resultPath?.let { java.io.File(it).delete() } }
            runCatching { entry.inputPath?.let { java.io.File(it).delete() } }
            db.historyDao().delete(entry)
        }
    }

    suspend fun clearAll() = db.historyDao().clearAll()
}
