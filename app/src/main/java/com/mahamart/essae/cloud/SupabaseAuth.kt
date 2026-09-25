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
 * Small dependency-free Supabase Auth client.
 *
 * Store phones do not use this class. It is only used by the hidden Admin area.
 * The publishable key is safe to ship in the Android client; never put a
 * service_role/secret key here.
 */
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
                    "$baseUrl/rest/v1/stores?select=store_code,store_name&active=eq.true&order=store_code"
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
                                code = row.optString("store_code"),
                                name = row.optString("store_name")
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
        if (rows.length() == 0) {
            error("Admin profile was not found.")
        }

        val row = rows.getJSONObject(0)
        return AdminProfile(
            id = row.optString("id"),
            fullName = row.optString("full_name"),
            role = row.optString("role"),
            active = row.optBoolean("active", false)
        )
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        bearer: String? = null
    ): JSONObject {
        val connection = open(url, "POST", bearer)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            error(extractError(response, "Request failed."))
        }
        return JSONObject(response)
    }

    private fun postRpcValue(
        url: String,
        body: JSONObject,
        bearer: String
    ): String {
        val connection = open(url, "POST", bearer)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            error(extractError(response, "Request failed."))
        }

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
        val code: String,
        val name: String
    )

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
    }
}
