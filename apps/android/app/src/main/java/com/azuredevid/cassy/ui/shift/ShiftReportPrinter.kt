package com.azuredevid.cassy.ui.shift

import com.azuredevid.cassy.R
import com.azuredevid.cassy.core.hardware.PrinterPort9100Manager
import com.azuredevid.cassy.core.hardware.ShiftReportThermalBuilder
import com.azuredevid.cassy.core.security.SessionManager
import com.azuredevid.cassy.domain.model.XReport
import com.azuredevid.cassy.domain.model.ZReport
import com.azuredevid.cassy.domain.repository.PrinterSettingsRepository
import com.azuredevid.cassy.ui.designsystem.UiText

/**
 * Handles ESC/POS thermal receipt printing and cash drawer kick for X-Report and Z-Report.
 */
internal class ShiftReportPrinter(
    private val printerSettings: PrinterSettingsRepository,
    private val sessionManager: SessionManager?,
    private val printerManager: PrinterPort9100Manager,
    private val thermalBuilder: ShiftReportThermalBuilder
) {

    suspend fun printXReport(
        report: XReport,
        onPrintingStateChanged: (Boolean) -> Unit,
        onEffect: suspend (ShiftUiEffect) -> Unit
    ) {
        val config = printerSettings.getConfig()
        if (!config.hasCashierPrinter) {
            onEffect(ShiftUiEffect.ShowToast(UiText.res(R.string.msg_cashier_printer_missing)))
            return
        }
        onPrintingStateChanged(true)
        val payload = thermalBuilder.buildXReportReceipt(resolveStoreName(), report)
        printerManager.printRawBytes(config.cashierPrinterIp, payload, config.cashierPrinterPort)
            .onSuccess {
                onPrintingStateChanged(false)
                onEffect(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_xreport_printed)))
            }
            .onFailure {
                onPrintingStateChanged(false)
                onEffect(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_xreport_print_failed)))
            }
    }

    suspend fun printZReport(
        report: ZReport,
        onPrintingStateChanged: (Boolean) -> Unit,
        onEffect: suspend (ShiftUiEffect) -> Unit
    ) {
        val config = printerSettings.getConfig()
        if (!config.hasCashierPrinter) {
            onEffect(ShiftUiEffect.ShowToast(UiText.res(R.string.msg_cashier_printer_missing)))
            return
        }
        onPrintingStateChanged(true)
        val payload = thermalBuilder.buildZReportReceipt(resolveStoreName(), report)
        printerManager.printRawBytes(config.cashierPrinterIp, payload, config.cashierPrinterPort)
            .onSuccess {
                onPrintingStateChanged(false)
                onEffect(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_zreport_printed)))
            }
            .onFailure {
                onPrintingStateChanged(false)
                onEffect(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_zreport_print_failed)))
            }
    }

    suspend fun kickDrawerIfConfigured(onEffect: suspend (ShiftUiEffect) -> Unit) {
        val config = printerSettings.getConfig()
        if (!config.hasCashierPrinter) {
            onEffect(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_drawer_no_ip)))
            return
        }
        printerManager.openCashDrawer(config.cashierPrinterIp, config.cashierPrinterPort)
            .onFailure {
                onEffect(ShiftUiEffect.ShowToast(UiText.res(R.string.shift_msg_drawer_failed, config.cashierPrinterIp)))
            }
    }

    private fun resolveStoreName(): String {
        return sessionManager?.currentSession()?.staff?.storeName
            ?.takeIf { it.isNotBlank() }
            ?: "KASIR POS"
    }
}
