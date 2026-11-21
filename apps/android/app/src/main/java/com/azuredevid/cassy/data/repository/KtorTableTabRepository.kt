package com.azuredevid.cassy.data.repository

import com.azuredevid.cassy.data.model.CreateOrderItemInputDto
import com.azuredevid.cassy.data.model.PosOrderDto
import com.azuredevid.cassy.data.model.PosOrderItemDto
import com.azuredevid.cassy.data.network.HttpErrorMapper
import com.azuredevid.cassy.domain.model.CreatePosOrderItemInput
import com.azuredevid.cassy.domain.model.PosOrder
import com.azuredevid.cassy.domain.repository.ClosedTab
import com.azuredevid.cassy.domain.repository.PosTableSessionSummary
import com.azuredevid.cassy.domain.repository.ShareLine
import com.azuredevid.cassy.domain.repository.TabSplit
import com.azuredevid.cassy.domain.repository.PosTable
import com.azuredevid.cassy.domain.repository.TabItemVoidInput
import com.azuredevid.cassy.domain.repository.TableTabRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PosTableDto(
    val id: String,
    @SerialName("table_number") val tableNumber: String = "",
    val label: String = "",
    val section: String = "",
    val capacity: Int = 0,
    val status: String = "available",
    @SerialName("unpaid_amount") val unpaidAmount: Long = 0L
)

@Serializable
private data class TableBoardResponseDto(val tables: List<PosTableDto> = emptyList())

@Serializable
private data class OpenTabRequestDto(
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("table_id") val tableId: String,
    @SerialName("table_number") val tableNumber: String
)

@Serializable
private data class TabOrderResponseDto(val order: PosOrderDto)

@Serializable
private data class TabConflictDto(
    val code: String = "",
    @SerialName("existing_order_id") val existingOrderId: String = ""
)

@Serializable
private data class TabRoundDto(val items: List<PosOrderItemDto> = emptyList())

@Serializable
private data class TabDetailResponseDto(val order: PosOrderDto, val rounds: List<TabRoundDto> = emptyList())

@Serializable
private data class SubmitRoundRequestDto(
    @SerialName("idempotency_key") val idempotencyKey: String,
    val items: List<CreateOrderItemInputDto>
)

@Serializable
private data class CloseTabRequestDto(
    val strategy: String,
    @SerialName("split_count") val splitCount: Int? = null,
    val splits: List<SplitShareDto>? = null
)

@Serializable
private data class SplitShareDto(val items: List<SplitItemDto>)

@Serializable
private data class SplitItemDto(@SerialName("order_item_id") val orderItemId: String, val quantity: Int)

@Serializable
private data class VoidTabItemRequestDto(
    val quantity: Int,
    val reason: String,
    val pin: String,
    @SerialName("owner_pin") val ownerPin: String? = null,
    @SerialName("approval_id") val approvalId: String? = null
)

@Serializable
private data class PaymentSplitDto(
    val id: String,
    @SerialName("split_number") val number: Int = 1,
    @SerialName("total_amount") val totalAmount: Long = 0L,
    @SerialName("paid_amount") val paidAmount: Long = 0L,
    val status: String = "unpaid",
    val items: List<PaymentSplitItemDto> = emptyList()
)

@Serializable
private data class PaymentSplitItemDto(
    @SerialName("order_item_id") val orderItemId: String,
    val quantity: Int = 0,
    @SerialName("allocated_amount") val allocatedAmount: Long = 0L
)

@Serializable
private data class CloseTabResponseDto(val order: PosOrderDto, val splits: List<PaymentSplitDto> = emptyList())

@Serializable
private data class TableSessionResponseDto(
    val id: String,
    @SerialName("table_id") val tableId: String = "",
    val status: String = "",
    @SerialName("expires_at") val expiresAt: String? = null
) {
    fun toDomain() = PosTableSessionSummary(id = id, tableId = tableId, status = status, expiresAt = expiresAt)
}

@Serializable
private data class MoveSessionRequestDto(@SerialName("to_table_id") val toTableId: String)

@Serializable
private data class MergeSessionRequestDto(@SerialName("session_id") val sessionId: String)

@Serializable
private data class SplitTableRequestDto(@SerialName("table_id") val tableId: String)

@Serializable
private data class ExtendTimerRequestDto(val minutes: Int)

/** Ktor client for `/api/v1/pos/tables` and `/api/v1/pos/tabs`. */
class KtorTableTabRepository(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TableTabRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun baseUrl() = serverSettings.getBaseUrl().trimEnd('/')

    override suspend fun listTables(): Result<List<PosTable>> = withContext(ioDispatcher) {
        try {
            val response = httpClient.get("${baseUrl()}/api/v1/pos/tables")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memuat daftar meja."))
            }
            Result.success(
                response.body<TableBoardResponseDto>().tables.map {
                    PosTable(it.id, it.tableNumber, it.label, it.section, it.capacity, it.status, it.unpaidAmount)
                }
            )
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat daftar meja."))
        }
    }

    override suspend fun openTab(idempotencyKey: String, tableId: String, tableNumber: String): Result<PosOrder> =
        withContext(ioDispatcher) {
            try {
                val response = httpClient.post("${baseUrl()}/api/v1/pos/tabs") {
                    contentType(ContentType.Application.Json)
                    setBody(OpenTabRequestDto(idempotencyKey, tableId, tableNumber))
                }
                if (response.status == HttpStatusCode.Conflict) {
                    // The table already has an open tab: continue that bill instead of opening a second one.
                    val conflict = runCatching { json.decodeFromString<TabConflictDto>(response.bodyAsText()) }.getOrNull()
                    if (conflict?.code == "TAB_LABEL_IN_USE" && conflict.existingOrderId.isNotBlank()) {
                        return@withContext getTab(conflict.existingOrderId)
                    }
                }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membuka tagihan meja."))
                }
                Result.success(response.body<TabOrderResponseDto>().order.toDomain())
            } catch (t: Throwable) {
                Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membuka tagihan meja."))
            }
        }

    override suspend fun getTab(orderId: String): Result<PosOrder> = withContext(ioDispatcher) {
        try {
            val response = httpClient.get("${baseUrl()}/api/v1/pos/tabs/$orderId")
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal memuat tagihan meja."))
            }
            val detail = response.body<TabDetailResponseDto>()
            // Items live on the rounds; flatten them so the cart can show the whole running bill.
            Result.success(detail.order.toDomain().copy(items = detail.rounds.flatMap { r -> r.items.map { it.toDomain() } }))
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal memuat tagihan meja."))
        }
    }

    override suspend fun voidItem(orderId: String, itemId: String, input: TabItemVoidInput): Result<PosOrder> =
        withContext(ioDispatcher) {
            try {
                val response = httpClient.post("${baseUrl()}/api/v1/pos/tabs/$orderId/items/$itemId/void") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        VoidTabItemRequestDto(
                            quantity = input.quantity,
                            reason = input.reason,
                            pin = input.pin,
                            ownerPin = input.ownerPin?.ifBlank { null },
                            approvalId = input.approvalId
                        )
                    )
                }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal membatalkan item."))
                }
                Result.success(response.body<TabOrderResponseDto>().order.toDomain())
            } catch (t: Throwable) {
                Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membatalkan item."))
            }
        }

    override suspend fun submitRound(
        orderId: String,
        idempotencyKey: String,
        items: List<CreatePosOrderItemInput>
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val response = httpClient.post("${baseUrl()}/api/v1/pos/tabs/$orderId/rounds") {
                contentType(ContentType.Application.Json)
                setBody(
                    SubmitRoundRequestDto(
                        idempotencyKey = idempotencyKey,
                        items = items.map { CreateOrderItemInputDto(it.productId, it.quantity, it.notes.takeIf { n -> n.isNotBlank() }) }
                    )
                )
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal mengirim ronde pesanan."))
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal mengirim ronde pesanan."))
        }
    }

    override suspend fun closeTab(orderId: String, splitCount: Int): Result<ClosedTab> =
        close(orderId, if (splitCount > 1) CloseTabRequestDto("equal", splitCount) else CloseTabRequestDto("full"))

    override suspend fun closeTabByItem(orderId: String, shares: List<Map<String, Int>>): Result<ClosedTab> =
        close(orderId, CloseTabRequestDto("by_item", splits = shares.map { share -> SplitShareDto(share.map { SplitItemDto(it.key, it.value) }) }))

    override suspend fun reopenTab(orderId: String): Result<PosOrder> = withContext(ioDispatcher) {
        try {
            val response = httpClient.post("${baseUrl()}/api/v1/pos/tabs/$orderId/reopen") {
                contentType(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    HttpErrorMapper.toDomainException(response, "Gagal membuka kembali tagihan meja.")
                )
            }
            Result.success(response.body<TabOrderResponseDto>().order.toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal membuka kembali tagihan meja."))
        }
    }

    override suspend fun closeTableSession(sessionId: String): Result<PosTableSessionSummary> =
        postTableSessionAction(sessionId, "close", null, "Gagal menutup sesi meja.")

    override suspend fun moveTableSession(sessionId: String, toTableId: String): Result<PosTableSessionSummary> =
        postTableSessionAction(sessionId, "move", MoveSessionRequestDto(toTableId), "Gagal memindahkan meja.")

    override suspend fun mergeTableSession(sessionId: String, targetSessionId: String): Result<PosTableSessionSummary> =
        postTableSessionAction(sessionId, "merge", MergeSessionRequestDto(targetSessionId), "Gagal menggabungkan meja.")

    override suspend fun splitTableSession(sessionId: String, tableId: String): Result<PosTableSessionSummary> =
        postTableSessionAction(sessionId, "split", SplitTableRequestDto(tableId), "Gagal memisahkan meja.")

    override suspend fun extendTableSession(sessionId: String, minutes: Int): Result<PosTableSessionSummary> =
        postTableSessionAction(sessionId, "extend", ExtendTimerRequestDto(minutes), "Gagal memperpanjang waktu meja.")

    private suspend fun postTableSessionAction(
        sessionId: String,
        action: String,
        body: Any?,
        fallbackError: String
    ): Result<PosTableSessionSummary> = withContext(ioDispatcher) {
        try {
            val response = httpClient.post("${baseUrl()}/api/v1/pos/table-sessions/$sessionId/$action") {
                contentType(ContentType.Application.Json)
                if (body != null) {
                    setBody(body)
                }
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, fallbackError))
            }
            Result.success(response.body<TableSessionResponseDto>().toDomain())
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, fallbackError))
        }
    }

    private suspend fun close(orderId: String, request: CloseTabRequestDto): Result<ClosedTab> = withContext(ioDispatcher) {
        try {
            val response = httpClient.post("${baseUrl()}/api/v1/pos/tabs/$orderId/close") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(HttpErrorMapper.toDomainException(response, "Gagal menutup tagihan meja."))
            }
            val body = response.body<CloseTabResponseDto>()
            if (body.splits.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Tagihan meja tidak punya split pembayaran."))
            }
            val splits = body.splits.sortedBy { it.number }
                .map { s ->
                    TabSplit(
                        s.id, s.number, s.totalAmount, s.paidAmount, s.status,
                        s.items.map { ShareLine(it.orderItemId, it.quantity, it.allocatedAmount) }
                    )
                }
            Result.success(ClosedTab(body.order.toDomain(), splits))
        } catch (t: Throwable) {
            Result.failure(HttpErrorMapper.fromThrowable(t, "Gagal menutup tagihan meja."))
        }
    }
}
