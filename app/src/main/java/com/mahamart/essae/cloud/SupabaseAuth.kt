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

/** Dependency-free Supabase Auth/admin client. Store phones do not use this class. */
class SupabaseAuth(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "supabase_admin_session",
        Context.MODE_PRIVATE
    )

    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val isSignedIn: Boolean
        get() = prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank().not()

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)

    val userId: String?
        get() = prefs.getString(KEY_USER_ID, null)

    val userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)

    suspend fun restoreSession(): Result<AdminProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val token = accessToken ?: error("No saved admin session.")
            val id = userId ?: error("Saved admin session is incomplete.")
            val profile = getProfile(id, token)
            if (profile.role != "ADMIN" || !profile.active) {
                clearSession()
                error("Admin session is no longer authorized.")
            }
            profile
        }
    }

    suspend fun signIn(email: String, password: String): Result<AdminProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(baseUrl.isNotBlank()) { "Supabase URL is missing." }
                require(publishableKey.isNotBlank()) { "Supabase publishable key is missing." }

                val auth = postJson(
                    "$baseUrl/auth/v1/token?grant_type=password",
                    JSONObject()
                        .put("email", email.trim())
                        .put("password", password)
                )

                val token = auth.optString("access_token")
                val refresh = auth.optString("refresh_token")
                val user = auth.optJSONObject("user")
                    ?: error("Supabase did not return a user.")
                val id = user.optString("id")
                val returnedEmail = user.optString("email", email.trim())

                require(token.isNotBlank() && id.isNotBlank()) {
                    "Supabase returned an incomplete login response."
                }

                val profile = getProfile(id, token)
                if (profile.role != "ADMIN" || !profile.active) {
                    clearSession()
                    error("This account is not an active administrator.")
                }

                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, token)
                    .putString(KEY_REFRESH_TOKEN, refresh)
                    .putString(KEY_USER_ID, id)
                    .putString(KEY_EMAIL, returnedEmail)
                    .apply()

                profile
            }
        }

    suspend fun getActiveStores(): Result<List<StoreOption>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken ?: error("Admin session expired. Sign in again.")
                val url =
                    "$baseUrl/rest/v1/stores?select=id,store_code,store_name&active=eq.true&order=store_code"
                val connection = open(url, "GET", token)
                val response = readResponse(connection)
                if (connection.responseCode !in 200..299) {
                    error(extractError(response, "Could not load stores."))
                }

                val rows = JSONArray(response)
                buildList {
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        add(
                            StoreOption(
                                id = row.optString("id"),
                                code = row.optString("store_code"),
                                name = row.optString("store_name")
                            )
                        )
                    }
                }
            }
        }

    suspend fun getStorePluPrices(pluNo: Int): Result<List<StorePluPrice>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken ?: error("Admin session expired. Sign in again.")
                val body = JSONObject().put("p_plu_no", pluNo)
                val response = postRpcValue(
                    "$baseUrl/rest/v1/rpc/admin_get_store_plu_prices",
                    body,
                    token
                )

                val rows = JSONArray(response)
                buildList {
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        add(
                            StorePluPrice(
                                storeId = row.optString("store_id"),
                                storeCode = row.optString("store_code"),
                                storeName = row.optString("store_name"),
                                currentPrice = if (row.isNull("current_price")) null
                                else row.optDouble("current_price"),
                                lastUploadedAt = row.optString("last_uploaded_at")
                                    .ifBlank { null },
                                pendingPushedAt = row.optString("pending_pushed_at")
                                    .ifBlank { null },
                                pendingPushedPrice = if (row.isNull("pending_pushed_price"))
                                    null
                                else
                                    row.optDouble("pending_pushed_price"),
                                lastUploadSource = row.optString("last_upload_source")
                                    .ifBlank { null },
                                deviceIp = row.optString("device_ip").ifBlank { null }
                            )
                        )
                    }
                }
            }
        }

    suspend fun publishAdminPriceUpdate(
        applyToAll: Boolean,
        storeIds: List<String>,
        items: List<AdminPriceItem>,
        note: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = accessToken ?: error("Admin session expired. Sign in again.")
            require(items.isNotEmpty()) { "At least one price item is required." }
            if (!applyToAll) require(storeIds.isNotEmpty()) { "Select at least one store." }

            val itemArray = JSONArray()
            items.forEach { item ->
                itemArray.put(
                    JSONObject()
                        .put("plu_no", item.pluNo)
                        .put("plu_name", item.pluName)
                        .put("new_price", item.newPrice)
                )
            }

            val storeArray = JSONArray()
            storeIds.forEach { storeArray.put(it) }

            val body = JSONObject()
                .put("p_apply_to_all", applyToAll)
                .put("p_store_ids", storeArray)
                .put("p_items", itemArray)
                .put("p_note", note)

            postRpcValue(
                "$baseUrl/rest/v1/rpc/admin_publish_price_update",
                body,
                token
            )
        }
    }

    suspend fun sendAdminPushNotification(updateId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken ?: error("Admin session expired. Sign in again.")
                require(updateId.isNotBlank()) { "Admin Push update ID is missing." }

                val connection = open(
                    "$baseUrl/functions/v1/send-admin-push-notification",
                    "POST",
                    token
                )
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use {
                    it.write(
                        JSONObject().put("update_id", updateId)
                            .toString().toByteArray(Charsets.UTF_8)
                    )
                }

                val response = readResponse(connection)
                if (connection.responseCode !in 200..299) {
                    error(extractError(response, "Could not send store notification."))
                }
                Unit
            }
        }

    suspend fun getStoreOperationsDetail(storeCode: String): Result<StoreOperationsDetail> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken ?: error("Admin session expired. Sign in again.")
                val encoded = java.net.URLEncoder.encode(storeCode.trim(), Charsets.UTF_8.name())
                val store = getActiveStores().getOrThrow().firstOrNull { it.code == storeCode.trim() }
                val resolvedStoreName = store?.name.orEmpty()

                fun getRows(path: String): JSONArray {
                    val connection = open("$baseUrl/rest/v1/$path", "GET", token)
                    val response = readResponse(connection)
                    if (connection.responseCode !in 200..299) {
                        error(extractError(response, "Could not load store operations."))
                    }
                    return JSONArray(response)
                }

                val changes = getRows(
                    "admin_price_change_report?select=plu_no,plu_name,old_price,new_price,status,source,changed_at,uploaded_at&store_code=eq.$encoded&order=changed_at.desc&limit=100"
                )
                val changeRows = buildList {
                    for (i in 0 until changes.length()) {
                        val row = changes.getJSONObject(i)
                        add(
                            PriceChangeRow(
                                pluNo = row.optInt("plu_no", 0),
                                pluName = row.optString("plu_name"),
                                oldPrice = row.optDouble("old_price", 0.0),
                                newPrice = row.optDouble("new_price", 0.0),
                                status = row.optString("status"),
                                source = row.optString("source"),
                                changedAt = row.optString("changed_at").ifBlank { null },
                                uploadedAt = row.optString("uploaded_at").ifBlank { null }
                            )
                        )
                    }
                }

                val uploads = getRows(
                    "admin_scale_upload_report?select=device_id,scale_ip,started_at,completed_at,status,plu_count,error_message&store_code=eq.$encoded&order=started_at.desc&limit=50"
                )
                val uploadRows = buildList {
                    for (i in 0 until uploads.length()) {
                        val row = uploads.getJSONObject(i)
                        add(
                            ScaleUploadRow(
                                deviceId = row.optString("device_id"),
                                scaleIp = row.optString("scale_ip"),
                                startedAt = row.optString("started_at").ifBlank { null },
                                completedAt = row.optString("completed_at").ifBlank { null },
                                status = row.optString("status"),
                                pluCount = row.optInt("plu_count", 0),
                                errorMessage = row.optString("error_message").ifBlank { null }
                            )
                        )
                    }
                }

                val pushes = getRows(
                    "admin_price_push_report?select=update_id,created_at,created_by_name,mode,item_count,status,synced_at,uploaded_at&store_code=eq.$encoded&order=created_at.desc&limit=50"
                )
                val pushRows = buildList {
                    for (i in 0 until pushes.length()) {
                        val row = pushes.getJSONObject(i)
                        add(
                            PricePushRow(
                                updateId = row.optString("update_id"),
                                createdAt = row.optString("created_at").ifBlank { null },
                                createdByName = row.optString("created_by_name"),
                                mode = row.optString("mode"),
                                itemCount = row.optInt("item_count", 0),
                                status = row.optString("status"),
                                syncedAt = row.optString("synced_at").ifBlank { null },
                                uploadedAt = row.optString("uploaded_at").ifBlank { null }
                            )
                        )
                    }
                }

                StoreOperationsDetail(
                    storeCode = storeCode,
                    storeName = resolvedStoreName,
                    pendingChanges = changeRows.filter { it.status == "PENDING" },
                    recentChanges = changeRows,
                    uploads = uploadRows,
                    pushes = pushRows
                )
            }
        }

    suspend fun getOperationsSnapshot(): Result<List<StoreOperations>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken ?: error("Admin session expired. Sign in again.")

                val stores = getActiveStores().getOrThrow()
                val devices = getRegisteredStoreDevices().getOrThrow()

                val pendingUrl =
                    "$baseUrl/rest/v1/admin_price_change_report" +
                        "?select=store_code,changed_at" +
                        "&status=eq.PENDING" +
                        "&order=changed_at.desc"
                val pendingConnection = open(pendingUrl, "GET", token)
                val pendingResponse = readResponse(pendingConnection)
                if (pendingConnection.responseCode !in 200..299) {
                    error(extractError(pendingResponse, "Could not load pending price changes."))
                }

                val pendingRows = JSONArray(pendingResponse)
                val pendingByStore = mutableMapOf<String, Int>()
                for (i in 0 until pendingRows.length()) {
                    val row = pendingRows.getJSONObject(i)
                    val code = row.optString("store_code")
                    if (code.isNotBlank()) {
                        pendingByStore[code] = (pendingByStore[code] ?: 0) + 1
                    }
                }

                val uploadUrl =
                    "$baseUrl/rest/v1/admin_scale_upload_report" +
                        "?select=store_code,started_at,completed_at,status" +
                        "&order=started_at.desc" +
                        "&limit=500"
                val uploadConnection = open(uploadUrl, "GET", token)
                val uploadResponse = readResponse(uploadConnection)
                if (uploadConnection.responseCode !in 200..299) {
                    error(extractError(uploadResponse, "Could not load upload history."))
                }

                val uploadRows = JSONArray(uploadResponse)
                val latestUploadByStore = mutableMapOf<String, String>()
                for (i in 0 until uploadRows.length()) {
                    val row = uploadRows.getJSONObject(i)
                    val code = row.optString("store_code")
                    val status = row.optString("status")
                    val completed = row.optString("completed_at").ifBlank { null }
                    if (code.isNotBlank() && status == "COMPLETED" && completed != null) {
                        latestUploadByStore.putIfAbsent(code, completed)
                    }
                }

                stores.map { store ->
                    val storeDevices = devices.filter { it.storeCode == store.code }
                    val activeDeviceCount = storeDevices.count { it.active }
                    val latestSeen = storeDevices
                        .mapNotNull { it.lastSeenAt }
                        .maxByOrNull { it }

                    StoreOperations(
                        storeCode = store.code,
                        storeName = store.name,
                        activeDeviceCount = activeDeviceCount,
                        deviceCount = storeDevices.size,
                        lastSeenAt = latestSeen,
                        pendingCount = pendingByStore[store.code] ?: 0,
                        lastUploadAt = latestUploadByStore[store.code]
                    )
                }
            }
        }

    suspend fun getRegisteredStoreDevices(): Result<List<StoreDevice>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = accessToken ?: error("Admin session expired. Sign in again.")
                val url =
                    "$baseUrl/rest/v1/admin_store_devices" +
                        "?select=id,store_code,store_name,device_id,device_name,device_ip,active,registered_at,last_seen_at,updated_at" +
                        "&order=store_code.asc,device_name.asc"

                val connection = open(url, "GET", token)
                val response = readResponse(connection)
                if (connection.responseCode !in 200..299) {
                    error(extractError(response, "Could not load registered devices."))
                }

                val rows = JSONArray(response)
                buildList {
                    for (i in 0 until rows.length()) {
                        val row = rows.getJSONObject(i)
                        add(
                            StoreDevice(
                                id = row.optString("id"),
                                storeCode = row.optString("store_code"),
                                storeName = row.optString("store_name"),
                                deviceId = row.optString("device_id"),
                                deviceName = row.optString("device_name"),
                                deviceIp = row.optString("device_ip"),
                                active = row.optBoolean("active", false),
                                registeredAt = row.optString("registered_at"),
                                lastSeenAt = row.optString("last_seen_at").ifBlank { null },
                                updatedAt = row.optString("updated_at")
                            )
                        )
                    }
                }
            }
        }

    suspend fun generateStoreDeviceCode(
        storeCode: String,
        expiresMinutes: Int = 60
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = accessToken ?: error("Admin session expired. Sign in again.")
            val body = JSONObject()
                .put("p_store_code", storeCode.trim())
                .put("p_expires_minutes", expiresMinutes)

            postRpcValue(
                "$baseUrl/rest/v1/rpc/admin_generate_store_device_code",
                body,
                token
            )
        }
    }

    fun signOut() {
        clearSession()
    }

    private fun getProfile(id: String, token: String): AdminProfile {
        val url = "$baseUrl/rest/v1/profiles?id=eq.$id&select=id,full_name,role,active"
        val connection = open(url, "GET", token)
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            error(extractError(response, "Could not verify admin profile."))
        }

        val rows = JSONArray(response)
        if (rows.length() == 0) error("Admin profile was not found.")

        val row = rows.getJSONObject(0)
        return AdminProfile(
            id = row.optString("id"),
            fullName = row.optString("full_name"),
            role = row.optString("role"),
            active = row.optBoolean("active", false)
        )
    }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val connection = open(url, "POST", null)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) error(extractError(response, "Request failed."))
        return JSONObject(response)
    }

    private fun postRpcValue(url: String, body: JSONObject, bearer: String): String {
        val connection = open(url, "POST", bearer)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) error(extractError(response, "Request failed."))

        val value = JSONTokener(response.trim()).nextValue()
        return when (value) {
            is String -> value
            else -> value.toString()
        }
    }

    private fun open(url: String, method: String, bearer: String?): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("apikey", publishableKey)
            if (!bearer.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearer")
            }
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream
        else connection.errorStream ?: connection.inputStream
        return stream.bufferedReader().use { it.readText() }
    }

    private fun extractError(body: String, fallback: String): String {
        return runCatching {
            val json = JSONObject(body)
            json.optString("msg").ifBlank {
                json.optString("error_description").ifBlank {
                    json.optString("message").ifBlank { fallback }
                }
            }
        }.getOrDefault(fallback)
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }

    data class AdminProfile(
        val id: String,
        val fullName: String,
        val role: String,
        val active: Boolean
    )

    data class StoreOption(
        val id: String,
        val code: String,
        val name: String
    )

    data class StorePluPrice(
        val storeId: String,
        val storeCode: String,
        val storeName: String,
        val currentPrice: Double?,
        val lastUploadedAt: String?,
        val pendingPushedAt: String?,
        val pendingPushedPrice: Double?,
        val lastUploadSource: String?,
        val deviceIp: String?
    )

    data class AdminPriceItem(
        val pluNo: Int,
        val pluName: String,
        val newPrice: Double
    )

    data class StoreOperationsDetail(
        val storeCode: String,
        val storeName: String,
        val pendingChanges: List<PriceChangeRow>,
        val recentChanges: List<PriceChangeRow>,
        val uploads: List<ScaleUploadRow>,
        val pushes: List<PricePushRow>
    )

    data class PriceChangeRow(
        val pluNo: Int,
        val pluName: String,
        val oldPrice: Double,
        val newPrice: Double,
        val status: String,
        val source: String,
        val changedAt: String?,
        val uploadedAt: String?
    )

    data class ScaleUploadRow(
        val deviceId: String,
        val scaleIp: String,
        val startedAt: String?,
        val completedAt: String?,
        val status: String,
        val pluCount: Int,
        val errorMessage: String?
    )

    data class PricePushRow(
        val updateId: String,
        val createdAt: String?,
        val createdByName: String,
        val mode: String,
        val itemCount: Int,
        val status: String,
        val syncedAt: String?,
        val uploadedAt: String?
    )

    data class StoreOperations(
        val storeCode: String,
        val storeName: String,
        val activeDeviceCount: Int,
        val deviceCount: Int,
        val lastSeenAt: String?,
        val pendingCount: Int,
        val lastUploadAt: String?
    )

    data class StoreDevice(
        val id: String,
        val storeCode: String,
        val storeName: String,
        val deviceId: String,
        val deviceName: String,
        val deviceIp: String,
        val active: Boolean,
        val registeredAt: String,
        val lastSeenAt: String?,
        val updatedAt: String
    )

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
