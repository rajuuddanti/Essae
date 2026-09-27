package com.mahamart.essae.cloud

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mahamart.essae.AdminPushNotification

/**
 * Receives live FCM Admin Push notifications while the app is backgrounded
 * or closed. Supabase remains the source of truth; this service only alerts.
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
    }
}
