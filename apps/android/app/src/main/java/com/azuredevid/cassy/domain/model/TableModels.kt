package com.azuredevid.cassy.domain.model

/**
 * Occupancy of a table as derived by the backend from its open session (decision T1). The client
 * never derives it from orders.
 */
enum class TableStatus(val wireValue: String) {
    AVAILABLE("available"),
    OCCUPIED("occupied"),
    SETTLED("settled");

    companion object {
        /** Unknown values map to [OCCUPIED] so every guard that reads the status fails closed. */
        fun fromWire(value: String?): TableStatus = entries.firstOrNull { it.wireValue == value } ?: OCCUPIED
    }
}

/**
 * One non-archived table from `GET /api/v1/pos/tables`.
 *
 * [qrToken] and [qrUrl] are null when the staff member lacks `table.qr.manage`; QR actions must then stay
 * hidden. [qrUrl] is the guest link the backend builds from its public web origin; the client prints it
 * as is and never derives it from the API host, which differs from the web host in production.
 * [hasOpenSession] is true whenever the backend returned a session object, regardless of [status].
 */
data class TableEntry(
    val id: String,
    val tableNumber: String,
    val label: String,
    val section: String,
    val capacity: Int,
    val qrToken: String?,
    val qrUrl: String?,
    val qrRotatedAt: String,
    val isActive: Boolean,
    val status: TableStatus,
    val hasOpenSession: Boolean,
    val mergedIntoTableId: String?,
    val unpaidOrders: Int
) {
    /** Display name for cards and printouts: the label when set, otherwise the table number. */
    val displayName: String
        get() = label.ifBlank { tableNumber }

    /**
     * Why rotating this table's QR must be refused, or null when rotation is safe. Rotation kills the
     * printed sticker at once, so a guest who is seated, merged into another table, or still owes money
     * would lose their ordering link mid-visit. Any doubt (unknown status) counts as occupied.
     */
    fun qrRotationBlock(): QrRotationBlock? = when {
        hasOpenSession || status != TableStatus.AVAILABLE -> QrRotationBlock.ACTIVE_SESSION
        mergedIntoTableId != null -> QrRotationBlock.MERGED
        unpaidOrders > 0 -> QrRotationBlock.UNPAID_ORDERS
        else -> null
    }
}

/** Reasons the client refuses to rotate a table QR (fail-closed guard, PR 5). */
enum class QrRotationBlock {
    ACTIVE_SESSION,
    MERGED,
    UNPAID_ORDERS,
    /** The fresh board could not be loaded or no longer lists the table, so safety cannot be proven. */
    STATE_UNKNOWN
}

/**
 * Owner input for creating or editing a table. Fields map 1:1 to the backend `TableInput`
 * (`table_number` 1-50 chars, `label` <= 100, `section` <= 50, `capacity` 1-100); the server validates.
 */
data class TableDraft(
    val tableNumber: String,
    val label: String = "",
    val section: String = "",
    val capacity: Int = 4,
    val isActive: Boolean = true
)
