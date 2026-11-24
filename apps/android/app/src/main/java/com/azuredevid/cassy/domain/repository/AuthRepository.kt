package com.azuredevid.cassy.domain.repository

import com.azuredevid.cassy.domain.model.OfflinePinVerifier
import com.azuredevid.cassy.domain.model.StaffIdentity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MobileBootstrapData(
    @SerialName("store_id") val storeId: String = "",
    @SerialName("store_slug") val storeSlug: String = "",
    @SerialName("store_name") val storeName: String = "",
    @SerialName("staff_session") val staffSession: MobileStaffSessionDto? = null,
    @SerialName("app_brand") val appBrand: MobileAppBrandDto? = null,
    @SerialName("pos_layout") val posLayout: MobilePosLayoutDto? = null
) {
    /** Effective permissions of the signed-in staff; the login response carries none (OpenAPI MobileBootstrap). */
    val permissions: List<String> get() = staffSession?.permissions.orEmpty()
}

@Serializable
data class MobileStaffSessionDto(
    @SerialName("permissions") val permissions: List<String> = emptyList()
)

@Serializable
data class MobileAppBrandDto(
    @SerialName("display_name") val displayName: String = "Cassy",
    @SerialName("monogram_text") val monogramText: String = "CS"
)

@Serializable
data class MobilePosLayoutDto(
    @SerialName("tablet_orientation") val tabletOrientation: String = "landscape_primary",
    @SerialName("auto_lock_seconds") val autoLockSeconds: Int = 300,
    @SerialName("kds_auto_refresh_seconds") val kdsAutoRefreshSeconds: Int = 5,
    @SerialName("kds_sound_alert_enabled") val kdsSoundAlertEnabled: Boolean = true
)

data class AuthLoginResult(
    val staff: StaffIdentity,
    val pinChangeRequired: Boolean,
    val offlinePinVerifier: OfflinePinVerifier?
)

data class PinSetupResult(
    val storeSlug: String,
    val email: String,
    val message: String
)

interface AuthRepository {
    suspend fun login(
        storeSlug: String,
        email: String,
        badge: String = "",
        pin: String,
        deviceCredential: String? = null
    ): Result<AuthLoginResult>

    suspend fun fetchMobileBootstrap(): Result<MobileBootstrapData>

    suspend fun fetchMe(): Result<StaffIdentity>

    suspend fun logout(): Result<Unit>

    /** Changes the currently signed-in staff member's PIN (`POST /api/v1/auth/staff/pin/change`). */
    suspend fun changePin(currentPin: String, newPin: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("changePin not configured"))

    /** Requests a PIN reset link via email (`POST /api/v1/auth/staff/pin/forgot`). */
    suspend fun forgotPinByEmail(storeSlug: String, email: String): Result<String> =
        Result.failure(UnsupportedOperationException("forgotPinByEmail not configured"))

    /** Requests a PIN reset OTP via SMS (`POST /api/v1/auth/staff/pin/forgot-sms`). */
    suspend fun forgotPinBySms(storeSlug: String, phone: String): Result<String> =
        Result.failure(UnsupportedOperationException("forgotPinBySms not configured"))

    /** Completes SMS OTP PIN reset (`POST /api/v1/auth/staff/pin/reset-sms`). */
    suspend fun resetPinWithSms(storeSlug: String, phone: String, code: String, newPin: String): Result<PinSetupResult> =
        Result.failure(UnsupportedOperationException("resetPinWithSms not configured"))

    /** Sets initial/reset PIN using an activation token (`POST /api/v1/auth/staff/pin/setup`). */
    suspend fun setupPinWithToken(token: String, newPin: String): Result<PinSetupResult> =
        Result.failure(UnsupportedOperationException("setupPinWithToken not configured"))
}
