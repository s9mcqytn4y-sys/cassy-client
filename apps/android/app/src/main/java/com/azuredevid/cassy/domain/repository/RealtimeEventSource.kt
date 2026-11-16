package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.data.network.RealtimeConnectionState
import com.azuredevid.cassy.data.network.RealtimeEventEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Store event stream from `/ws` (ticket from `POST /api/v1/pos/ws-ticket`). Events arrive at least once
 * in per-store sequence order; implementations drop repeats so collectors see each sequence once.
 * A `resync_required` event means more than 500 events were missed: collectors reload over REST.
 */
interface RealtimeEventSource {
    val connectionState: StateFlow<RealtimeConnectionState>
    val events: SharedFlow<RealtimeEventEnvelope>

    /** Starts the connection loop in [scope] if it is not running; it stops when [scope] is cancelled. */
    fun start(scope: CoroutineScope)
}
