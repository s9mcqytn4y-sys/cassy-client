package com.azuredevid.cassy.domain.repository

/** A remote owner approval (ADR-007) as the requesting cashier sees it. */
data class OwnerApproval(
    val id: String,
    /** pending, approved, rejected, consumed or expired. */
    val status: String,
    val decisionNote: String = ""
)

/**
 * Asks the owner's phone to approve an action above the cashier's own limit. The owner has 2 minutes
 * to decide and the cashier 2 minutes more to use an approval; the server binds it to the action,
 * reference and params, so it cannot be spent on anything else.
 */
interface ApprovalRepository {
    suspend fun requestVoidItem(itemId: String, quantity: Int, reason: String): Result<OwnerApproval>

    suspend fun get(id: String): Result<OwnerApproval>
}
