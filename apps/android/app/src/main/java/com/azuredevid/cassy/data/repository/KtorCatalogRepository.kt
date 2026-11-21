package com.azuredevid.cassy.data.repository

import android.content.Context
import com.azuredevid.cassy.core.coroutines.runCatchingCancellable
import com.azuredevid.cassy.data.local.CachedCategoryEntity
import com.azuredevid.cassy.data.local.CachedProductEntity
import com.azuredevid.cassy.data.local.CatalogCacheDao
import com.azuredevid.cassy.data.model.CatalogCategory
import com.azuredevid.cassy.data.model.CatalogProduct
import com.azuredevid.cassy.data.model.CatalogSnapshot
import com.azuredevid.cassy.data.model.CatalogVariant
import com.azuredevid.cassy.data.model.ProductUpsertInput
import com.azuredevid.cassy.data.model.VariantUpsertInput
import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.repository.CatalogRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KtorCatalogRepository(
    private val context: Context? = null,
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val catalogCacheDao: CatalogCacheDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CatalogRepository {

    private val cacheJson = Json { ignoreUnknownKeys = true }

    override suspend fun getCatalog(storeSlug: String): Result<List<CatalogProduct>> = withContext(ioDispatcher) {
        val cleanSlug = storeSlug.trim()
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.get("$baseUrl/api/v1/public/stores/$cleanSlug/catalog")
            if (response.status.isSuccess()) {
                val snapshot = response.body<CatalogViewDto>().toSnapshot()
                persistSnapshot(cleanSlug, snapshot)
                return@withContext Result.success(snapshot.products)
            }
            val cached = readCachedProducts(cleanSlug)
            if (cached.isNotEmpty()) return@withContext Result.success(cached)
            Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memuat katalog menu dari server."))
        } catch (t: Throwable) {
            val cached = readCachedProducts(cleanSlug)
            if (cached.isNotEmpty()) return@withContext Result.success(cached)
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat katalog menu."))
        }
    }

    private suspend fun persistSnapshot(storeSlug: String, snapshot: CatalogSnapshot) {
        val dao = catalogCacheDao ?: return
        val now = System.currentTimeMillis()
        runCatchingCancellable {
            dao.replaceSnapshot(
                storeSlug = storeSlug,
                categories = snapshot.categories.map {
                    CachedCategoryEntity(it.id, storeSlug, it.name, it.displayOrder, it.isActive, now)
                },
                products = snapshot.products.map {
                    CachedProductEntity(
                        id = it.id,
                        storeSlug = storeSlug,
                        categoryId = it.categoryId,
                        name = it.name,
                        basePriceIdr = it.basePriceIdr,
                        isAvailable = it.isAvailable,
                        payloadJson = cacheJson.encodeToString(it),
                        updatedAtMs = now
                    )
                }
            )
        }
    }

    private suspend fun readCachedProducts(storeSlug: String): List<CatalogProduct> {
        val dao = catalogCacheDao ?: return emptyList()
        return runCatchingCancellable {
            dao.getProducts(storeSlug).mapNotNull { entity ->
                runCatching { cacheJson.decodeFromString<CatalogProduct>(entity.payloadJson) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun getStaffCatalog(): Result<CatalogSnapshot> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.get("$baseUrl/api/v1/pos/catalog")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal memuat katalog staf.")
                )
            }
            Result.success(response.body<CatalogViewDto>().toSnapshot())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat katalog staf."))
        }
    }

    override suspend fun createCategory(
        name: String,
        displayOrder: Int,
        isActive: Boolean
    ): Result<CatalogCategory> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.post("$baseUrl/api/v1/catalog/categories") {
                contentType(ContentType.Application.Json)
                setBody(CategoryUpsertDto(name.trim(), displayOrder, isActive))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal menambah kategori.")
                )
            }
            Result.success(response.body<CategoryDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal menambah kategori."))
        }
    }

    override suspend fun updateCategory(
        id: String,
        name: String,
        displayOrder: Int,
        isActive: Boolean
    ): Result<CatalogCategory> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.patch("$baseUrl/api/v1/catalog/categories/${id.trim()}") {
                contentType(ContentType.Application.Json)
                setBody(CategoryUpsertDto(name.trim(), displayOrder, isActive))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal memperbarui kategori.")
                )
            }
            Result.success(response.body<CategoryDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memperbarui kategori."))
        }
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.delete("$baseUrl/api/v1/catalog/categories/${id.trim()}")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal menghapus kategori.")
                )
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal menghapus kategori."))
        }
    }

    override suspend fun createProduct(input: ProductUpsertInput): Result<CatalogProduct> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val price = input.basePriceIdr ?: 0L
            val body = ProductCreateDto(
                categoryId = input.categoryId?.takeIf { it.isNotBlank() },
                name = input.name.trim(),
                description = input.description.trim(),
                productType = input.productType,
                prepStation = input.prepStation,
                isAvailable = input.isAvailable,
                variants = listOf(
                    VariantUpsertDto(
                        name = input.defaultVariantName.ifBlank { "Reguler" },
                        price = price,
                        isDefault = true,
                        isAvailable = true
                    )
                )
            )
            val response = httpClient.post("$baseUrl/api/v1/catalog/products") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal membuat produk.")
                )
            }
            Result.success(response.body<ProductDto>().toDomain(categoryNames = emptyMap()))
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membuat produk."))
        }
    }

    override suspend fun updateProduct(id: String, input: ProductUpsertInput): Result<CatalogProduct> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val body = ProductUpdateDto(
                categoryId = input.categoryId?.takeIf { it.isNotBlank() },
                clearCategory = input.clearCategory,
                name = input.name.trim(),
                description = input.description.trim(),
                productType = input.productType,
                prepStation = input.prepStation,
                isAvailable = input.isAvailable
            )
            val response = httpClient.patch("$baseUrl/api/v1/catalog/products/${id.trim()}") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal memperbarui produk.")
                )
            }
            Result.success(response.body<ProductDto>().toDomain(categoryNames = emptyMap()))
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memperbarui produk."))
        }
    }

    override suspend fun archiveProduct(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.delete("$baseUrl/api/v1/catalog/products/${id.trim()}")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal mengarsipkan produk.")
                )
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mengarsipkan produk."))
        }
    }

    override suspend fun setProductAvailability(id: String, isAvailable: Boolean): Result<Unit> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.patch("$baseUrl/api/v1/catalog/products/${id.trim()}/availability") {
                contentType(ContentType.Application.Json)
                setBody(AvailabilityRequestDto(isAvailable))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal mengubah ketersediaan produk.")
                )
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mengubah ketersediaan produk."))
        }
    }

    override suspend fun createVariant(productId: String, input: VariantUpsertInput): Result<CatalogVariant> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.post("$baseUrl/api/v1/catalog/products/${productId.trim()}/variants") {
                contentType(ContentType.Application.Json)
                setBody(
                    VariantUpsertDto(
                        name = input.name.trim(),
                        sku = input.sku.trim().ifBlank { null },
                        price = input.price,
                        isDefault = input.isDefault,
                        isAvailable = input.isAvailable
                    )
                )
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal menambah varian.")
                )
            }
            Result.success(response.body<VariantDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal menambah varian."))
        }
    }

    override suspend fun updateVariant(variantId: String, input: VariantUpsertInput): Result<CatalogVariant> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.patch("$baseUrl/api/v1/catalog/variants/${variantId.trim()}") {
                contentType(ContentType.Application.Json)
                setBody(
                    VariantUpsertDto(
                        name = input.name.trim(),
                        sku = input.sku.trim().ifBlank { null },
                        price = input.price,
                        isDefault = input.isDefault,
                        isAvailable = input.isAvailable
                    )
                )
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal memperbarui varian.")
                )
            }
            Result.success(response.body<VariantDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memperbarui varian."))
        }
    }

    override suspend fun archiveVariant(variantId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val response = httpClient.delete("$baseUrl/api/v1/catalog/variants/${variantId.trim()}")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal mengarsipkan varian.")
                )
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mengarsipkan varian."))
        }
    }

    override suspend fun uploadProductImage(productId: String, webpBytes: ByteArray): Result<String> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl()
            val ticketResponse = httpClient.post("$baseUrl/api/v1/media/uploads") {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateMediaUploadDto(
                        kind = "product_image",
                        productId = productId.trim(),
                        contentType = "image/webp",
                        size = webpBytes.size.toLong()
                    )
                )
            }
            if (!ticketResponse.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(ticketResponse, "Gagal meminta URL unggah foto.")
                )
            }
            val ticket = ticketResponse.body<MediaUploadTicketDto>()

            val putResponse = httpClient.put(ticket.uploadUrl) {
                ticket.headers.forEach { (key, value) ->
                    if (!key.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                        header(key, value)
                    }
                }
                contentType(ContentType("image", "webp"))
                setBody(webpBytes)
            }
            if (!putResponse.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(putResponse, "Gagal mengunggah file gambar ke penyimpanan.")
                )
            }

            val completeResponse = httpClient.post("$baseUrl/api/v1/media/uploads/${ticket.id}/complete")
            if (!completeResponse.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(completeResponse, "Gagal memverifikasi foto produk.")
                )
            }
            val completed = completeResponse.body<CompletedMediaUploadDto>()
            Result.success(completed.imageUrl)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mengunggah foto produk."))
        }
    }
}
