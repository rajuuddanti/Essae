package com.mahamart.essae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.mahamart.essae.cloud.SupabaseAuth
import com.mahamart.essae.data.AppDatabase
import com.mahamart.essae.data.Plu
import kotlinx.coroutines.launch

class AdminPushActivity : ComponentActivity() {
    private lateinit var auth: SupabaseAuth
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuth(applicationContext)
        db = AppDatabase.create(applicationContext)

        setContent {
            MahaMartTheme {
                AdminPushScreen(auth, db) { finish() }
            }
        }
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}

@Composable
private fun AdminPushScreen(
    auth: SupabaseAuth,
    db: AppDatabase,
    onClose: () -> Unit
) {
    val allPlus by db.pluDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var search by rememberSaveable { mutableStateOf("") }
    var selectedPlu by remember { mutableStateOf<Plu?>(null) }
    var storePrices by remember { mutableStateOf<List<SupabaseAuth.StorePluPrice>>(emptyList()) }
    var selectedStoreIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var newPrice by rememberSaveable { mutableStateOf("") }

    var loadingStores by rememberSaveable { mutableStateOf(false) }
    var loadingPrices by rememberSaveable { mutableStateOf(false) }
    var publishing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }

    val filteredPlus = remember(allPlus, search) {
        val q = search.trim()
        if (q.isBlank()) {
            allPlus
        } else {
            allPlus.filter {
                it.number.toString().contains(q, ignoreCase = true) ||
                    it.name.contains(q, ignoreCase = true) ||
                    it.code.contains(q, ignoreCase = true)
            }
        }
    }

    fun selectPlu(plu: Plu) {
        selectedPlu = plu
        selectedStoreIds = emptySet()
        newPrice = ""
        message = ""
        error = ""
        loadingPrices = true

        scope.launch {
            auth.getStorePluPrices(plu.number)
                .onSuccess {
                    storePrices = it
                    loadingPrices = false
                }
                .onFailure {
                    storePrices = emptyList()
                    error = it.message ?: "Could not load store prices."
                    loadingPrices = false
                }
        }
    }

    fun goBackToMaster() {
        selectedPlu = null
        storePrices = emptyList()
        selectedStoreIds = emptySet()
        newPrice = ""
        message = ""
        error = ""
    }

    // Keep the Admin detail view in sync with the store's cloud current-price
    // record. Admin Push creates a pending update; the store receives it first.
    LaunchedEffect(selectedPlu?.number) {
        if (selectedPlu == null) return@LaunchedEffect

        while (true) {
            auth.getStorePluPrices(selectedPlu!!.number)
                .onSuccess { storePrices = it }
            kotlinx.coroutines.delay(5000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectedPlu == null) "ADMIN PUSH" else "ADMIN PUSH",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("CLOSE") }
        }

        if (selectedPlu == null) {
            Text(
                "Tap a SKU to see the running price in every store.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search SKU / Name / Barcode") },
                singleLine = true
            )

            Text(
                "PLU MASTER",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(filteredPlus, key = { it.number }) { plu ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectPlu(plu) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                plu.number.toString() + "  " +
                                    plu.name.ifBlank { "Unnamed PLU" },
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                plu.code + " • " +
                                    if (plu.uom == 0) "WEIGH" else "PCS",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Tap to view store prices",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "STORE PRICES →",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            val plu = selectedPlu!!
            val repeatedMasterPrices = storePrices
                .mapNotNull { it.currentPrice }
                .groupingBy { it }
                .eachCount()
                .filterValues { it >= 2 }

            // A Master Price exists only when one price is shared by
            // at least two stores. If multiple prices tie, there is
            // no single unambiguous Master Price.
            val masterPrice =
                if (repeatedMasterPrices.size == 1) {
                    repeatedMasterPrices.keys.first()
                } else {
                    null
                }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { goBackToMaster() }) {
                    Text("← PLU LIST")
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    plu.name,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                "PLU " + plu.number + " • " + plu.code,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "Master price: " + (
                    masterPrice?.let { "₹" + String.format("%.2f", it) }
                        ?: "—"
                    ),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NEW PRICE",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = newPrice,
                    onValueChange = { value ->
                        if (value.matches(Regex("^\\d{0,8}(\\.\\d{0,2})?$"))) {
                            newPrice = value
                        }
                    },
                    modifier = Modifier
                        .width(130.dp)
                        .height(56.dp),
                    label = { Text("₹", fontSize = 10.sp) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp
                    ),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedStoreIds = storePrices.map { it.storeId }.toSet()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("SELECT ALL")
                }
                OutlinedButton(
                    onClick = {
                        selectedStoreIds = emptySet()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CLEAR")
                }
            }

            if (loadingPrices) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(storePrices, key = { it.storeId }) { store ->
                        val checked = selectedStoreIds.contains(store.storeId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    selectedStoreIds =
                                        if (it) selectedStoreIds + store.storeId
                                        else selectedStoreIds - store.storeId
                                }
                            )

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    store.storeName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    buildLastChangedText(
                                        store.lastUploadedAt,
                                        store.pendingPushedAt,
                                        store.pendingPushedPrice,
                                        store.lastUploadSource
                                    ),
                                    fontSize = 10.sp
                                )
                            }

                            Text(
                                if (store.currentPrice == null)
                                    "—"
                                else
                                    "₹" + String.format("%.2f", store.currentPrice),
                                modifier = Modifier.width(72.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            val readyToPush = newPrice.isNotBlank() && newPrice.toDoubleOrNull() != null

            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !publishing &&
                    selectedStoreIds.isNotEmpty() &&
                    readyToPush
            ) {
                Text("PUSH TO " + selectedStoreIds.size + " STORE(S)")
            }

            if (publishing) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
    }

    if (confirm && selectedPlu != null) {
        val pushPrice = newPrice.toDoubleOrNull() ?: 0.0
        val grouped = mapOf(pushPrice to selectedStoreIds.toList())

        AlertDialog(
            onDismissRequest = { if (!publishing) confirm = false },
            title = { Text("Confirm Admin Push") },
            text = {
                Text(
                    selectedPlu!!.name + "\n" +
                        "New price: ₹" + String.format("%.2f", pushPrice) + "\n" +
                        selectedStoreIds.size + " store(s) selected\n\n" +
                        "Only selected stores will receive these pending updates. " +
                        "The physical scales are not changed by this action."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = false
                        publishing = true
                        error = ""
                        message = ""

                        scope.launch {
                            var firstError: String? = null
                            var successCount = 0

                            for ((price, storeIds) in grouped) {
                                val result = auth.publishAdminPriceUpdate(
                                    applyToAll = false,
                                    storeIds = storeIds,
                                    items = listOf(
                                        SupabaseAuth.AdminPriceItem(
                                            pluNo = selectedPlu!!.number,
                                            pluName = selectedPlu!!.name,
                                            newPrice = price
                                        )
                                    ),
                                    note = "Admin Push from store-price view"
                                )

                                result.onSuccess {
                                    successCount++
                                }.onFailure {
                                    if (firstError == null) {
                                        firstError = it.message ?: "Admin Push failed."
                                    }
                                }
                            }

                            if (firstError != null) {
                                error = firstError!!
                                message = if (successCount > 0)
                                    "$successCount price group(s) published before the error."
                                else
                                    ""
                            } else {
                                message =
                                    "Published ₹" + String.format("%.2f", pushPrice) + " to " +
                                        selectedStoreIds.size + " store(s)."
                            }

                            publishing = false
                        }
                    },
                    enabled = !publishing
                ) {
                    Text("PUBLISH")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirm = false },
                    enabled = !publishing
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
}

private fun buildLastChangedText(
    lastUploadedAt: String?,
    pendingPushedAt: String?,
    pendingPushedPrice: Double?,
    lastUploadSource: String?
): String {
    val confirmed = formatIstLastChanged(lastUploadedAt)

    if (pendingPushedAt.isNullOrBlank()) {
        return if (lastUploadSource.equals("MANUAL", ignoreCase = true)) {
            "LC: $confirmed M"
        } else {
            "LC: $confirmed"
        }
    }

    val pushedDate = formatIstDayMonth(pendingPushedAt)
    val pendingPrice = pendingPushedPrice?.let {
        String.format("%.0f", it)
    }

    return if (pushedDate == null) {
        if (pendingPrice == null) {
            "LC: $confirmed*"
        } else {
            "LC: $confirmed*($pendingPrice)"
        }
    } else {
        if (pendingPrice == null) {
            "LC: $confirmed ($pushedDate)*"
        } else {
            "LC: $confirmed ($pushedDate)*($pendingPrice)"
        }
    }
}

private fun formatIstDayMonth(value: String?): String? {
    if (value.isNullOrBlank()) return null

    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .format(DateTimeFormatter.ofPattern("dd-MM"))
    }.getOrNull()
}

private fun formatIstLastChanged(value: String?): String {
    if (value.isNullOrBlank()) return "—"

    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.of("Asia/Kolkata"))
            .format(
                DateTimeFormatter.ofPattern(
                    "dd-MM-yyyy, HH:mm"
                )
            )
    }.getOrElse {
        "—"
    }
}

