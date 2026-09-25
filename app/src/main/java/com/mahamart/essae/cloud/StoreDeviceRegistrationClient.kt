package com.mahamart.essae.cloud

import android.content.Context
import com.mahamart.essae.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dependency-free Supabase client for store-device registration.
 *
 * This client does not require a Supabase Auth session. The registration
 * RPC is intentionally granted to anon/authenticated and validates the
 * one-time code + physical device ID server-side.
 */
class StoreDeviceRegistrationClient(context: Context) {

    private val appContext = context.applicationContext
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun registerDevice(
        registrationCode: String,
        deviceId: String,
        deviceName: String,
        deviceIp: String
    ): Result<RegisteredDevice> = withContext(Dispatchers.IO) {
        runCatching {
            require(baseUrl.isNotBlank()) { "Supabase URL is missing." }
            require(publishableKey.isNotBlank()) { "Supabase publishable key is missing." }
            require(registrationCode.trim().isNotBlank()) {
                "Registration code is required."
            }
            require(deviceId.trim().isNotBlank()) {
                "Device ID is missing."
            }

            val body = JSONObject()
                .put("p_registration_code", registrationCode.trim())
                .put("p_device_id", deviceId.trim())
                .put("p_device_name", deviceName.trim())
                .put("p_device_ip", deviceIp.trim())

            val connection = open(
                "$baseUrl/rest/v1/rpc/store_register_device",
                "POST"
            )
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val response = readResponse(connection)
            if (connection.responseCode !in 200..299) {
                error(extractError(response, "Device registration failed."))
            }

            val value = JSONTokener(response.trim()).nextValue()
            val row = when (value) {
                is JSONArray -> {
                    if (value.length() == 0) error("Registration returned no device.")
                    value.getJSONObject(0)
                }
                is JSONObject -> value
                else -> error("Unexpected registration response.")
            }

            RegisteredDevice(
                storeCode = row.optString("store_code"),
                storeName = row.optString("store_name"),
                deviceId = row.optString("device_id"),
                deviceName = row.optString("device_name"),
                deviceIp = row.optString("device_ip"),
                registeredAt = row.optString("registered_at")
            )
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Authorization", "Bearer $publishableKey")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        return stream.bufferedReader().use { it.readText() }
    }

    private fun extractError(body: String, fallback: String): String {
        return runCatching {
            val json = JSONObject(body)
            json.optString("msg").ifBlank {
                json.optString("message").ifBlank {
                    json.optString("error_description").ifBlank { fallback }
                }
            }
        }.getOrDefault(fallback)
    }

    data class RegisteredDevice(
        val storeCode: String,
        val storeName: String,
        val deviceId: String,
        val deviceName: String,
        val deviceIp: String,
        val registeredAt: String
    )
}
