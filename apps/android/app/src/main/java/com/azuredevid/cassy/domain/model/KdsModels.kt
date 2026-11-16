package com.azuredevid.cassy.domain.model

/**
 * KDS station. [readPermission] gates the board and [updatePermission] the bump buttons; a cashier gains
 * the bar permissions when the store sets `cashier_handles_bar`, which the backend already folds into
 * the session's effective permissions.
 */
enum class KdsStation(val wireValue: String, val readPermission: String, val updatePermission: String) {
    BAR("bar", "kds.ticket.read.barista", "kds.ticket.update.barista"),
    KITCHEN("kitchen", "kds.ticket.read.kitchen", "kds.ticket.update.kitchen");

    companion object {
        fun fromWire(value: String?): KdsStation? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Ticket status as the backend stores it. Allowed moves mirror `POST /kds/tickets/{id}/transition`:
 * forward one step, plus a recall from [READY] back to [PREPARING]. [SERVED] and [CANCELLED] are final
 * and leave the board.
 */
enum class KdsTicketStatus(val wireValue: String) {
    QUEUED("queued"),
    PREPARING("preparing"),
    READY("ready"),
    SERVED("served"),
    CANCELLED("cancelled");

    /** The single forward step a bump performs, or null for final states. */
    val next: KdsTicketStatus?
        get() = when (this) {
            QUEUED -> PREPARING
            PREPARING -> READY
            READY -> SERVED
            SERVED, CANCELLED -> null
        }

    /** True while the ticket belongs on the open board. */
    val isOpen: Boolean
        get() = this == QUEUED || this == PREPARING || this == READY

    fun canMoveTo(target: KdsTicketStatus): Boolean = target == next || (this == READY && target == PREPARING)

    companion object {
        /** Unknown values are treated as final so an unexpected state never offers a bump. */
        fun fromWire(value: String?): KdsTicketStatus = entries.firstOrNull { it.wireValue == value } ?: CANCELLED
    }
}

/** One line on a ticket. [options] is the modifier summary already flattened for display. */
data class KdsTicketLine(
    val name: String,
    val variant: String,
    val quantity: Int,
    val options: List<String>,
    val notes: String
)

/** An open kitchen or bar ticket from `GET /api/v1/kds/tickets`. Times are epoch milliseconds (UTC). */
data class KdsTicket(
    val id: String,
    val orderId: String,
    val orderNumber: String,
    val queueNumber: String,
    val tableNumber: String,
    val roundNumber: Int?,
    val station: KdsStation,
    val status: KdsTicketStatus,
    val latePayment: Boolean,
    val createdAtMillis: Long,
    val lines: List<KdsTicketLine>
) {
    /** Whole minutes since the ticket was created; never negative when the tablet clock lags. */
    fun ageMinutes(nowMillis: Long): Long = ((nowMillis - createdAtMillis).coerceAtLeast(0L)) / 60_000L
}

/**
 * Age band of a ticket for colour plus text coding. Thresholds are fixed for Release One:
 * under [WARNING_MINUTES] is on time, under [LATE_MINUTES] needs attention, beyond is late.
 */
enum class KdsAgeLevel {
    ON_TIME,
    WARNING,
    LATE;

    companion object {
        const val WARNING_MINUTES = 5L
        const val LATE_MINUTES = 10L

        fun of(ageMinutes: Long): KdsAgeLevel = when {
            ageMinutes >= LATE_MINUTES -> LATE
            ageMinutes >= WARNING_MINUTES -> WARNING
            else -> ON_TIME
        }
    }
}
