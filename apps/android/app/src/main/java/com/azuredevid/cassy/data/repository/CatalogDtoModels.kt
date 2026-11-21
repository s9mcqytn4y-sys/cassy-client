package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.CatalogSnapshot
import com.azuredevid.cassy.data.model.CatalogVariant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CatalogViewDto(
    val categories: List<CategoryDto> = emptyList(),
    val products: List<ProductDto> = emptyList()
) {
    fun toSnapshot(): CatalogSnapshot {
        val cats = categories.map { it.toDomain() }
        val catNames = cats.associate { it.id to it.name }
        return CatalogSnapshot(
            categories = cats,
            products = products.map { it.toDomain(catNames) }
        )
    }
}

@Serializable
internal data class CategoryDto(
    val id: String,
    val name: String,
    @SerialName("display_order") val displayOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true
) {
    fun toDomain() = CatalogCategory(id = id, name = name, displayOrder = displayOrder, isActive = isActive)
}

@Serializable
internal data class CategoryUpsertDto(
    val name: String,
    @SerialName("display_order") val displayOrder: Int,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
internal data class VariantDto(
    val id: String,
    val name: String,
    val sku: String? = null,
    val price: Long,
    @SerialName("is_default") val isDefault: Boolean = false,
    val available: Boolean? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null
) {
    fun toDomain() = CatalogVariant(
        id = id,
        name = name,
        sku = sku.orEmpty(),
        price = price,
        isDefault = isDefault,
        available = available ?: isAvailable ?: true
    )
}

@Serializable
internal data class VariantUpsertDto(
    val name: String,
    val sku: String? = null,
    val price: Long,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("is_available") val isAvailable: Boolean
)

@Serializable
internal data class ProductDto(
    val id: String,
    @SerialName("category_id") val categoryId: String? = null,
    val name: String,
    val description: String = "",
    val type: String = "",
    @SerialName("product_type") val productType: String = "",
    @SerialName("prep_station") val prepStation: String = "bar",
    @SerialName("image_url") val imageUrl: String? = null,
    val available: Boolean? = null,
    @SerialName("is_available") val isAvailable: Boolean? = null,
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("base_price") val basePrice: Long? = null,
    val variants: List<VariantDto> = emptyList()
) {
    fun toDomain(categoryNames: Map<String, String>): CatalogProduct {
        val domainVariants = variants.map { it.toDomain() }
        val resolvedPrice = basePrice
            ?: domainVariants.firstOrNull { it.isDefault }?.price
            ?: domainVariants.firstOrNull()?.price
            ?: 0L
        return CatalogProduct(
            id = id,
            name = name,
            categoryId = categoryId.orEmpty(),
            categoryName = categoryId?.let { categoryNames[it] }.orEmpty(),
            description = description,
            basePriceIdr = resolvedPrice,
            imageUrl = imageUrl.orEmpty(),
            prepStation = prepStation.ifBlank { "bar" },
            isAvailable = available ?: isAvailable ?: true,
            stockQuantity = stockQuantity ?: 0,
            productType = type.ifBlank { productType }.ifBlank { "prepared" },
            variants = domainVariants
        )
    }
}

@Serializable
internal data class ProductCreateDto(
    @SerialName("category_id") val categoryId: String? = null,
    val name: String,
    val description: String,
    @SerialName("product_type") val productType: String,
    @SerialName("prep_station") val prepStation: String,
    @SerialName("is_available") val isAvailable: Boolean,
    val variants: List<VariantUpsertDto>
)

@Serializable
internal data class ProductUpdateDto(
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("clear_category") val clearCategory: Boolean = false,
    val name: String,
    val description: String,
    @SerialName("product_type") val productType: String,
    @SerialName("prep_station") val prepStation: String,
    @SerialName("is_available") val isAvailable: Boolean
)

@Serializable
internal data class AvailabilityRequestDto(
    @SerialName("is_available") val isAvailable: Boolean
)

@Serializable
internal data class CreateMediaUploadDto(
    val kind: String,
    @SerialName("product_id") val productId: String,
    @SerialName("content_type") val contentType: String,
    val size: Long
)

@Serializable
internal data class MediaUploadTicketDto(
    val id: String,
    @SerialName("upload_url") val uploadUrl: String,
    val headers: Map<String, String> = emptyMap()
)

@Serializable
internal data class CompletedMediaUploadDto(
    @SerialName("image_url") val imageUrl: String
)
