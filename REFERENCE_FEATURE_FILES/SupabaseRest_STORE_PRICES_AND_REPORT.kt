
package com.mahamart.essae.cloud

import android.content.Context
import com.mahamart.essae.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val SUPABASE_URL = BuildConfig.SUPABASE_URL
private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY

class SupabaseRest(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "supabase_session",
            Context.MODE_PRIVATE
        )

    fun isLoggedIn(): Boolean =
        !prefs.getString("access_token", null).isNullOrBlank()

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    // ---------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------

    suspend fun login(
        email: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {

        request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = JSONObject().apply {
                put("email", email)
                put("password", password)
            },
            authenticated = false
        ).mapCatching { raw ->

            val json = JSONObject(raw)

            val token =
                json.getString("access_token")

            val userId =
                json.getJSONObject("user")
                    .getString("id")

            prefs.edit()
                .putString("access_token", token)
                .putString("user_id", userId)
                .apply()

            userId
        }
    }

    // ---------------------------------------------------------
    // PROFILE
    // ---------------------------------------------------------

    suspend fun getProfile(): Result<Profile> =
        withContext(Dispatchers.IO) {

            val uid =
                prefs.getString(
                    "user_id",
                    null
                )
                    ?: return@withContext Result.failure(
                        Exception("Not signed in")
                    )

            request(
                method = "GET",
                path =
                    "/rest/v1/profiles" +
                            "?id=eq.${url(uid)}" +
                            "&select=id,full_name,role,store_id,active",
                body = null,
                authenticated = true
            ).mapCatching { raw ->

                val a = JSONArray(raw)

                if (a.length() == 0) {
                    error(
                        "No profile found for this account"
                    )
                }

                val o =
                    a.getJSONObject(0)

                Profile(
                    id =
                        o.getString("id"),

                    fullName =
                        o.optString(
                            "full_name",
                            ""
                        ),

                    role =
                        o.getString("role"),

                    storeId =
                        o.optString("store_id")
                            .takeIf {
                                it.isNotBlank() &&
                                        it != "null"
                            },

                    active =
                        o.optBoolean(
                            "active",
                            true
                        )
                )
            }
        }

    // ---------------------------------------------------------
    // STORES
    // ---------------------------------------------------------

    suspend fun getStores(): Result<List<StoreRow>> =
        withContext(Dispatchers.IO) {

            request(
                method = "GET",
                path =
                    "/rest/v1/stores" +
                            "?active=eq.true" +
                            "&select=id,store_code,store_name" +
                            "&order=store_code.asc",
                body = null,
                authenticated = true
            ).mapCatching { raw ->

                val a = JSONArray(raw)

                buildList {

                    for (i in 0 until a.length()) {

                        val o =
                            a.getJSONObject(i)

                        add(
                            StoreRow(
                                id =
                                    o.getString("id"),

                                code =
                                    o.getString(
                                        "store_code"
                                    ),

                                name =
                                    o.getString(
                                        "store_name"
                                    )
                            )
                        )
                    }
                }
            }
        }

    // ---------------------------------------------------------
    // PLU MASTER
    // ---------------------------------------------------------

    suspend fun getPlus(): Result<List<PluRow>> =
        withContext(Dispatchers.IO) {

            request(
                method = "GET",
                path =
                    "/rest/v1/plu_master" +
                            "?select=id,plu_no,plu_name,plu_code,uom,unit_price" +
                            "&order=plu_no.asc",
                body = null,
                authenticated = true
            ).mapCatching { raw ->

                val a = JSONArray(raw)

                buildList {

                    for (i in 0 until a.length()) {

                        val o =
                            a.getJSONObject(i)

                        add(
                            PluRow(
                                id =
                                    o.getString("id"),

                                number =
                                    o.getInt("plu_no"),

                                name =
                                    o.getString(
                                        "plu_name"
                                    ),

                                code =
                                    o.getString(
                                        "plu_code"
                                    ),

                                uom =
                                    o.getInt("uom"),

                                unitPrice =
                                    o.optDouble(
                                        "unit_price",
                                        0.0
                                    )
                            )
                        )
                    }
                }
            }
        }

    // ---------------------------------------------------------
    // ADMIN - PUBLISH PRICE UPDATE
    // ---------------------------------------------------------

    suspend fun publishPrice(
        pluId: String,
        newPrice: Double,
        storeIds: List<String>,
        applyAll: Boolean,
        description: String?
    ): Result<String> =
        withContext(Dispatchers.IO) {

            val body =
                JSONObject().apply {

                    put(
                        "p_plu_id",
                        pluId
                    )

                    put(
                        "p_new_price",
                        newPrice
                    )

                    put(
                        "p_store_ids",
                        JSONArray(storeIds)
                    )

                    put(
                        "p_apply_all",
                        applyAll
                    )

                    put(
                        "p_description",
                        description
                            ?: JSONObject.NULL
                    )
                }

            request(
                method = "POST",
                path =
                    "/rest/v1/rpc/admin_publish_price_update",
                body = body,
                authenticated = true
            )
        }

    // ---------------------------------------------------------
    // MANAGER - GET PENDING PRICE UPDATES
    // ---------------------------------------------------------

    suspend fun getPendingPriceUpdates():
            Result<List<ManagerPriceUpdate>> =
        withContext(Dispatchers.IO) {

            request(
                method = "POST",
                path =
                    "/rest/v1/rpc/manager_get_price_updates",
                body = JSONObject(),
                authenticated = true
            ).mapCatching { raw ->

                val a = JSONArray(raw)

                buildList {

                    for (i in 0 until a.length()) {

                        val o =
                            a.getJSONObject(i)

                        add(
                            ManagerPriceUpdate(

                                targetId =
                                    o.getString(
                                        "target_id"
                                    ),

                                batchNumber =
                                    o.getInt(
                                        "batch_number"
                                    ),

                                pluId =
                                    o.getString(
                                        "plu_id"
                                    ),

                                pluNo =
                                    o.getInt(
                                        "plu_no"
                                    ),

                                oldPrice =
                                    o.optDouble(
                                        "old_price",
                                        0.0
                                    ),

                                newPrice =
                                    o.getDouble(
                                        "new_price"
                                    ),

                                storeId =
                                    o.getString(
                                        "store_id"
                                    ),

                                storeCode =
                                    o.getString(
                                        "store_code"
                                    ),

                                storeName =
                                    o.getString(
                                        "store_name"
                                    )
                            )
                        )
                    }
                }
            }
        }

    // ---------------------------------------------------------
    // MANAGER - APPLY ONE PRICE UPDATE
    // ---------------------------------------------------------

    suspend fun applyPriceUpdate(
        targetId: String
    ): Result<String> =
        withContext(Dispatchers.IO) {

            val body =
                JSONObject().apply {

                    put(
                        "p_target_id",
                        targetId
                    )
                }

            request(
                method = "POST",
                path =
                    "/rest/v1/rpc/manager_apply_price_update",
                body = body,
                authenticated = true
            )
        }


// ---------------------------------------------------------
// ADMIN - DAILY PRICE UPDATE REPORT
// ---------------------------------------------------------

suspend fun getDailyPriceReport(
    dateIso: String
): Result<DailyPriceReport> =
    withContext(Dispatchers.IO) {

        runCatching {

            val date = java.time.LocalDate.parse(dateIso)
            val zone = java.time.ZoneId.of("Asia/Kolkata")

            val start =
                date.atStartOfDay(zone)
                    .toInstant()
                    .toString()

            val end =
                date.plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toString()

            val batchRaw =
                request(
                    method = "GET",
                    path =
                        "/rest/v1/price_update_batches" +
                                "?created_at=gte.${url(start)}" +
                                "&created_at=lt.${url(end)}" +
                                "&select=id,batch_number,description,status,created_at" +
                                "&order=created_at.desc",
                    body = null,
                    authenticated = true
                ).getOrThrow()

            val batches = JSONArray(batchRaw)

            val stores =
                getStores().getOrThrow()
                    .associateBy { it.id }

            val plus =
                getPlus().getOrThrow()
                    .associateBy { it.id }

            val rows =
                mutableListOf<DailyPriceReportItem>()

            for (i in 0 until batches.length()) {

                val batch = batches.getJSONObject(i)
                val batchId = batch.getString("id")

                val itemRaw =
                    request(
                        method = "GET",
                        path =
                            "/rest/v1/price_update_items" +
                                    "?batch_id=eq.${url(batchId)}" +
                                    "&select=id,plu_id,old_price,new_price,created_at" +
                                    "&order=created_at.asc",
                        body = null,
                        authenticated = true
                    ).getOrThrow()

                val itemJson = JSONArray(itemRaw)

                for (j in 0 until itemJson.length()) {

                    val item = itemJson.getJSONObject(j)
                    val itemId = item.getString("id")
                    val plu = plus[item.getString("plu_id")]

                    val targetRaw =
                        request(
                            method = "GET",
                            path =
                                "/rest/v1/price_update_item_targets" +
                                        "?price_update_item_id=eq.${url(itemId)}" +
                                        "&select=id,store_id,status,downloaded_at,applied_at,error_message",
                            body = null,
                            authenticated = true
                        ).getOrThrow()

                    val targetJson = JSONArray(targetRaw)

                    if (targetJson.length() == 0) {

                        rows += DailyPriceReportItem(
                            batchNumber = batch.getInt("batch_number"),
                            pluNo = plu?.number ?: -1,
                            pluName = plu?.name ?: "Unknown PLU",
                            oldPrice = item.optDouble("old_price", 0.0),
                            newPrice = item.getDouble("new_price"),
                            storeCode = "—",
                            storeName = "No target",
                            status = batch.optString("status", "PUBLISHED"),
                            createdAt = item.optString("created_at", ""),
                            errorMessage = null
                        )

                    } else {

                        for (k in 0 until targetJson.length()) {

                            val target =
                                targetJson.getJSONObject(k)

                            val store =
                                stores[target.getString("store_id")]

                            rows += DailyPriceReportItem(
                                batchNumber =
                                    batch.getInt("batch_number"),
                                pluNo =
                                    plu?.number ?: -1,
                                pluName =
                                    plu?.name ?: "Unknown PLU",
                                oldPrice =
                                    item.optDouble(
                                        "old_price",
                                        0.0
                                    ),
                                newPrice =
                                    item.getDouble("new_price"),
                                storeCode =
                                    store?.code ?: "UNKNOWN",
                                storeName =
                                    store?.name ?: "Unknown Store",
                                status =
                                    target.optString(
                                        "status",
                                        "PENDING"
                                    ),
                                createdAt =
                                    target.optString(
                                        "applied_at",
                                        target.optString(
                                            "downloaded_at",
                                            item.optString(
                                                "created_at",
                                                ""
                                            )
                                        )
                                    ),
                                errorMessage =
                                    target.optString(
                                        "error_message"
                                    ).takeIf {
                                        it.isNotBlank()
                                    }
                            )
                        }
                    }
                }
            }

            DailyPriceReport(
                date = dateIso,
                batchCount = batches.length(),
                priceItemCount =
                    rows.map {
                        "${it.batchNumber}-${it.pluNo}"
                    }.toSet().size,
                storeCount =
                    rows.map { it.storeCode }
                        .filter { it != "—" }
                        .toSet().size,
                pendingCount =
                    rows.count {
                        it.status.equals(
                            "PENDING",
                            ignoreCase = true
                        )
                    },
                downloadedCount =
                    rows.count {
                        it.status.equals(
                            "DOWNLOADED",
                            ignoreCase = true
                        )
                    },
                appliedCount =
                    rows.count {
                        it.status.equals(
                            "APPLIED",
                            ignoreCase = true
                        )
                    },
                failedCount =
                    rows.count {
                        it.status.equals(
                            "FAILED",
                            ignoreCase = true
                        )
                    },
                items = rows
            )
        }
    }

    // ---------------------------------------------------------
    // ---------------------------------------------------------
    // ADMIN - GET CURRENT STORE PRICES
    // ---------------------------------------------------------

    suspend fun getStorePluPrices():
        Result<List<StorePluPriceRow>> =
        withContext(Dispatchers.IO) {

            request(
                method = "GET",
                path =
                    "/rest/v1/store_plu_prices" +
                            "?select=plu_id,store_id,unit_price" +
                            "&order=store_id.asc,plu_id.asc",
                body = null,
                authenticated = true
            ).mapCatching { raw ->

                val a = JSONArray(raw)

                buildList {
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)

                        add(
                            StorePluPriceRow(
                                pluId = o.getString("plu_id"),
                                storeId = o.getString("store_id"),
                                unitPrice = o.optDouble(
                                    "unit_price",
                                    0.0
                                )
                            )
                        )
                    }
                }
            }
        }

    // COMMON HTTP REQUEST
    // ---------------------------------------------------------

    private fun request(
        method: String,
        path: String,
        body: JSONObject?,
        authenticated: Boolean
    ): Result<String> = runCatching {

        val conn =
            (URL(
                SUPABASE_URL + path
            ).openConnection() as HttpURLConnection).apply {

                requestMethod =
                    method

                connectTimeout =
                    15000

                readTimeout =
                    20000

                setRequestProperty(
                    "apikey",
                    SUPABASE_ANON_KEY
                )

                setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                setRequestProperty(
                    "Accept",
                    "application/json"
                )

                if (authenticated) {

                    val token =
                        prefs.getString(
                            "access_token",
                            null
                        )
                            ?: error(
                                "Not signed in"
                            )

                    setRequestProperty(
                        "Authorization",
                        "Bearer $token"
                    )
                }

                doInput = true

                if (body != null) {
                    doOutput = true
                }
            }

        if (body != null) {

            conn.outputStream.use { output ->

                output.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }
        }

        val code =
            conn.responseCode

        val stream =
            if (code in 200..299)
                conn.inputStream
            else
                conn.errorStream

        val text =
            BufferedReader(
                InputStreamReader(
                    stream,
                    Charsets.UTF_8
                )
            ).use {
                it.readText()
            }

        conn.disconnect()

        if (code !in 200..299) {

            error(
                "Supabase HTTP $code: $text"
            )
        }

        text
    }

    private fun url(
        value: String
    ): String =
        URLEncoder.encode(
            value,
            "UTF-8"
        )
}

// -------------------------------------------------------------
// DATA CLASSES
// -------------------------------------------------------------

data class StorePluPriceRow(
    val pluId: String,
    val storeId: String,
    val unitPrice: Double
)

data class DailyPriceReport(
    val date: String,
    val batchCount: Int,
    val priceItemCount: Int,
    val storeCount: Int,
    val pendingCount: Int,
    val downloadedCount: Int,
    val appliedCount: Int,
    val failedCount: Int,
    val items: List<DailyPriceReportItem>
)

data class DailyPriceReportItem(
    val batchNumber: Int,
    val pluNo: Int,
    val pluName: String,
    val oldPrice: Double,
    val newPrice: Double,
    val storeCode: String,
    val storeName: String,
    val status: String,
    val createdAt: String,
    val errorMessage: String?
)



data class Profile(
    val id: String,
    val fullName: String,
    val role: String,
    val storeId: String?,
    val active: Boolean
)

data class StoreRow(
    val id: String,
    val code: String,
    val name: String
)

data class PluRow(
    val id: String,
    val number: Int,
    val name: String,
    val code: String,
    val uom: Int,
    val unitPrice: Double
)

data class ManagerPriceUpdate(
    val targetId: String,
    val batchNumber: Int,
    val pluId: String,
    val pluNo: Int,
    val oldPrice: Double,
    val newPrice: Double,
    val storeId: String,
    val storeCode: String,
    val storeName: String
)
