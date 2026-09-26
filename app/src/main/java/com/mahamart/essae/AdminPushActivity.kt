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
    var newPrices by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

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
        newPrices = emptyMap()
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
        newPrices = emptyMap()
        message = ""
        error = ""
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
                            "₹" + String.format("%.2f", plu.unitPrice),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            val plu = selectedPlu!!

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
                "Master price: ₹" + String.format("%.2f", plu.unitPrice),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "STORE",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "RUNNING",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(78.dp)
                )
                Text(
                    "NEW",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(86.dp)
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
                                    "LC: " + formatIstLastChanged(store.lastUploadedAt),
                                    fontSize = 10.sp
                                )
                            }

                            Text(
                                if (store.currentPrice == null)
                                    "—"
                                else
                                    "₹" + String.format("%.2f", store.currentPrice),
                                modifier = Modifier.width(58.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = newPrices[store.storeId] ?: "",
                                onValueChange = { value ->
                                    if (value.matches(Regex("^\\d{0,8}(\\.\\d{0,2})?$"))) {
                                        newPrices = newPrices + (store.storeId to value)
                                    }
                                },
                                modifier = Modifier
                                    .width(62.dp)
                                    .height(40.dp),
                                label = { Text("₹", fontSize = 9.sp) },
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp
                                ),
                                singleLine = true,
                                enabled = true
                            )
                        }
                    }
                }
            }

            val readyCount = selectedStoreIds.count {
                !newPrices[it].isNullOrBlank() && newPrices[it]?.toDoubleOrNull() != null
            }

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
                    readyCount == selectedStoreIds.size
            ) {
                Text("PUSH TO " + selectedStoreIds.size + " STORE(S)")
            }

            if (publishing) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }
    }

    if (confirm && selectedPlu != null) {
        val grouped = selectedStoreIds
            .mapNotNull { id ->
                val price = newPrices[id]?.toDoubleOrNull() ?: return@mapNotNull null
                id to price
            }
            .groupBy({ it.second }, { it.first })

        AlertDialog(
            onDismissRequest = { if (!publishing) confirm = false },
            title = { Text("Confirm Admin Push") },
            text = {
                Text(
                    selectedPlu!!.name + "\n" +
                        grouped.size + " price group(s) • " +
                        selectedStoreIds.size + " store(s)\n\n" +
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
                                    "Published $successCount price group(s) for " +
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

