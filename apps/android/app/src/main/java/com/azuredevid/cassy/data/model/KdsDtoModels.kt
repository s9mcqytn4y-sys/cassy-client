package com.azuredevid.cassy.data.model

import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketLine
import com.azuredevid.cassy.domain.model.KdsTicketStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/** Wire shape of `KDSTicket` (OpenAPI). */
@Serializable
data class KdsTicketDto(
    @SerialName("id") val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("order_number") val orderNumber: String = "",
    @SerialName("queue_number") val queueNumber: String = "",
    @SerialName("table_number") val tableNumber: String = "",
    @SerialName("round_number") val roundNumber: Int? = null,
    @SerialName("station") val station: String,
    @SerialName("status") val status: String,
    @SerialName("late_payment") val latePayment: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("lines") val lines: List<KdsLineDto> = emptyList()
) {
    /** Null for a station this app does not know, so such tickets are skipped instead of misrouted. */
    fun toDomain(): KdsTicket? {
        val parsedStation = KdsStation.fromWire(station) ?: return null
        return KdsTicket(
            id = id,
            orderId = orderId,
            orderNumber = orderNumber,
            queueNumber = queueNumber,
            tableNumber = tableNumber,
            roundNumber = roundNumber,
            station = parsedStation,
            status = KdsTicketStatus.fromWire(status),
            latePayment = latePayment,
            createdAtMillis = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L),
            lines = lines.map { it.toDomain() }
        )
    }
}

@Serializable
data class KdsLineDto(
    @SerialName("name") val name: String,
    @SerialName("variant") val variant: String = "",
    @SerialName("quantity") val quantity: Int = 1,
    @SerialName("options") val options: String = "",
    @SerialName("notes") val notes: String = ""
) {
    fun toDomain() = KdsTicketLine(
        name = name,
        variant = variant,
        quantity = quantity,
        options = optionNames(options),
        notes = notes
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * `options` is the JSON snapshot of the selected modifiers (backend `selectedOptionSnapshotJSON`:
         * `option_name`, `quantity`, nested `sub_addons`). Sub-add-ons are listed after their parent with
         * an indent marker. Anything unparseable yields no options rather than a crash.
         */
        fun optionNames(snapshot: String): List<String> {
            if (snapshot.isBlank()) return emptyList()
            val root = runCatching { json.parseToJsonElement(snapshot) }.getOrNull() as? JsonArray ?: return emptyList()
            val out = mutableListOf<String>()
            fun walk(options: JsonArray, depth: Int) {
                options.forEach { element ->
                    val option = element as? JsonObject ?: return@forEach
                    val name = (option["option_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (!name.isNullOrBlank()) {
                        val qty = (option["quantity"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
                        val label = if (qty > 1) "${qty}x $name" else name
                        out += "  ".repeat(depth) + label
                    }
                    (option["sub_addons"] as? JsonArray)?.let { walk(it, depth + 1) }
                }
            }
            walk(root, 0)
            return out
        }
    }
}

@Serializable
data class KdsTicketsResponseDto(
    @SerialName("tickets") val tickets: List<KdsTicketDto> = emptyList()
)

@Serializable
data class KdsTransitionRequestDto(
    @SerialName("to") val to: String,
    @SerialName("expected_status") val expectedStatus: String
)
