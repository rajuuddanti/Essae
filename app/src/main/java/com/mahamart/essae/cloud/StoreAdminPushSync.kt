package com.mahamart.essae.cloud

import android.content.Context
import android.provider.Settings
import com.mahamart.essae.BuildConfig
import com.mahamart.essae.data.Plu
import com.mahamart.essae.data.PriceChangeAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL

/**
 * Pulls Admin Push updates for the currently registered physical device.
 *
 * Admin Push is cloud -> store:
 *   Supabase pending update -> Room price -> local PENDING audit -> ACK SYNCED.
 *
 * It does not upload to Essae. Upload All remains the physical-scale step.
 */
class StoreAdminPushSync(private val context: Context) {

    private val appContext = context.applicationContext
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    private val registrationPrefs =
        appContext.getSharedPreferences(
            "store_device_registration",
            Context.MODE_PRIVATE
        )

    fun deviceId(): String =
        Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()

    fun deviceIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .firstOrNull {
                !it.isLoopbackAddress &&
                    it.hostAddress?.contains(":") == false
            }
            ?.hostAddress ?: ""
    } catch (_: Exception) {
        ""
    }

    suspend fun pullAndApply(
        currentPlus: List<Plu>,
        upsert: suspend (Plu) -> Unit,
        insertAudit: suspend (PriceChangeAudit) -> Unit
    ): Result<List<Int>> = withContext(Dispatchers.IO) {
        runCatching {
            require(baseUrl.isNotBlank()) { "Supabase URL is missing." }
            require(publishableKey.isNotBlank()) {
                "Supabase publishable key is missing."
            }

            if (!registrationPrefs.getBoolean("registered", false)) {
                return@runCatching emptyList()
            }

            val id = deviceId().trim()
            if (id.isBlank()) return@runCatching emptyList()

            val ip = deviceIp()
            val pending = rpc(
                "store_get_pending_admin_price_updates_v2",
                JSONObject()
                    .put("p_device_id", id)
                    .put("p_device_ip", ip)
            )

            val rows = when (val value =
                JSONTokener(pending.ifBlank { "[]" }).nextValue()
            ) {
                is JSONArray -> value
                is JSONObject -> JSONArray().put(value)
                else -> JSONArray()
            }

            if (rows.length() == 0) {
                return@runCatching emptyList()
            }

            val updateIds = mutableListOf<String>()
            val appliedPluNumbers = linkedSetOf<Int>()

            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val updateId = row.optString("update_id").trim()
                if (updateId.isBlank()) continue

                val items = parseItems(row.opt("items"))
                val itemList = items
                    .filter { it.has("plu_no") && it.has("new_price") }

                if (itemList.isEmpty()) continue

                for (item in itemList) {
                    val pluNo = item.optInt("plu_no", -1)
                    val newPrice = item.optDouble("new_price", Double.NaN)
                    if (pluNo < 0 || newPrice.isNaN()) continue

                    val existing = currentPlus.firstOrNull {
                        it.number == pluNo
                    }

                    val plu = existing?.copy(unitPrice = newPrice)
                        ?: Plu(
                            number = pluNo,
                            name = item.optString("plu_name"),
                            code = item.optString("plu_code"),
                            uom = item.optInt("uom", 0),
                            unitPrice = newPrice
                        )

                    upsert(plu)

                    if (existing == null || existing.unitPrice != newPrice) {
                        insertAudit(
                            PriceChangeAudit(
                                pluNo = plu.number,
                                pluName = plu.name,
                                oldPrice = existing?.unitPrice ?: 0.0,
                                newPrice = newPrice,
                                source = "ADMIN_PUSH",
                                status = "PENDING",
                                deviceIp = ip,
                                deviceId = id,
                                scaleIp = "",
                                cloudSynced = true
                            )
                        )
                        appliedPluNumbers += pluNo
                    }
                }

                // TEMP TEST MODE:
                // Treat an Admin Push price change as the current store
                // price immediately, so the Admin screen can verify the
                // store's new price before physical Essae upload.
                rpc(
                    "log_pending_price_changes",
                    JSONObject()
                        .put("p_device_ip", ip)
                        .put("p_device_id", id)
                        .put("p_scale_ip", "")
                        .put("p_source", "ADMIN_PUSH")
                        .put(
                            "p_items",
                            JSONArray().apply {
                                items.forEach { put(it) }
                            }
                        )
                )

                updateIds += updateId
            }

            if (updateIds.isNotEmpty()) {
                rpc(
                    "store_mark_admin_price_updates_synced_v2",
                    JSONObject()
                        .put("p_device_id", id)
                        .put(
                            "p_update_ids",
                            JSONArray(updateIds)
                        )
                )
            }

            appliedPluNumbers.toList()
        }
    }

    suspend fun startScaleUpload(
        pluCount: Int,
        scaleIp: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(baseUrl.isNotBlank()) { "Supabase URL is missing." }
            require(publishableKey.isNotBlank()) {
                "Supabase publishable key is missing."
            }

            if (!registrationPrefs.getBoolean("registered", false)) {
                return@runCatching ""
            }

            val id = deviceId().trim()
            if (id.isBlank()) return@runCatching ""

            val response = rpc(
                "start_scale_upload",
                JSONObject()
                    .put("p_device_ip", deviceIp())
                    .put("p_device_id", id)
                    .put("p_scale_ip", scaleIp)
                    .put("p_plu_count", pluCount)
            )

            val value = JSONTokener(response.ifBlank { "\"\"" }).nextValue()
            when (value) {
                is String -> value
                else -> value.toString()
            }
        }
    }

    suspend fun completeScaleUpload(
        sessionId: String,
        scaleIp: String,
        plus: List<Plu>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (sessionId.isBlank()) return@runCatching 0

            val id = deviceId().trim()
            if (id.isBlank()) return@runCatching 0

            val items = JSONArray()
            plus.forEach { plu ->
                items.put(
                    JSONObject()
                        .put("plu_no", plu.number)
                        .put("plu_name", plu.name)
                        .put("unit_price", plu.unitPrice)
                )
            }

            val response = rpc(
                "complete_scale_upload",
                JSONObject()
                    .put("p_session_id", sessionId)
                    .put("p_device_ip", deviceIp())
                    .put("p_device_id", id)
                    .put("p_scale_ip", scaleIp)
                    .put("p_items", items)
            )

            JSONTokener(response.ifBlank { "0" }).nextValue().let {
                when (it) {
                    is Number -> it.toInt()
                    is String -> it.toIntOrNull() ?: 0
                    else -> 0
                }
            }
        }
    }

    suspend fun failScaleUpload(
        sessionId: String,
        message: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (sessionId.isBlank()) return@runCatching Unit

            rpc(
                "fail_scale_upload",
                JSONObject()
                    .put("p_session_id", sessionId)
                    .put("p_error_message", message)
            )
            Unit
        }
    }

    private fun parseItems(value: Any?): List<JSONObject> {
        return when (value) {
            is JSONArray -> {
                (0 until value.length()).mapNotNull {
                    value.optJSONObject(it)
                }
            }
            is String -> {
                runCatching {
                    val parsed = JSONTokener(value).nextValue()
                    if (parsed is JSONArray) {
                        (0 until parsed.length()).mapNotNull {
                            parsed.optJSONObject(it)
                        }
                    } else {
                        emptyList()
                    }
                }.getOrDefault(emptyList())
            }
            else -> emptyList()
        }
    }

    private fun rpc(
        function: String,
        body: JSONObject
    ): String {
        val connection =
            (URL(
                "$baseUrl/rest/v1/rpc/$function"
            ).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("apikey", publishableKey)
                setRequestProperty(
                    "Authorization",
                    "Bearer $publishableKey"
                )
                setRequestProperty(
                    "Content-Type",
                    "application/json"
                )
                setRequestProperty(
                    "Accept",
                    "application/json"
                )
            }

        connection.outputStream.use {
            it.write(
                body.toString()
                    .toByteArray(Charsets.UTF_8)
            )
        }

        val responseCode = connection.responseCode
        val stream =
            if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
                    ?: connection.inputStream
            }

        val response =
            stream.bufferedReader()
                .use { it.readText() }

        connection.disconnect()

        if (responseCode !in 200..299) {
            val message =
                runCatching {
                    JSONObject(response)
                        .optString("message")
                        .ifBlank {
                            JSONObject(response)
                                .optString("hint")
                        }
                }.getOrDefault("Admin Push sync failed.")

            error(
                if (message.isBlank())
                    "$function failed ($responseCode)."
                else
                    "$function failed: $message"
            )
        }

        return response
    }
}
