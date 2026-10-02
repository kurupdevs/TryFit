package com.kurupdevs.tryfit.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manual TryOnHistoryDao implementation (KSP codegen unavailable). */
class TryOnHistoryDao_Impl(private val __db: RoomDatabase) : TryOnHistoryDao {
    private val _flow = MutableStateFlow<List<TryOnHistoryEntity>>(emptyList())

    init { refresh() }

    companion object {
        @JvmStatic fun getRequiredConverters(): List<Class<*>> = emptyList()
    }

    private fun db(): SupportSQLiteDatabase = __db.openHelper.writableDatabase

    private fun refresh() {
        _flow.value = queryAll()
    }

    private fun readRow(c: android.database.Cursor): TryOnHistoryEntity {
        val iSession = c.getColumnIndexOrThrow("session_id")
        val iProduct = c.getColumnIndexOrThrow("product_id")
        val iName = c.getColumnIndexOrThrow("product_name")
        val iStatus = c.getColumnIndexOrThrow("status")
        val iResult = c.getColumnIndexOrThrow("result_path")
        val iInput = c.getColumnIndexOrThrow("input_path")
        val iRating = c.getColumnIndexOrThrow("rating")
        val iCreated = c.getColumnIndexOrThrow("created_at")
        return TryOnHistoryEntity(
            sessionId = c.getString(iSession),
            productId = if (c.isNull(iProduct)) null else c.getString(iProduct),
            productName = if (c.isNull(iName)) null else c.getString(iName),
            status = c.getString(iStatus),
            resultPath = if (c.isNull(iResult)) null else c.getString(iResult),
            inputPath = if (c.isNull(iInput)) null else c.getString(iInput),
            rating = if (c.isNull(iRating)) null else c.getInt(iRating),
            createdAt = c.getLong(iCreated),
        )
    }

    private fun queryAll(): List<TryOnHistoryEntity> {
        val c = db().query("SELECT * FROM tryon_history ORDER BY created_at DESC LIMIT 200")
        c.use {
            val out = ArrayList<TryOnHistoryEntity>(it.count)
            while (it.moveToNext()) out.add(readRow(it))
            return out
        }
    }

    override fun observeAll(): Flow<List<TryOnHistoryEntity>> = _flow.asStateFlow()

    override suspend fun byId(sessionId: String): TryOnHistoryEntity? {
        val c = db().query(
            "SELECT * FROM tryon_history WHERE session_id = ? LIMIT 1",
            arrayOf<Any>(sessionId),
        )
        c.use { return if (it.moveToFirst()) readRow(it) else null }
    }

    private fun bindInsert(stmt: androidx.sqlite.db.SupportSQLiteStatement, e: TryOnHistoryEntity) {
        stmt.bindString(1, e.sessionId)
        if (e.productId == null) stmt.bindNull(2) else stmt.bindString(2, e.productId)
        if (e.productName == null) stmt.bindNull(3) else stmt.bindString(3, e.productName)
        stmt.bindString(4, e.status)
        if (e.resultPath == null) stmt.bindNull(5) else stmt.bindString(5, e.resultPath)
        if (e.inputPath == null) stmt.bindNull(6) else stmt.bindString(6, e.inputPath)
        if (e.rating == null) stmt.bindNull(7) else stmt.bindLong(7, e.rating.toLong())
        stmt.bindLong(8, e.createdAt)
    }

    override suspend fun upsert(entry: TryOnHistoryEntity) {
        val stmt = db().compileStatement(
            "INSERT OR REPLACE INTO tryon_history " +
                "(session_id, product_id, product_name, status, result_path, input_path, rating, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )
        stmt.use { bindInsert(it, entry); it.executeInsert() }
        refresh()
    }

    override suspend fun update(entry: TryOnHistoryEntity) {
        // UPDATE with all columns (matches @Update semantics on full entity).
        val stmt = db().compileStatement(
            "UPDATE tryon_history SET product_id = ?, product_name = ?, status = ?, " +
                "result_path = ?, input_path = ?, rating = ?, created_at = ? WHERE session_id = ?"
        )
        stmt.use {
            if (entry.productId == null) it.bindNull(1) else it.bindString(1, entry.productId)
            if (entry.productName == null) it.bindNull(2) else it.bindString(2, entry.productName)
            it.bindString(3, entry.status)
            if (entry.resultPath == null) it.bindNull(4) else it.bindString(4, entry.resultPath)
            if (entry.inputPath == null) it.bindNull(5) else it.bindString(5, entry.inputPath)
            if (entry.rating == null) it.bindNull(6) else it.bindLong(6, entry.rating.toLong())
            it.bindLong(7, entry.createdAt)
            it.bindString(8, entry.sessionId)
            it.executeUpdateDelete()
        }
        refresh()
    }

    override suspend fun delete(entry: TryOnHistoryEntity) {
        val stmt = db().compileStatement("DELETE FROM tryon_history WHERE session_id = ?")
        stmt.use {
            it.bindString(1, entry.sessionId)
            it.executeUpdateDelete()
        }
        refresh()
    }

    override suspend fun clearAll() {
        db().execSQL("DELETE FROM tryon_history")
        refresh()
    }
}
