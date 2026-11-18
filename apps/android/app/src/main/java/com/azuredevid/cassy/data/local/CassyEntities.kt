package com.azuredevid.cassy.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_categories",
    indices = [Index(value = ["storeSlug"])]
)
data class CachedCategoryEntity(
    @PrimaryKey val id: String,
    val storeSlug: String,
    val name: String,
    val displayOrder: Int,
    val isActive: Boolean,
    val updatedAtMs: Long
)

@Entity(
    tableName = "cached_products",
    indices = [Index(value = ["storeSlug", "categoryId"])]
)
data class CachedProductEntity(
    @PrimaryKey val id: String,
    val storeSlug: String,
    val categoryId: String,
    val name: String,
    val basePriceIdr: Long,
    val isAvailable: Boolean,
    val payloadJson: String,
    val updatedAtMs: Long
)

@Entity(
    tableName = "offline_pending_orders",
    indices = [Index(value = ["syncStatus", "createdAtMs"])]
)
data class OfflinePendingOrderEntity(
    @PrimaryKey val clientOrderId: String,
    val storeSlug: String,
    val idempotencyKey: String,
    val payloadJson: String,
    val cashReceivedIdr: Long,
    val createdAtMs: Long,
    val syncStatus: String,
    val lastError: String = ""
) {
    companion object {
        const val STATUS_PENDING = "PENDING_SYNC"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
    }
}
