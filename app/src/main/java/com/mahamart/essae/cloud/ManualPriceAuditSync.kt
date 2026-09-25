package com.mahamart.essae.cloud

import android.content.Context
import android.provider.Settings
import com.mahamart.essae.BuildConfig
import com.mahamart.essae.data.PriceChangeAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

class ManualPriceAuditSync(private val context: Context) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun deviceId(): String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: ""

    fun deviceIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
            ?.hostAddress ?: ""
    } catch (_: Exception) { "" }

    suspend fun pushManualChange(audit: PriceChangeAudit): Boolean = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || publishableKey.isBlank()) return@withContext false
        try {
            val items = JSONArray().put(JSONObject().apply {
                put("plu_no", audit.pluNo)
                put("plu_name", audit.pluName)
                put("old_price", audit.oldPrice)
                put("new_price", audit.newPrice)
            })
            val body = JSONObject().apply {
                put("p_device_ip", audit.deviceIp)
                put("p_device_id", audit.deviceId)
                put("p_scale_ip", audit.scaleIp)
                put("p_source", "MANUAL")
                put("p_items", items)
            }
            val conn = (URL("$baseUrl/rest/v1/rpc/log_pending_price_changes").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=minimal")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            ok
        } catch (_: Exception) { false }
    }
}
