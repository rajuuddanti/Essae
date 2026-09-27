package com.mahamart.essae.cloud

import android.content.Context
import android.provider.Settings
import com.mahamart.essae.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Stores the current Firebase registration token against the already
 * registered physical store device. The device security token is used
 * to authenticate the RPC.
 */
class FcmTokenRegistrar(private val context: Context) {

    fun register(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return

        val prefs = context.getSharedPreferences(
            "store_device_registration",
            Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean("registered", false)) return

        val deviceToken = prefs.getString("device_token", "").orEmpty()
        if (deviceToken.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val body = JSONObject()
                    .put(
                        "p_device_id",
                        Settings.Secure.getString(
                            context.contentResolver,
                            Settings.Secure.ANDROID_ID
                        ).orEmpty()
                    )
                    .put("p_device_token", deviceToken)
                    .put("p_fcm_token", clean)

                val connection =
                    (URL(
                        BuildConfig.SUPABASE_URL.trimEnd('/') +
                            "/rest/v1/rpc/store_set_fcm_token"
                    ).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty(
                            "apikey",
                            BuildConfig.SUPABASE_PUBLISHABLE_KEY
                        )
                        setRequestProperty(
                            "Authorization",
                            "Bearer " + BuildConfig.SUPABASE_PUBLISHABLE_KEY
                        )
                        setRequestProperty(
                            "Content-Type",
                            "application/json"
                        )
                    }

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                if (connection.responseCode !in 200..299) {
                    error("FCM token registration failed: ${connection.responseCode}")
                }
                connection.disconnect()
            }
        }
    }
}
