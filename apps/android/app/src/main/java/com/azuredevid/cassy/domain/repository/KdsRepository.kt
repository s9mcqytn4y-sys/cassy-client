package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.domain.model.KdsStation
import com.azuredevid.cassy.domain.model.KdsTicket
import com.azuredevid.cassy.domain.model.KdsTicketStatus

/**
 * KDS tickets over `/api/v1/kds/tickets*`. Failures carry a `DomainException`; a transition refused
 * because another screen moved the ticket first is `DomainError.Conflict` with code
 * [CODE_STALE_STATUS], and callers reload instead of retrying.
 */
interface KdsRepository {
    /** Open tickets (queued, preparing, ready) of [station], oldest first. */
    suspend fun listTickets(station: KdsStation): Result<List<KdsTicket>>

    /**
     * Moves [ticketId] to [to] only if it is still [expected] on the server (optimistic concurrency).
     * Returns the updated ticket.
     */
    suspend fun transition(ticketId: String, to: KdsTicketStatus, expected: KdsTicketStatus): Result<KdsTicket>

    companion object {
        const val CODE_STALE_STATUS = "KDS_STALE_STATUS"
    }
}
