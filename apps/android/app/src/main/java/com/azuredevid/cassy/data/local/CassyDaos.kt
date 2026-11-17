package com.azuredevid.cassy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogCacheDao {
    @Query("SELECT * FROM cached_categories WHERE storeSlug = :storeSlug ORDER BY displayOrder ASC")
    suspend fun getCategories(storeSlug: String): List<CachedCategoryEntity>

    @Query("SELECT * FROM cached_products WHERE storeSlug = :storeSlug ORDER BY name ASC")
    suspend fun getProducts(storeSlug: String): List<CachedProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CachedCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<CachedProductEntity>)

    @Query("DELETE FROM cached_categories WHERE storeSlug = :storeSlug")
    suspend fun clearCategories(storeSlug: String)

    @Query("DELETE FROM cached_products WHERE storeSlug = :storeSlug")
    suspend fun clearProducts(storeSlug: String)

    @Transaction
    suspend fun replaceSnapshot(
        storeSlug: String,
        categories: List<CachedCategoryEntity>,
        products: List<CachedProductEntity>
    ) {
        clearCategories(storeSlug)
        clearProducts(storeSlug)
        if (categories.isNotEmpty()) insertCategories(categories)
        if (products.isNotEmpty()) insertProducts(products)
    }
}

@Dao
interface OfflineOrderDao {
    @Query("SELECT * FROM offline_pending_orders WHERE syncStatus = 'PENDING_SYNC' ORDER BY createdAtMs ASC")
    suspend fun getPendingOrders(): List<OfflinePendingOrderEntity>

    @Query("SELECT COUNT(*) FROM offline_pending_orders WHERE syncStatus = 'PENDING_SYNC'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: OfflinePendingOrderEntity)

    @Query("UPDATE offline_pending_orders SET syncStatus = :status, lastError = :error WHERE clientOrderId = :clientOrderId")
    suspend fun updateStatus(clientOrderId: String, status: String, error: String = "")

    @Query("DELETE FROM offline_pending_orders WHERE syncStatus = 'SYNCED' AND createdAtMs < :olderThanMs")
    suspend fun pruneSynced(olderThanMs: Long)
}
