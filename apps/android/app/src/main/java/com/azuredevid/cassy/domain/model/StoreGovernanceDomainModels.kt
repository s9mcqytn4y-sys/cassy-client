package com.azuredevid.cassy.domain.model

enum class MidtransEnvironment(val raw: String) {
    SANDBOX("sandbox"),
    PRODUCTION("production");

    companion object {
        fun fromRaw(raw: String): MidtransEnvironment =
            entries.find { it.raw.equals(raw, ignoreCase = true) } ?: SANDBOX
    }
}

enum class QRServiceMode(val raw: String) {
    PICKUP("pickup"),
    TABLE("table");

    companion object {
        fun fromRaw(raw: String): QRServiceMode =
            entries.find { it.raw.equals(raw, ignoreCase = true) } ?: TABLE
    }
}

data class PaymentCredentials(
    val environment: MidtransEnvironment,
    val merchantId: String,
    val clientKey: String,
    val serverKeyMasked: String,
    val isEnabled: Boolean,
    val fallbackStaticQrisEnabled: Boolean,
    val validationStatus: String = "",
    val lastVerifiedAt: String? = null
)

data class DayOperatingHours(
    val open: String = "08:00",
    val close: String = "22:00",
    val closed: Boolean = false
)

data class StoreCMSConfig(
    val defaultLocale: String,
    val supportedLocales: List<String>,
    val heroTitleId: String,
    val heroTitleEn: String,
    val heroSubtitleId: String,
    val heroSubtitleEn: String,
    val heroBannerUrl: String,
    val primaryHex: String,
    val accentHex: String,
    val highContrastMode: Boolean,
    val hidePoweredByBadge: Boolean,
    val customDomain: String,
    val publicContactEmail: String = "",
    val operatingHours: Map<String, DayOperatingHours> = emptyMap()
)

data class StoreSettings(
    val qrServiceMode: QRServiceMode,
    val qrOrderingPaused: Boolean,
    val tableTimeLimitMinutes: Int,
    val tableTimerWarningMinutes: Int,
    val staticQrisFallbackMode: String,
    val cashierHandlesBar: Boolean,
    val enabledPaymentMethods: List<String> = emptyList()
)

data class PaymentCredentialInput(
    val environment: MidtransEnvironment,
    val merchantId: String,
    val clientKey: String,
    val serverKey: String,
    val isEnabled: Boolean = true,
    val fallbackStaticQrisEnabled: Boolean = true,
    val verifyLive: Boolean = false
)

data class StoreCMSConfigInput(
    val defaultLocale: String = "id-ID",
    val supportedLocales: List<String> = listOf("id-ID", "en-US"),
    val heroTitleId: String,
    val heroTitleEn: String,
    val heroSubtitleId: String,
    val heroSubtitleEn: String,
    val heroBannerUrl: String,
    val primaryHex: String,
    val accentHex: String,
    val highContrastMode: Boolean = false,
    val hidePoweredByBadge: Boolean = false,
    val customDomain: String = "",
    val publicContactEmail: String = "",
    val operatingHours: Map<String, DayOperatingHours> = emptyMap()
)

data class StoreSettingsPatch(
    val qrServiceMode: QRServiceMode? = null,
    val qrOrderingPaused: Boolean? = null,
    val tableTimeLimitMinutes: Int? = null,
    val tableTimerWarningMinutes: Int? = null,
    val staticQrisFallbackMode: String? = null,
    val cashierHandlesBar: Boolean? = null,
    val enabledPaymentMethods: List<String>? = null
)

