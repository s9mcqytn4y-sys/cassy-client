package com.azuredevid.cassy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedCategoryEntity::class,
        CachedProductEntity::class,
        OfflinePendingOrderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CassyDatabase : RoomDatabase() {
    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun offlineOrderDao(): OfflineOrderDao

    companion object {
        const val DATABASE_NAME = "cassy_pos_cache.db"
    }
}
