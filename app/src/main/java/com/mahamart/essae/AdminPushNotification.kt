package com.mahamart.essae

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * Local system notifications for Admin Push events.
 *
 * This branch intentionally uses the existing Admin Push polling path.
 * It does not change Supabase or require Firebase/FCM.
 */
object AdminPushNotification {

    private const val CHANNEL_ID = "admin_price_updates"
    private const val CHANNEL_NAME = "Admin Price Updates"
    private const val NOTIFICATION_ID = 4101

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager =
            context.getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when Admin price updates reach this store device."
        }

        manager.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return context.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showAdminPushReceived(
        context: Context,
        priceCount: Int
    ) {
        if (priceCount <= 0 || !canPost(context)) return

        createChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            4101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val title =
            if (priceCount == 1) {
                "New Admin Price Update"
            } else {
                "$priceCount New Admin Price Updates"
            }

        val text =
            if (priceCount == 1) {
                "A new price update was received. Please upload it to the scale."
            } else {
                "New price updates were received. Please review and upload them to the scale."
            }

        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context)
                    .setPriority(android.app.Notification.PRIORITY_HIGH)
            }
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(
                    android.app.Notification.BigTextStyle()
                        .bigText(text)
                )
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

        context
            .getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
