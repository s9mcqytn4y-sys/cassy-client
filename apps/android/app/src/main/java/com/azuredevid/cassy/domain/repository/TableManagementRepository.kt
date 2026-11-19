package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.domain.model.TableDraft
import com.azuredevid.cassy.domain.model.TableEntry

/**
 * Table and table-QR management against `/api/v1/pos/tables*` (Release One PR 5).
 *
 * Every call returns [Result]; failures carry a `DomainException` from `HttpErrorMapper` so callers can
 * branch on the backend code (for example `TABLE_SESSION_OPEN` on archive, HTTP 402
 * `PLAN_LIMIT_REACHED` on create) without parsing HTTP themselves.
 */
interface TableManagementRepository {
    /** Non-archived tables with server-derived occupancy, in the order the backend returns them. */
    suspend fun listTables(): Result<List<TableEntry>>

    /** Creates a table; the response carries the first QR token for printing. */
    suspend fun createTable(draft: TableDraft): Result<TableEntry>

    /** Updates number, label, section, capacity, or active flag. */
    suspend fun updateTable(id: String, draft: TableDraft): Result<TableEntry>

    /** Retires a table; its QR stops resolving. Fails with `TABLE_SESSION_OPEN` while guests are seated. */
    suspend fun archiveTable(id: String): Result<Unit>

    /**
     * Replaces the table's QR token; the old sticker stops working immediately. The backend does not
     * check for seated guests, so callers must run the fail-closed guard first.
     */
    suspend fun rotateQr(id: String): Result<TableEntry>
}
