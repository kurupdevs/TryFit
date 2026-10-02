package com.kurupdevs.tryfit.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** Manual NotificationDao implementation (KSP codegen unavailable). */
class NotificationDao_Impl(private val __db: RoomDatabase) : NotificationDao {
    private val _flow = MutableStateFlow<List<NotificationEntity>>(emptyList())

    init { refresh() }

    companion object {
        @JvmStatic fun getRequiredConverters(): List<Class<*>> = emptyList()
    }

    private fun db(): SupportSQLiteDatabase = __db.openHelper.writableDatabase

    private fun refresh() {
        _flow.value = queryAll()
    }

    private fun readRow(c: android.database.Cursor): NotificationEntity {
        val iId = c.getColumnIndexOrThrow("id")
        val iTitle = c.getColumnIndexOrThrow("title")
        val iBody = c.getColumnIndexOrThrow("body")
        val iType = c.getColumnIndexOrThrow("type")
        val iRead = c.getColumnIndexOrThrow("read")
        val iDeep = c.getColumnIndexOrThrow("deep_link")
        val iCreated = c.getColumnIndexOrThrow("created_at")
        return NotificationEntity(
            id = c.getString(iId),
            title = c.getString(iTitle),
            body = c.getString(iBody),
            type = c.getString(iType),
            read = c.getInt(iRead) != 0,
            deepLink = if (c.isNull(iDeep)) null else c.getString(iDeep),
            createdAt = c.getLong(iCreated),
        )
    }

    private fun queryAll(): List<NotificationEntity> {
        val c = db().query("SELECT * FROM notifications ORDER BY created_at DESC LIMIT 50")
        c.use {
            val out = ArrayList<NotificationEntity>(it.count)
            while (it.moveToNext()) out.add(readRow(it))
            return out
        }
    }

    override fun observeAll(): Flow<List<NotificationEntity>> = _flow.asStateFlow()

    override fun observeUnreadCount(): Flow<Int> = _flow.map { list -> list.count { !it.read } }

    override suspend fun upsert(notification: NotificationEntity) {
        val stmt = db().compileStatement(
            "INSERT OR REPLACE INTO notifications " +
                "(id, title, body, type, read, deep_link, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        )
        stmt.use {
            it.bindString(1, notification.id)
            it.bindString(2, notification.title)
            it.bindString(3, notification.body)
            it.bindString(4, notification.type)
            it.bindLong(5, if (notification.read) 1 else 0)
            if (notification.deepLink == null) it.bindNull(6) else it.bindString(6, notification.deepLink)
            it.bindLong(7, notification.createdAt)
            it.executeInsert()
        }
        refresh()
    }

    override suspend fun markRead(id: String) {
        val stmt = db().compileStatement("UPDATE notifications SET read = 1 WHERE id = ?")
        stmt.use {
            it.bindString(1, id)
            it.executeUpdateDelete()
        }
        refresh()
    }

    override suspend fun markAllRead() {
        db().execSQL("UPDATE notifications SET read = 1")
        refresh()
    }

    override suspend fun clearAll() {
        db().execSQL("DELETE FROM notifications")
        refresh()
    }
}
