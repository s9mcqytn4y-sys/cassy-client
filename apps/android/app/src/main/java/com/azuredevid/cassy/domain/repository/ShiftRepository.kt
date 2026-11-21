package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.domain.model.CashDrawer
import com.azuredevid.cassy.domain.model.CashMovementResult
import com.azuredevid.cassy.domain.model.CashMovementType
import com.azuredevid.cassy.domain.model.Shift
import com.azuredevid.cassy.domain.model.ShiftCloseResult
import com.azuredevid.cassy.domain.model.ShiftOpenResult
import com.azuredevid.cassy.domain.model.XReport
import com.azuredevid.cassy.domain.model.ZReport

interface ShiftRepository {
    suspend fun getDrawers(): Result<List<CashDrawer>>
    suspend fun getCurrentShift(drawerId: String? = null): Result<Shift?>
    suspend fun openShift(
        openingFloat: Long,
        drawerId: String? = null,
        notes: String = ""
    ): Result<ShiftOpenResult>

    suspend fun recordCashMovement(
        movementType: CashMovementType,
        amount: Long,
        reason: String,
        shiftId: String? = null,
        drawerId: String? = null
    ): Result<CashMovementResult>

    suspend fun closeShift(
        shiftId: String,
        countedCash: Long,
        ownerPin: String? = null,
        varianceReason: String? = null,
        notes: String? = null
    ): Result<ShiftCloseResult>

    suspend fun getXReport(shiftId: String): Result<XReport>
    suspend fun getZReport(shiftId: String): Result<ZReport>

    /** Force-closes an open shift with manager/owner permission (`POST /api/v1/pos/shifts/{id}/force-close`). */
    suspend fun forceCloseShift(
        shiftId: String,
        reason: String,
        countedCash: Long? = null,
        notes: String? = null
    ): Result<ZReport> = Result.failure(UnsupportedOperationException("forceCloseShift not configured"))

    /** Updates cash drawer display name or active status (`PATCH /api/v1/pos/drawers/{id}`). */
    suspend fun updateDrawer(
        drawerId: String,
        name: String,
        isActive: Boolean? = null
    ): Result<CashDrawer> = Result.failure(UnsupportedOperationException("updateDrawer not configured"))
}
