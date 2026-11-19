package com.azuredevid.cassy.data.network

import com.azuredevid.cassy.core.coroutines.runCatchingCancellable
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.data.repository.ServerSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import com.azuredevid.cassy.domain.repository.RealtimeEventSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import kotlin.random.Random

enum class RealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

@Serializable
data class WsTicketResponseDto(
    val ticket: String,
    @SerialName("expires_at") val expiresAt: String = "",
    val channels: List<String> = emptyList()
)

/**
 * One `/ws` frame: `{seq, type, store_id, payload}` (backend `realtime.EventMessage`). [seq] is the
 * per-store sequence; 0 means unsequenced (for example `resync_required`). [payload] is the event object.
 */
@Serializable
data class RealtimeEventEnvelope(
    val seq: Long = 0L,
    val type: String = "",
    @SerialName("store_id") val storeId: String = "",
    val payload: JsonObject? = null
) {
    /** Payload as raw JSON text, for callers that only search it. */
    val payloadJson: String
        get() = payload?.toString().orEmpty()

    /** String field of [payload], or null when absent or not a string. */
    fun payloadString(key: String): String? =
        (payload?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val TYPE_RESYNC_REQUIRED = "resync_required"
    }
}

/**
 * Remembers the highest event sequence seen and drops anything at or below it, because `/ws` delivers
 * at least once and replays after `last_seq` on reconnect. Unsequenced events (seq 0) always pass.
 * Not thread-safe; the connection loop is its only caller.
 */
class RealtimeSequenceTracker {
    /** Highest accepted sequence, or -1 before the first sequenced event. */
    var lastSeq: Long = -1L
        private set

    /** True when [seq] is new and must be delivered; advances [lastSeq] when it is. */
    fun accept(seq: Long): Boolean {
        if (seq <= 0L) return true
        if (seq <= lastSeq) return false
        lastSeq = seq
        return true
    }
}

/**
 * Reconnect delay with exponential backoff and full jitter: a uniform random delay in
 * `[0, min(capMillis, baseMillis * 2^attempt)]`. Full jitter spreads tablets that dropped together
 * (Wi-Fi blip, server restart) so they do not reconnect in lockstep.
 */
class ReconnectBackoff(
    private val baseMillis: Long = 500L,
    private val capMillis: Long = 30_000L,
    private val random: Random = Random.Default
) {
    /** Upper bound of the delay for [attempt] (0-based), before jitter. */
    fun ceilingMillis(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 20)
        return (baseMillis shl shift).coerceAtMost(capMillis)
    }

    fun delayMillis(attempt: Int): Long = random.nextLong(0L, ceilingMillis(attempt) + 1)
}

/**
 * Staff WebSocket client: single-use ticket, then `/ws?ticket=...&last_seq=...` so missed events are
 * replayed after a reconnect. Repeats are dropped by [RealtimeSequenceTracker]; reconnects wait
 * [ReconnectBackoff] (exponential with full jitter). The loop runs while the scope given to [start] lives.
 */
class CassyRealtimeClient(
    private val httpClient: HttpClient,
    private val serverSettings: ServerSettingsRepository,
    private val sessionManager: SessionManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val backoff: ReconnectBackoff = ReconnectBackoff()
) : RealtimeEventSource {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<RealtimeEventEnvelope>(extraBufferCapacity = 64)
    override val events: SharedFlow<RealtimeEventEnvelope> = _events.asSharedFlow()

    private val sequence = RealtimeSequenceTracker()
    private var connectionJob: Job? = null

    suspend fun requestTicket(): Result<WsTicketResponseDto> {
        val token = sessionManager.getAccessToken()
            ?: return Result.failure(IllegalStateException("Sesi staf tidak tersedia untuk tiket realtime."))
        val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
        return runCatchingCancellable {
            val response = httpClient.post("$baseUrl/api/v1/pos/ws-ticket") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (!response.status.isSuccess()) {
                throw HttpErrorMapper.toDomainException(response, "Gagal meminta tiket WebSocket.")
            }
            response.body<WsTicketResponseDto>()
        }
    }

    fun buildWebSocketUrl(ticket: String): String {
        val baseUrl = serverSettings.getBaseUrl().trimEnd('/')
        val wsBase = when {
            baseUrl.startsWith("https://", ignoreCase = true) -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://", ignoreCase = true) -> "ws://" + baseUrl.removePrefix("http://")
            else -> baseUrl
        }
        val encodedTicket = URLEncoder.encode(ticket, "UTF-8")
        val lastSeq = sequence.lastSeq
        return if (lastSeq >= 0) {
            "$wsBase/ws?ticket=$encodedTicket&last_seq=$lastSeq"
        } else {
            "$wsBase/ws?ticket=$encodedTicket"
        }
    }

    override fun start(scope: CoroutineScope) {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch(ioDispatcher) {
            var attempt = 0
            while (isActive) {
                if (sessionManager.getAccessToken().isNullOrBlank()) {
                    _connectionState.value = RealtimeConnectionState.DISCONNECTED
                    delay(2_000L)
                    continue
                }

                _connectionState.value = if (attempt == 0) {
                    RealtimeConnectionState.CONNECTING
                } else {
                    RealtimeConnectionState.RECONNECTING
                }

                val ticketResult = requestTicket()
                val ticket = ticketResult.getOrNull()?.ticket
                if (ticket.isNullOrBlank()) {
                    _connectionState.value = RealtimeConnectionState.RECONNECTING
                    delay(backoff.delayMillis(attempt))
                    attempt++
                    continue
                }

                try {
                    val wsUrl = buildWebSocketUrl(ticket)
                    httpClient.webSocket(urlString = wsUrl) {
                        _connectionState.value = RealtimeConnectionState.CONNECTED
                        attempt = 0
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                val envelope = runCatching {
                                    json.decodeFromString(RealtimeEventEnvelope.serializer(), text)
                                }.getOrNull()
                                if (envelope != null && sequence.accept(envelope.seq)) {
                                    _events.emit(envelope)
                                }
                            }
                        }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Throwable) {
                    // Dropped or refused: fall through to the jittered reconnect below.
                }

                _connectionState.value = RealtimeConnectionState.RECONNECTING
                delay(backoff.delayMillis(attempt))
                attempt++
            }
        }
    }

    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

}
