package com.azuredevid.cassy.data.model

import com.azuredevid.cassy.domain.model.TableDraft
import com.azuredevid.cassy.domain.model.TableEntry
import com.azuredevid.cassy.domain.model.TableStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire shape of `Table` and `TableBoardEntry` (OpenAPI). Board-only fields default so the same DTO
 * decodes create, update, and rotate responses, which return a plain `Table`.
 */
@Serializable
data class TableDto(
    @SerialName("id") val id: String,
    @SerialName("table_number") val tableNumber: String,
    @SerialName("label") val label: String = "",
    @SerialName("section") val section: String = "",
    @SerialName("capacity") val capacity: Int = 4,
    @SerialName("qr_token") val qrToken: String? = null,
    @SerialName("qr_rotated_at") val qrRotatedAt: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("status") val status: String? = null,
    @SerialName("session") val session: JsonObject? = null,
    @SerialName("unpaid_orders") val unpaidOrders: Int = 0,
    @SerialName("merged_into_table_id") val mergedIntoTableId: String? = null
) {
    /**
     * A plain `Table` (no `status`) is a freshly created or edited row, so it maps to available with no
     * session. Board rows always carry `status`; unknown values fail closed via [TableStatus.fromWire].
     */
    fun toDomain(): TableEntry = TableEntry(
        id = id,
        tableNumber = tableNumber,
        label = label,
        section = section,
        capacity = capacity,
        qrToken = qrToken?.takeIf { it.isNotBlank() },
        qrRotatedAt = qrRotatedAt,
        isActive = isActive,
        status = if (status == null) TableStatus.AVAILABLE else TableStatus.fromWire(status),
        hasOpenSession = session != null,
        mergedIntoTableId = mergedIntoTableId?.takeIf { it.isNotBlank() },
        unpaidOrders = unpaidOrders
    )
}

@Serializable
data class TableBoardResponseDto(
    @SerialName("tables") val tables: List<TableDto> = emptyList()
)

/** Request body for create and update (`TableInput`). */
@Serializable
data class TableInputDto(
    @SerialName("table_number") val tableNumber: String,
    @SerialName("label") val label: String,
    @SerialName("section") val section: String,
    @SerialName("capacity") val capacity: Int,
    @SerialName("is_active") val isActive: Boolean
) {
    companion object {
        fun from(draft: TableDraft) = TableInputDto(
            tableNumber = draft.tableNumber.trim(),
            label = draft.label.trim(),
            section = draft.section.trim(),
            capacity = draft.capacity,
            isActive = draft.isActive
        )
    }
}
