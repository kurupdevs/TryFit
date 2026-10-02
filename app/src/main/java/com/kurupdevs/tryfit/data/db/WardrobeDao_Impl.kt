package com.kurupdevs.tryfit.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manual WardrobeDao implementation (KSP codegen unavailable in the manual
 * build pipeline). Backed directly by SupportSQLiteDatabase.
 */
class WardrobeDao_Impl(private val __db: RoomDatabase) : WardrobeDao {
    private val _flow = MutableStateFlow<List<WardrobeItemEntity>>(emptyList())

    init { refresh() }

    companion object {
        @JvmStatic fun getRequiredConverters(): List<Class<*>> = emptyList()
    }

    private fun db(): SupportSQLiteDatabase = __db.openHelper.writableDatabase

    private fun refresh() {
        _flow.value = queryAll()
    }

    private fun queryAll(): List<WardrobeItemEntity> {
        val c = db().query("SELECT * FROM wardrobe_items ORDER BY added_at DESC")
        c.use {
            val iId = it.getColumnIndexOrThrow("product_id")
            val iAdded = it.getColumnIndexOrThrow("added_at")
            val iNotes = it.getColumnIndexOrThrow("notes")
            val out = ArrayList<WardrobeItemEntity>(it.count)
            while (it.moveToNext()) {
                out.add(
                    WardrobeItemEntity(
                        productId = it.getString(iId),
                        addedAt = it.getLong(iAdded),
                        notes = if (it.isNull(iNotes)) null else it.getString(iNotes),
                    )
                )
            }
            return out
        }
    }

    override fun observeAll(): Flow<List<WardrobeItemEntity>> = _flow.asStateFlow()

    override suspend fun exists(productId: String): Boolean {
        val c = db().query(
            "SELECT EXISTS(SELECT 1 FROM wardrobe_items WHERE product_id = ?)",
            arrayOf<Any>(productId),
        )
        c.use { return it.moveToFirst() && it.getInt(0) != 0 }
    }

    override suspend fun upsert(item: WardrobeItemEntity) {
        val stmt = db().compileStatement(
            "INSERT OR REPLACE INTO wardrobe_items (product_id, added_at, notes) VALUES (?, ?, ?)"
        )
        stmt.use {
            it.bindString(1, item.productId)
            it.bindLong(2, item.addedAt)
            if (item.notes == null) it.bindNull(3) else it.bindString(3, item.notes)
            it.executeInsert()
        }
        refresh()
    }

    override suspend fun deleteByProductId(productId: String) {
        val stmt = db().compileStatement("DELETE FROM wardrobe_items WHERE product_id = ?")
        stmt.use {
            it.bindString(1, productId)
            it.executeUpdateDelete()
        }
        refresh()
    }

    override suspend fun clearAll() {
        db().execSQL("DELETE FROM wardrobe_items")
        refresh()
    }
}
