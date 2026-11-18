package com.azuredevid.cassy.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.azuredevid.cassy.R

/**
 * Optional FCM/push registration contract so production flavors can register a push token with the
 * backend without forcing a hard compile-time dependency on google-services.json in local/CI builds.
 */
fun interface PushTokenRegistrar {
    suspend fun registerPushToken(token: String): Result<Unit>
}

/**
 * Manages Android 8+ notification channels and posts high-priority operational notifications
 * (incoming table orders, call-waiter requests, and offline hardware/sync alerts) while respecting
 * Android 13+ POST_NOTIFICATIONS runtime permission.
 */
class CassyNotificationManager(
    private val context: Context
) {
    init {
        ensureChannelsRegistered()
    }

    fun ensureChannelsRegistered() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val orderChannel = NotificationChannel(
            CHANNEL_ORDER_ALERTS,
            context.getString(R.string.notif_channel_orders_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_orders_desc)
        }

        val waiterChannel = NotificationChannel(
            CHANNEL_CALL_WAITER_ALERTS,
            context.getString(R.string.notif_channel_waiter_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_waiter_desc)
        }

        val hardwareChannel = NotificationChannel(
            CHANNEL_HARDWARE_ALERTS,
            context.getString(R.string.notif_channel_hardware_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_hardware_desc)
        }

        manager.createNotificationChannels(listOf(orderChannel, waiterChannel, hardwareChannel))
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun notifyIncomingOrder(notificationId: Int, title: String, message: String) {
        postNotification(
            channelId = CHANNEL_ORDER_ALERTS,
            notificationId = notificationId,
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    fun notifyCallWaiter(notificationId: Int, tableLabel: String, message: String) {
        postNotification(
            channelId = CHANNEL_CALL_WAITER_ALERTS,
            notificationId = notificationId,
            title = tableLabel,
            message = message,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    fun notifyHardwareAlert(notificationId: Int, title: String, message: String) {
        postNotification(
            channelId = CHANNEL_HARDWARE_ALERTS,
            notificationId = notificationId,
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        message: String,
        priority: Int
    ) {
        if (!hasNotificationPermission()) return
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    companion object {
        const val CHANNEL_ORDER_ALERTS = "order_alerts"
        const val CHANNEL_CALL_WAITER_ALERTS = "call_waiter_alerts"
        const val CHANNEL_HARDWARE_ALERTS = "hardware_alerts"
    }
}
