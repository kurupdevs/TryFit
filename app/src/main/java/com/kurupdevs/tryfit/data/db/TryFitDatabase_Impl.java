package com.kurupdevs.tryfit.data.db;

import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Manual RoomDatabase implementation (KSP codegen unavailable in the manual
 * build pipeline; equivalent to Room's generated TryFitDatabase_Impl).
 */
@SuppressWarnings({"unchecked", "deprecation"})
public class TryFitDatabase_Impl extends TryFitDatabase {
    private volatile WardrobeDao _wardrobeDao;
    private volatile TryOnHistoryDao _historyDao;
    private volatile NotificationDao _notificationDao;

    @Override
    protected SupportSQLiteOpenHelper createOpenHelper(DatabaseConfiguration configuration) {
        final SupportSQLiteOpenHelper.Callback _openCallback =
                new RoomOpenHelper(configuration, new RoomOpenHelper.Delegate(1) {
                    @Override
                    public void createAllTables(SupportSQLiteDatabase _db) {
                        _db.execSQL("CREATE TABLE IF NOT EXISTS `wardrobe_items` (`product_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, `notes` TEXT, PRIMARY KEY(`product_id`))");
                        _db.execSQL("CREATE TABLE IF NOT EXISTS `tryon_history` (`session_id` TEXT NOT NULL, `product_id` TEXT, `product_name` TEXT, `status` TEXT NOT NULL, `result_path` TEXT, `input_path` TEXT, `rating` INTEGER, `created_at` INTEGER NOT NULL, PRIMARY KEY(`session_id`))");
                        _db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `type` TEXT NOT NULL, `read` INTEGER NOT NULL, `deep_link` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))");
                        _db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
                        _db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'manual-impl-v1')");
                    }

                    @Override
                    public void dropAllTables(SupportSQLiteDatabase _db) {
                        _db.execSQL("DROP TABLE IF EXISTS `wardrobe_items`");
                        _db.execSQL("DROP TABLE IF EXISTS `tryon_history`");
                        _db.execSQL("DROP TABLE IF EXISTS `notifications`");
                    }

                    @Override
                    public RoomOpenHelper.ValidationResult onValidateSchema(SupportSQLiteDatabase _db) {
                        return new RoomOpenHelper.ValidationResult(true, null);
                    }
                }, "manual-impl-v1", "manual-impl-v1");
        final SupportSQLiteOpenHelper.Configuration _sqliteConfig =
                SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                        .name(configuration.name)
                        .callback(_openCallback)
                        .build();
        return configuration.sqliteOpenHelperFactory.create(_sqliteConfig);
    }

    @Override
    protected InvalidationTracker createInvalidationTracker() {
        final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
        HashMap<String, HashSet<String>> _viewTables = new HashMap<String, HashSet<String>>(0);
        return new InvalidationTracker(this, _shadowTablesMap, _viewTables,
                "wardrobe_items", "tryon_history", "notifications");
    }

    @Override
    public void clearAllTables() {
        super.assertNotMainThread();
        final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
        try {
            super.beginTransaction();
            _db.execSQL("DELETE FROM `wardrobe_items`");
            _db.execSQL("DELETE FROM `tryon_history`");
            _db.execSQL("DELETE FROM `notifications`");
            super.setTransactionSuccessful();
        } finally {
            super.endTransaction();
        }
    }

    @Override
    protected java.util.Map<Class<?>, java.util.List<Class<?>>> getRequiredTypeConverters() {
        final java.util.HashMap<Class<?>, java.util.List<Class<?>>> _typeConvertersMap =
                new java.util.HashMap<Class<?>, java.util.List<Class<?>>>();
        _typeConvertersMap.put(WardrobeDao.class, WardrobeDao_Impl.getRequiredConverters());
        _typeConvertersMap.put(TryOnHistoryDao.class, TryOnHistoryDao_Impl.getRequiredConverters());
        _typeConvertersMap.put(NotificationDao.class, NotificationDao_Impl.getRequiredConverters());
        return _typeConvertersMap;
    }

    @Override
    public WardrobeDao wardrobeDao() {
        if (_wardrobeDao != null) {
            return _wardrobeDao;
        } else {
            synchronized (this) {
                if (_wardrobeDao == null) {
                    _wardrobeDao = new WardrobeDao_Impl(this);
                }
                return _wardrobeDao;
            }
        }
    }

    @Override
    public TryOnHistoryDao historyDao() {
        if (_historyDao != null) {
            return _historyDao;
        } else {
            synchronized (this) {
                if (_historyDao == null) {
                    _historyDao = new TryOnHistoryDao_Impl(this);
                }
                return _historyDao;
            }
        }
    }

    @Override
    public NotificationDao notificationDao() {
        if (_notificationDao != null) {
            return _notificationDao;
        } else {
            synchronized (this) {
                if (_notificationDao == null) {
                    _notificationDao = new NotificationDao_Impl(this);
                }
                return _notificationDao;
            }
        }
    }
}
