package com.azuredevid.cassy.core.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID

/**
 * Android Bluetooth Classic SPP (RFCOMM) thermal printer manager for 58mm and 80mm ESC/POS printers.
 * All blocking socket operations run strictly on [ioDispatcher] with a bounded timeout to prevent ANRs.
 */
class AndroidBluetoothEscPosManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Long = 4_000L
) : BluetoothPrinterClient {

    private val appContext = context.applicationContext
    private val drawerKickCommand = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())
    private val escInitCommand = byteArrayOf(0x1B, 0x40)

    private fun adapterOrNull(): BluetoothAdapter? {
        val manager = appContext.getSystemService(BluetoothManager::class.java)
        return manager?.adapter
    }

    override fun isBluetoothSupported(): Boolean = adapterOrNull() != null

    override fun isBluetoothEnabled(): Boolean = adapterOrNull()?.isEnabled == true

    override fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    override fun listBondedDevices(): Result<List<PairedBluetoothDevice>> = runCatching {
        if (!hasConnectPermission()) {
            throw SecurityException("Izin Bluetooth belum diberikan")
        }
        val adapter = adapterOrNull()
            ?: throw IllegalStateException("Perangkat tidak memiliki adapter Bluetooth")
        if (!adapter.isEnabled) {
            throw IllegalStateException("Bluetooth perangkat sedang mati")
        }
        adapter.bondedDevices
            .orEmpty()
            .map { device ->
                PairedBluetoothDevice(
                    name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                    macAddress = device.address.uppercase()
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun checkHealth(macAddress: String): PrinterPort9100Manager.HealthCheckResult {
        val result = transmit(macAddress, escInitCommand)
        return result.fold(
            onSuccess = {
                PrinterPort9100Manager.HealthCheckResult(
                    PrinterPort9100Manager.PrinterHealth.READY,
                    "Printer Bluetooth terhubung dan siap mencetak"
                )
            },
            onFailure = { err ->
                PrinterPort9100Manager.HealthCheckResult(
                    PrinterPort9100Manager.PrinterHealth.OFFLINE,
                    err.message ?: "Printer Bluetooth tidak merespons"
                )
            }
        )
    }

    override suspend fun printRawBytes(macAddress: String, payload: ByteArray): Result<Boolean> {
        if (payload.isEmpty()) {
            return Result.failure(IllegalArgumentException("Payload byte cetak tidak boleh kosong"))
        }
        return transmit(macAddress, payload)
    }

    override suspend fun openCashDrawer(macAddress: String): Result<Boolean> =
        transmit(macAddress, drawerKickCommand)

    @SuppressLint("MissingPermission")
    private suspend fun transmit(macAddress: String, payload: ByteArray): Result<Boolean> =
        withContext(ioDispatcher) {
            runCatching {
                val normalizedMac = normalizeBluetoothMac(macAddress)
                    ?: throw IllegalArgumentException("Alamat MAC Bluetooth tidak valid")
                if (!hasConnectPermission()) {
                    throw SecurityException("Izin Bluetooth belum diberikan")
                }
                val adapter = adapterOrNull()
                    ?: throw IllegalStateException("Perangkat tidak mendukung Bluetooth")
                if (!adapter.isEnabled) {
                    throw IllegalStateException("Bluetooth sedang nonaktif")
                }
                val device = adapter.getRemoteDevice(normalizedMac)
                var socket: BluetoothSocket? = null
                try {
                    withTimeout(connectTimeoutMs) {
                        if (hasScanPermission() && adapter.isDiscovering) {
                            runCatching { adapter.cancelDiscovery() }
                        }
                        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        socket!!.connect()
                        val out = socket!!.outputStream
                        out.write(payload)
                        out.flush()
                        // Brief drain delay so the Bluetooth serial buffer finishes before close.
                        delay(120L)
                    }
                    true
                } catch (e: IOException) {
                    throw IOException("Gagal terhubung ke printer Bluetooth ($normalizedMac): ${e.message}", e)
                } finally {
                    runCatching { socket?.close() }
                }
            }
        }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        /** Standard Serial Port Profile (SPP) RFCOMM UUID used by thermal ESC/POS printers. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

        fun normalizeBluetoothMac(raw: String): String? {
            val trimmed = raw.trim()
            return if (MAC_REGEX.matches(trimmed)) trimmed.uppercase() else null
        }
    }
}
