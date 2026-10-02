package com.kurupdevs.tryfit.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Entities — offline-first mirrors of the Supabase tables (SPEC §6).
// Supabase is the server of record; Room is the always-available cache.
// ---------------------------------------------------------------------------

/** Saved wardrobe items. Mirrors `wardrobe_items(user_id, product_id)`. */
@Entity(tableName = "wardrobe_items")
data class WardrobeItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
)

/** Local try-on session history w/ thumbnails. Mirrors `tryon_sessions`. */
@Entity(tableName = "tryon_history")
data class TryOnHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "product_id")
    val productId: String?,
    @ColumnInfo(name = "product_name")
    val productName: String?,
    /** queued / processing / done / failed / cancelled (TryOnStatus names). */
    val status: String,
    /** App-private file path of the result image (null until done). */
    @ColumnInfo(name = "result_path")
    val resultPath: String?,
    /** App-private file path of the input photo (for before/after). */
    @ColumnInfo(name = "input_path")
    val inputPath: String?,
    /** User rating: 1 = thumbs down, 2 = thumbs up, null = unrated. */
    val rating: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

/** Local notifications. Mirrors `notifications` (SPEC §6). */
@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val body: String,
    /** tryon_done / promo / system (SPEC §6 notification_type). */
    val type: String,
    val read: Boolean = false,
    /** Internal route for deep-linking, e.g. "tryonResult/abc". Null = none. */
    @ColumnInfo(name = "deep_link")
    val deepLink: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

// ---------------------------------------------------------------------------
// DAOs
// ---------------------------------------------------------------------------

@Dao
interface WardrobeDao {
    @Query("SELECT * FROM wardrobe_items ORDER BY added_at DESC")
    fun observeAll(): Flow<List<WardrobeItemEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM wardrobe_items WHERE product_id = :productId)")
    suspend fun exists(productId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WardrobeItemEntity)

    @Query("DELETE FROM wardrobe_items WHERE product_id = :productId")
    suspend fun deleteByProductId(productId: String)

    @Query("DELETE FROM wardrobe_items")
    suspend fun clearAll()
}

@Dao
interface TryOnHistoryDao {
    @Query("SELECT * FROM tryon_history ORDER BY created_at DESC LIMIT 200")
    fun observeAll(): Flow<List<TryOnHistoryEntity>>

    @Query("SELECT * FROM tryon_history WHERE session_id = :sessionId LIMIT 1")
    suspend fun byId(sessionId: String): TryOnHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TryOnHistoryEntity)

    @Update
    suspend fun update(entry: TryOnHistoryEntity)

    @Delete
    suspend fun delete(entry: TryOnHistoryEntity)

    @Query("DELETE FROM tryon_history")
    suspend fun clearAll()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY created_at DESC LIMIT 50")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: NotificationEntity)

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

@Database(
    entities = [WardrobeItemEntity::class, TryOnHistoryEntity::class, NotificationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TryFitDatabase : RoomDatabase() {
    abstract fun wardrobeDao(): WardrobeDao
    abstract fun historyDao(): TryOnHistoryDao
    abstract fun notificationDao(): NotificationDao
}
