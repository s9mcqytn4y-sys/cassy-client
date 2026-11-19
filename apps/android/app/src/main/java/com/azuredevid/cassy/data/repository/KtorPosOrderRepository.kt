package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.model.CreateOrderItemInputDto
import com.azuredevid.cassy.data.model.CreateOrderRequestDto
import com.azuredevid.cassy.data.model.CreateOrderResponseDto
import com.azuredevid.cassy.data.model.InitiatePaymentRequestDto
import com.azuredevid.cassy.data.model.InitiatePaymentResponseDto
import com.azuredevid.cassy.data.model.OrderDetailResponseDto
import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentResult
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderDetail
import com.azuredevid.cassy.domain.repository.DeviceOfflineOrderSyncItem
import com.azuredevid.cassy.domain.repository.DeviceOfflineOrderSyncOutcome
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class OfflinePaymentInputDto(
    val method: String = "cash",
    @SerialName("cash_tendered_amount") val cashTenderedAmount: Long,
    val reference: String = ""
)

@Serializable
private data class OfflineOrderInputDto(
    @SerialName("client_order_id") val clientOrderId: String,
    @SerialName("offline_queue_number") val offlineQueueNumber: String,
    @SerialName("offline_created_at") val offlineCreatedAt: String,
    @SerialName("cashier_id") val cashierId: String,
    @SerialName("shift_id") val shiftId: String,
    @SerialName("catalog_version") val catalogVersion: String,
    @SerialName("order_type") val orderType: String,
    @SerialName("customer_name") val customerName: String,
    val notes: String? = null,
    val items: List<CreateOrderItemInputDto>,
    val payment: OfflinePaymentInputDto,
    @SerialName("printed_locally") val printedLocally: Boolean = true
)

@Serializable
private data class OfflineSyncRequestDto(
    val orders: List<OfflineOrderInputDto>
)

@Serializable
private data class OfflineSyncResultDto(
    @SerialName("client_order_id") val clientOrderId: String,
    val status: String,
    val reason: String = "",
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("order_number") val orderNumber: String = "",
    @SerialName("queue_number") val queueNumber: String = ""
)

@Serializable
private data class OfflineSyncResponseDto(
    val results: List<OfflineSyncResultDto> = emptyList()
)

class KtorPosOrderRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PosOrderRepository {

    override suspend fun createOrder(input: CreatePosOrderInput): Result<PosOrder> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val dto = CreateOrderRequestDto(
                idempotencyKey = input.idempotencyKey,
                tableNumber = input.tableNumber?.takeIf { it.isNotBlank() },
                customerName = input.customerName.trim().ifBlank { "Pelanggan Walk-In" },
                customerPhone = input.customerPhone?.takeIf { it.isNotBlank() },
                orderType = input.orderType,
                billingMode = input.billingMode,
                expectedTotal = input.expectedTotal,
                expectedQuoteFingerprint = input.expectedQuoteFingerprint?.takeIf { it.isNotBlank() },
                notes = input.notes.takeIf { it.isNotBlank() },
                items = input.items.map {
                    CreateOrderItemInputDto(
                        productId = it.productId,
                        quantity = it.quantity,
                        notes = it.notes.takeIf { n -> n.isNotBlank() }
                    )
                }
            )
            val response = httpClient.post("$baseUrl/api/v1/pos/orders") {
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membuat pesanan ke server."))
            }
            Result.success(response.body<CreateOrderResponseDto>().order.toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membuat pesanan ke server."))
        }
    }

    override suspend fun initiatePayment(input: InitiatePosPaymentInput): Result<InitiatePosPaymentResult> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val dto = InitiatePaymentRequestDto(
                idempotencyKey = input.idempotencyKey,
                splitId = input.splitId?.takeIf { it.isNotBlank() },
                method = input.method,
                cashTenderedAmount = input.cashTenderedAmount,
                drawerId = input.drawerId?.takeIf { it.isNotBlank() }
            )
            val response = httpClient.post("$baseUrl/api/v1/pos/orders/${input.orderId}/payments") {
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memproses pembayaran."))
            }
            Result.success(response.body<InitiatePaymentResponseDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memproses pembayaran."))
        }
    }

    override suspend fun getOrder(orderId: String): Result<PosOrderDetail> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.get("$baseUrl/api/v1/pos/orders/$orderId") {
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memeriksa status pesanan."))
            }
            Result.success(response.body<OrderDetailResponseDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memeriksa status pesanan."))
        }
    }

    override suspend fun syncDeviceOfflineOrders(
        orders: List<DeviceOfflineOrderSyncItem>
    ): Result<List<DeviceOfflineOrderSyncOutcome>> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val dto = OfflineSyncRequestDto(
                orders = orders.map { order ->
                    OfflineOrderInputDto(
                        clientOrderId = order.clientOrderId,
                        offlineQueueNumber = order.offlineQueueNumber,
                        offlineCreatedAt = order.offlineCreatedAtIso,
                        cashierId = order.cashierId,
                        shiftId = order.shiftId,
                        catalogVersion = order.catalogVersion,
                        orderType = order.orderType,
                        customerName = order.customerName.trim().ifBlank { "Pelanggan Walk-In" },
                        notes = order.notes.takeIf { it.isNotBlank() },
                        items = order.items.map { item ->
                            CreateOrderItemInputDto(
                                productId = item.productId,
                                quantity = item.quantity,
                                notes = item.notes.takeIf { it.isNotBlank() }
                            )
                        },
                        payment = OfflinePaymentInputDto(
                            method = "cash",
                            cashTenderedAmount = order.cashTenderedAmount
                        ),
                        printedLocally = order.printedLocally
                    )
                }
            )
            val response = httpClient.post("$baseUrl/api/v1/device/offline-orders/sync") {
                contentType(ContentType.Application.Json)
                setBody(dto)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal menyinkronkan antrean pesanan offline.")
                )
            }
            val results = response.body<OfflineSyncResponseDto>().results.map {
                DeviceOfflineOrderSyncOutcome(
                    clientOrderId = it.clientOrderId,
                    status = it.status,
                    reason = it.reason,
                    orderId = it.orderId,
                    orderNumber = it.orderNumber,
                    queueNumber = it.queueNumber
                )
            }
            Result.success(results)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal menyinkronkan antrean pesanan offline."))
        }
    }
}
