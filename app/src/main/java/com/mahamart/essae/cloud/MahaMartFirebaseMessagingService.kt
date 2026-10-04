package com.mahamart.essae.cloud

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mahamart.essae.AdminPushNotification

/**
 * Receives live FCM Admin Push notifications while the app is backgrounded
 * or closed. Supabase remains the source of truth; this service alerts and
 * schedules a background pull. Physical scale upload remains a separate step.
 */
class MahaMartFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmTokenRegistrar(applicationContext).register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val count = message.data["price_count"]?.toIntOrNull() ?: 1
        AdminPushNotification.showAdminPushReceived(
            applicationContext,
            count
        )
        AdminPushBackgroundSync.enqueue(applicationContext)
    }
}

/** Queues cloud-to-Room syncing without uploading to the scale. */
object AdminPushBackgroundSync {
    private const val UNIQUE_WORK_NAME = "admin-price-background-sync"

    fun enqueue(context: android.content.Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<AdminPushSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
    }
}
