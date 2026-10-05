package com.mahamart.essae.cloud

import android.content.Context
import android.provider.Settings
import com.mahamart.essae.BuildConfig
import com.mahamart.essae.data.Plu
import com.mahamart.essae.data.PriceChangeAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private fun deviceToken(): String =
        registrationPrefs.getString("device_token", "").orEmpty()

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
        readPrice: suspend (Int) -> Double?,
        insertAudit: suspend (PriceChangeAudit) -> Unit,
        hasAdminAudit: suspend (String, Int) -> Boolean
    ): Result<List<Int>> = adminPushPullMutex.withLock {
        pullAndApplyLocked(currentPlus, upsert, readPrice, insertAudit, hasAdminAudit)
    }

    private suspend fun pullAndApplyLocked(
        currentPlus: List<Plu>,
        upsert: suspend (Plu) -> Unit,
        readPrice: suspend (Int) -> Double?,
        insertAudit: suspend (PriceChangeAudit) -> Unit,
        hasAdminAudit: suspend (String, Int) -> Boolean
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

            val token = deviceToken().trim()
            require(token.isNotBlank()) {
                "Device security registration required. Re-register this phone."
            }

            val ip = deviceIp()
            val pending = rpc(
                "store_get_pending_admin_price_updates_v2",
                JSONObject()
                    .put("p_device_id", id)
                    .put("p_device_token", token)
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

            // Always apply Admin Pushes in cloud creation order. The RPC normally
            // returns this order, but enforce it locally so a network/proxy/database
            // response cannot let an older push overwrite a newer push for the same PLU.
            val orderedRows = (0 until rows.length())
                .map { rows.getJSONObject(it) }
                .sortedWith(
                    compareBy<JSONObject> { it.optString("created_at") }
                        .thenBy { it.optString("update_id") }
                )

            val updateIds = mutableListOf<String>()
            val appliedPluNumbers = linkedSetOf<Int>()
            // Keep this snapshot current as multiple pushes for one PLU are applied.
            val latestPlus = currentPlus.associateBy { it.number }.toMutableMap()

            for (row in orderedRows)
                val updateId = row.optString("update_id").trim()
                if (updateId.isBlank()) continue

                val items = parseItems(row.opt("items"))
                val itemList = items
                    .filter { it.has("plu_no") && it.has("new_price") }

                // The backend filters SAME-price rows against the store's
                // Store Master Price. An empty list means this Admin Push
                // was valid but requires no local/scale action.
                if (itemList.isEmpty()) {
                    updateIds += updateId
                    continue
                }

                val auditItems = JSONArray()
                for (item in itemList) {
                    val pluNo = item.optInt("plu_no", -1)
                    val newPrice = item.optDouble("new_price", Double.NaN)
                    if (pluNo < 0 || newPrice.isNaN()) continue

                    val existing = latestPlus[pluNo]

                    val pushedCode = item.optString("plu_code").trim()
                    val resolvedCode = pushedCode.ifBlank {
                        existing?.code.orEmpty()
                    }

                    val plu = existing?.copy(
                        unitPrice = newPrice,
                        code = resolvedCode
                    ) ?: Plu(
                        number = pluNo,
                        name = item.optString("plu_name"),
                        code = resolvedCode,
                        uom = item.optInt("uom", 0),
                        unitPrice = newPrice
                    )

                    auditItems.put(
                        JSONObject(item.toString())
                            .put("admin_update_id", updateId)
                            .put(
                                "old_price",
                                item.optDouble(
                                    "old_price",
                                    existing?.unitPrice ?: 0.0
                                )
                            )
                    )
                    upsert(plu)
                    val savedPrice = readPrice(pluNo)
                    check(savedPrice != null && kotlin.math.abs(savedPrice - newPrice) < 0.0001) {
                        "Admin Push verification failed for PLU $pluNo: expected $newPrice, saved $savedPrice"
                    }
                    latestPlus[pluNo] = plu

                    if (!hasAdminAudit(updateId, pluNo)) {
                        insertAudit(
                            PriceChangeAudit(
                                adminUpdateId = updateId,
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

                // Record the Admin Push as a pending store-price audit.
                // The confirmed store price remains the physical scale
                // upload result recorded by complete_scale_upload().
                rpc(
                    "log_pending_price_changes",
                    JSONObject()
                        .put("p_device_ip", ip)
                        .put("p_device_id", id)
                        .put("p_device_token", token)
                        .put("p_scale_ip", "")
                        .put("p_source", "ADMIN_PUSH")
                        .put("p_items", auditItems)
                )

                updateIds += updateId
            }

            if (updateIds.isNotEmpty()) {
                rpc(
                    "store_mark_admin_price_updates_synced_v2",
                    JSONObject()
                        .put("p_device_id", id)
                        .put("p_device_token", token)
                        .put(
                            "p_update_ids",
                            JSONArray(updateIds)
                        )
                )
            }

            appliedPluNumbers.toList()
        }
    }

    suspend fun applyCsvMasterPrices(
        plus: List<Plu>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(baseUrl.isNotBlank()) { "Supabase URL is missing." }
            require(publishableKey.isNotBlank()) {
                "Supabase publishable key is missing."
            }

            if (!registrationPrefs.getBoolean("registered", false)) {
                error("Device registration is required before CSV price sync.")
            }

            val id = deviceId().trim()
            require(id.isNotBlank()) { "Device ID is missing." }

            val token = deviceToken().trim()
            require(token.isNotBlank()) {
                "Device security registration required. Re-register this phone."
            }

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
                "store_apply_csv_master_prices",
                JSONObject()
                    .put("p_device_id", id)
                    .put("p_device_token", token)
                    .put("p_device_ip", deviceIp())
                    .put("p_items", items)
            )

            val value = JSONTokener(response.ifBlank { "0" }).nextValue()
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            }
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

            val token = deviceToken().trim()
            require(token.isNotBlank()) {
                "Device security registration required. Re-register this phone."
            }

            val response = rpc(
                "start_scale_upload",
                JSONObject()
                    .put("p_device_ip", deviceIp())
                    .put("p_device_id", id)
                    .put("p_device_token", token)
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

            val token = deviceToken().trim()
            require(token.isNotBlank()) {
                "Device security registration required. Re-register this phone."
            }

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
                    .put("p_device_token", token)
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

            val id = deviceId().trim()
            val token = deviceToken().trim()
            require(id.isNotBlank() && token.isNotBlank()) {
                "Device security registration required. Re-register this phone."
            }

            rpc(
                "fail_scale_upload",
                JSONObject()
                    .put("p_session_id", sessionId)
                    .put("p_device_id", id)
                    .put("p_device_token", token)
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


/** Process-wide serialization shared by the foreground VM and WorkManager worker. */
private val adminPushPullMutex = Mutex()
