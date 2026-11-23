package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.model.CreateOrderItemInputDto
import com.azuredevid.cassy.data.model.CreateOrderRequestDto
import com.azuredevid.cassy.data.model.CreateOrderResponseDto
import com.azuredevid.cassy.data.model.InitiatePaymentRequestDto
import com.azuredevid.cassy.data.model.InitiatePaymentResponseDto
import com.azuredevid.cassy.data.model.OrderDetailResponseDto
import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.data.network.deviceAuth
import com.azuredevid.cassy.domain.model.CreatePosOrderInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentInput
import com.azuredevid.cassy.domain.model.InitiatePosPaymentResult
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.model.PosOrderDetail
import com.azuredevid.cassy.domain.repository.DeviceOfflineOrderSyncItem
import com.azuredevid.cassy.domain.repository.DeviceOfflineOrderSyncOutcome
import com.azuredevid.cassy.domain.repository.PosCustomerStamps
import com.azuredevid.cassy.domain.repository.PosCustomerVerificationStart
import com.azuredevid.cassy.domain.repository.PosOrderDiscountInput
import com.azuredevid.cassy.domain.repository.PosOrderRepository
import com.azuredevid.cassy.domain.repository.PosOrderVoidInput
import com.azuredevid.cassy.domain.repository.PosReceiptLink
import com.azuredevid.cassy.domain.repository.PosRefundPaymentInput
import com.azuredevid.cassy.domain.repository.PosSalesChannel
import com.azuredevid.cassy.domain.repository.PosSalesChannelPrice
import com.azuredevid.cassy.domain.repository.PosStockOverrideResult
import com.azuredevid.cassy.domain.repository.ReceiptSendResult
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

@Serializable
private data class ReceiptSendRequestDto(
    val channel: String,
    val to: String
)

@Serializable
private data class ReceiptSendResultDto(
    val channel: String = "",
    val recipient: String = "",
    @SerialName("expires_at") val expiresAt: String = ""
)

@Serializable
private data class SalesChannelPriceDto(
    @SerialName("variant_id") val variantId: String,
    val price: Long
)

@Serializable
private data class SalesChannelDto(
    val id: String,
    val name: String,
    @SerialName("commission_bps") val commissionBps: Long = 0L,
    @SerialName("is_active") val isActive: Boolean = true,
    val prices: List<SalesChannelPriceDto> = emptyList()
)

@Serializable
private data class SalesChannelsResponseDto(
    @SerialName("sales_channels") val salesChannels: List<SalesChannelDto> = emptyList()
)

@Serializable
private data class CustomerLookupRequestDto(
    val phone: String
)

@Serializable
private data class CustomerDto(
    val id: String,
    val phone: String = "",
    val name: String = ""
)

@Serializable
private data class CustomerStampsDto(
    val customer: CustomerDto,
    val verified: Boolean = false,
    @SerialName("program_active") val programActive: Boolean = false,
    val stamps: Long = 0L,
    @SerialName("stamps_required") val stampsRequired: Long = 0L,
    @SerialName("reward_ready") val rewardReady: Boolean = false,
    @SerialName("reward_product_ids") val rewardProductIds: List<String> = emptyList()
)

class KtorPosOrderRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Paired device credential; the offline batch endpoint authenticates the device, not the cashier. */
    private val deviceCredential: () -> String? = { null }
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
                drawerId = input.drawerId?.takeIf { it.isNotBlank() },
                reconciliationRef = input.reconciliationRef?.trim()?.takeIf { it.isNotEmpty() }
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
            // Without a device credential the coordinator falls back to replaying orders one by one.
            val credential = deviceCredential()
                ?: return@withContext Result.failure(IllegalStateException("Perangkat belum terdaftar untuk sinkron offline."))
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
                deviceAuth(credential)
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

    override suspend fun sendDigitalReceipt(
        orderId: String,
        channel: String,
        to: String
    ): Result<ReceiptSendResult> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.post("$baseUrl/api/v1/pos/orders/$orderId/receipt/send") {
                contentType(ContentType.Application.Json)
                setBody(ReceiptSendRequestDto(channel = channel.trim().lowercase(), to = to.trim()))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal mengirim struk digital.")
                )
            }
            val dto = response.body<ReceiptSendResultDto>()
            Result.success(
                ReceiptSendResult(
                    channel = dto.channel,
                    recipientMasked = dto.recipient,
                    expiresAtIso = dto.expiresAt
                )
            )
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mengirim struk digital."))
        }
    }

    override suspend fun listSalesChannels(): Result<List<PosSalesChannel>> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.get("$baseUrl/api/v1/pos/sales-channels")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal memuat daftar kanal penjualan.")
                )
            }
            val channels = response.body<SalesChannelsResponseDto>().salesChannels.map { ch ->
                PosSalesChannel(
                    id = ch.id,
                    name = ch.name,
                    commissionBps = ch.commissionBps,
                    isActive = ch.isActive,
                    prices = ch.prices.map { p ->
                        PosSalesChannelPrice(variantId = p.variantId, priceRupiah = p.price)
                    }
                )
            }
            Result.success(channels)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat daftar kanal penjualan."))
        }
    }

    override suspend fun lookupCustomerByPhone(phone: String): Result<PosCustomerStamps> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.post("$baseUrl/api/v1/pos/customers/lookup") {
                contentType(ContentType.Application.Json)
                setBody(CustomerLookupRequestDto(phone = phone.trim()))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Pelanggan tidak ditemukan.")
                )
            }
            val dto = response.body<CustomerStampsDto>()
            Result.success(
                PosCustomerStamps(
                    customerId = dto.customer.id,
                    phone = dto.customer.phone,
                    name = dto.customer.name,
                    verified = dto.verified,
                    programActive = dto.programActive,
                    stamps = dto.stamps,
                    stampsRequired = dto.stampsRequired,
                    rewardReady = dto.rewardReady,
                    rewardProductIds = dto.rewardProductIds
                )
            )
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Pelanggan tidak ditemukan."))
        }
    }

    override suspend fun applyOrderDiscount(orderId: String, input: PosOrderDiscountInput): Result<PosOrder> =
        withContext(ioDispatcher) {
            try {
                val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
                val response = httpClient.post("$baseUrl/api/v1/pos/orders/$orderId/discount") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        ApplyDiscountRequestDto(
                            discountAmount = input.discountAmount,
                            discountBps = input.discountBps,
                            reason = input.reason.trim(),
                            ownerPin = input.ownerPin?.trim()?.ifBlank { null },
                            approvalId = input.approvalId?.trim()?.ifBlank { null }
                        )
                    )
                }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal menerapkan diskon."))
                }
                Result.success(response.body<OrderActionResponseDto>().order.toDomain())
            } catch (t: Throwable) {
                Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal menerapkan diskon."))
            }
        }

    override suspend fun voidOrder(orderId: String, input: PosOrderVoidInput): Result<PosOrder> =
        withContext(ioDispatcher) {
            try {
                val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
                val response = httpClient.post("$baseUrl/api/v1/pos/orders/$orderId/void") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        VoidOrderRequestDto(
                            reason = input.reason.trim(),
                            pin = input.pin.trim(),
                            ownerPin = input.ownerPin?.trim()?.ifBlank { null },
                            approvalId = input.approvalId?.trim()?.ifBlank { null }
                        )
                    )
                }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membatalkan pesanan."))
                }
                Result.success(response.body<OrderActionResponseDto>().order.toDomain())
            } catch (t: Throwable) {
                Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membatalkan pesanan."))
            }
        }

    override suspend fun refundPayment(
        orderId: String,
        paymentId: String,
        input: PosRefundPaymentInput
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.post("$baseUrl/api/v1/pos/orders/$orderId/payments/$paymentId/refund") {
                contentType(ContentType.Application.Json)
                setBody(
                    RefundPaymentRequestDto(
                        reason = input.reason.trim(),
                        pin = input.pin.trim(),
                        ownerPin = input.ownerPin?.trim()?.ifBlank { null },
                        approvalId = input.approvalId?.trim()?.ifBlank { null },
                        restoreStock = input.restoreStock,
                        refundMethod = input.refundMethod.trim().lowercase(),
                        bankName = input.bankName.trim(),
                        accountLast4 = input.accountLast4.trim(),
                        transferReference = input.transferReference.trim()
                    )
                )
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memproses refund."))
            }
            val dto = response.body<RefundPaymentResponseDto>()
            Result.success(dto.refundStatus.ifBlank { dto.message.ifBlank { "refunded" } })
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memproses refund."))
        }
    }

    override suspend fun reprintOrder(orderId: String, kind: String): Result<Int> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.post("$baseUrl/api/v1/pos/orders/$orderId/reprint") {
                contentType(ContentType.Application.Json)
                setBody(ReprintOrderRequestDto(kind = kind.trim().lowercase()))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal mencetak ulang pesanan."))
            }
            Result.success(response.body<ReprintOrderResponseDto>().jobs)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mencetak ulang pesanan."))
        }
    }

    override suspend fun issueReceiptLink(orderId: String): Result<PosReceiptLink> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.post("$baseUrl/api/v1/pos/orders/$orderId/receipt-link") {
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membuat tautan struk."))
            }
            val dto = response.body<ReceiptLinkResponseDto>()
            Result.success(PosReceiptLink(url = dto.url, shortCode = dto.shortCode, expiresAtIso = dto.expiresAt))
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membuat tautan struk."))
        }
    }

    override suspend fun overrideProductStock(
        productId: String,
        newQuantity: Int,
        reason: String,
        ownerPin: String,
        enableTracking: Boolean,
        approvalId: String?
    ): Result<PosStockOverrideResult> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.post("$baseUrl/api/v1/pos/products/$productId/stock-override") {
                contentType(ContentType.Application.Json)
                setBody(
                    StockOverrideRequestDto(
                        newQuantity = newQuantity,
                        reason = reason.trim(),
                        ownerPin = ownerPin.trim(),
                        approvalId = approvalId?.trim()?.ifBlank { null },
                        enableTracking = enableTracking
                    )
                )
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memperbarui stok produk."))
            }
            val dto = response.body<StockOverrideResponseDto>()
            Result.success(
                PosStockOverrideResult(
                    productId = dto.productId,
                    productName = dto.productName,
                    previousQuantity = dto.previousQuantity,
                    newQuantity = dto.newQuantity,
                    delta = dto.delta
                )
            )
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memperbarui stok produk."))
        }
    }

    override suspend fun startCustomerVerification(
        phone: String,
        name: String
    ): Result<PosCustomerVerificationStart> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.post("$baseUrl/api/v1/pos/customers/verify") {
                contentType(ContentType.Application.Json)
                setBody(StartCustomerVerificationRequestDto(phone = phone.trim(), name = name.trim()))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memulai verifikasi pelanggan."))
            }
            val dto = response.body<StartCustomerVerificationResponseDto>()
            Result.success(
                PosCustomerVerificationStart(
                    verificationId = dto.verificationId.ifBlank { dto.id },
                    expiresAtIso = dto.expiresAt
                )
            )
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memulai verifikasi pelanggan."))
        }
    }

    override suspend fun confirmCustomerVerification(
        verificationId: String,
        code: String
    ): Result<PosCustomerStamps> = withContext(ioDispatcher) {
        try {
            val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
            val response = httpClient.post("$baseUrl/api/v1/pos/customers/verify/$verificationId/confirm") {
                contentType(ContentType.Application.Json)
                setBody(ConfirmCustomerVerificationRequestDto(code = code.trim()))
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Kode OTP pelanggan tidak valid."))
            }
            val dto = response.body<CustomerStampsDto>()
            Result.success(
                PosCustomerStamps(
                    customerId = dto.customer.id,
                    phone = dto.customer.phone,
                    name = dto.customer.name,
                    verified = dto.verified,
                    programActive = dto.programActive,
                    stamps = dto.stamps,
                    stampsRequired = dto.stampsRequired,
                    rewardReady = dto.rewardReady,
                    rewardProductIds = dto.rewardProductIds
                )
            )
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Kode OTP pelanggan tidak valid."))
        }
    }
}
