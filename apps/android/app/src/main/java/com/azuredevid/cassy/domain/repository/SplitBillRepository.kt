package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.domain.model.OpenSplitBillSummary
import com.azuredevid.cassy.domain.model.SplitBill
import com.azuredevid.cassy.domain.model.SplitPlan

/**
 * Cashier bill splitting for pay-first orders (Release One PR 9). Shares are priced by the server and
 * paid through [PosOrderRepository.initiatePayment] with `splitId`; the kitchen receives the order only
 * when the last share is paid. Failures carry a `DomainException` from `HttpErrorMapper`; a bill that
 * can no longer be divided is `DomainError.Conflict` with code [CODE_SPLIT_NOT_ALLOWED].
 */
interface SplitBillRepository {
    /** Divides a pending, unpaid pay-first order (`POST /api/v1/pos/orders/{id}/splits`). */
    suspend fun divide(orderId: String, plan: SplitPlan): Result<SplitBill>

    /** Current order and shares (`GET /api/v1/pos/orders/{id}`). */
    suspend fun getBill(orderId: String): Result<SplitBill>

    /**
     * Orders with at least one share paid and money still owed
     * (`GET /api/v1/pos/orders?payment_status=partially_paid`), including guest-split QR orders.
     */
    suspend fun listOpenBills(query: String = ""): Result<List<OpenSplitBillSummary>>

    companion object {
        const val CODE_SPLIT_NOT_ALLOWED = "SPLIT_NOT_ALLOWED"
    }
}
