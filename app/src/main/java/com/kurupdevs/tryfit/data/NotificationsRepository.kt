package com.kurupdevs.tryfit.data

import com.kurupdevs.tryfit.data.db.NotificationEntity
import com.kurupdevs.tryfit.data.db.TryFitDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Local notifications store (Room, last 50 — SPEC §6 offline rule).
 *
 * Server rows (from the `notifications` table / FCM) merge here; local
 * events ("Your try-on is ready!" when the user picked "notify me") are
 * written directly. The notification list UI reads ONLY this store.
 */
class NotificationsRepository(private val db: TryFitDatabase) {

    fun observe(): Flow<List<NotificationEntity>> = db.notificationDao().observeAll()

    fun observeUnreadCount(): Flow<Int> = db.notificationDao().observeUnreadCount()

    suspend fun add(
        title: String,
        body: String,
        type: String,
        deepLink: String? = null,
    ): NotificationEntity {
        val entity = NotificationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            body = body,
            type = type,
            deepLink = deepLink,
        )
        db.notificationDao().upsert(entity)
        return entity
    }

    /** Convenience: "Your try-on is ready!" deep-linking to the result. */
    suspend fun notifyTryOnReady(sessionId: String, productName: String?) {
        add(
            title = "Your try-on is ready!",
            body = if (productName != null) "See how the $productName looks on you."
            else "Your new look is ready to view.",
            type = "tryon_done",
            deepLink = "tryonResult/$sessionId",
        )
    }

    suspend fun markRead(id: String) = db.notificationDao().markRead(id)

    suspend fun markAllRead() = db.notificationDao().markAllRead()

    suspend fun clearAll() = db.notificationDao().clearAll()
}

/**
 * Push registration seam. FCM wiring (google-services.json, FirebaseMessaging
 * token → Edge Function `registerPushToken`) is NOT in v1 — this interface
 * keeps the call site stable so the real implementation drops in without
 * touching the UI. Documented, not stubbed-silent.
 */
interface PushTokenRegistrar {
    suspend fun registerToken(token: String): Result<Unit>
}

/** v1: no-op. Replace with the FCM implementation when google-services lands. */
class NoOpPushTokenRegistrar : PushTokenRegistrar {
    override suspend fun registerToken(token: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("FCM not wired in v1 (no google-services.json yet)"))
}
