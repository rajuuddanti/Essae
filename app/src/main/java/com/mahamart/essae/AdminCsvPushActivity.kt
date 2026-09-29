package com.mahamart.essae

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahamart.essae.cloud.SupabaseAuth
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class AdminCsvPushActivity : ComponentActivity() {
    private lateinit var auth: SupabaseAuth

    private val openCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleCsv(it) }
    }

    private var onCsvParsed: ((List<SupabaseAuth.AdminPriceItem>, String) -> Unit)? = null
    private var onCsvError: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuth(applicationContext)
        setContent {
            MahaMartTheme {
                AdminCsvPushScreen(
                    auth = auth,
                    onClose = { finish() },
                    onPickCsv = {
                        openCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                    }
                )
            }
        }
    }

    private fun handleCsv(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { parseCsv(it) }
            } ?: error("Could not open the CSV file.")
        }.onSuccess { (parsed, name) ->
            onCsvParsed?.invoke(parsed, name)
        }.onFailure {
            onCsvError?.invoke(it.message ?: "Could not read CSV.")
        }
    }

    @Composable
    private fun AdminCsvPushScreen(
        auth: SupabaseAuth,
        onClose: () -> Unit,
        onPickCsv: () -> Unit
    ) {
        var items by remember { mutableStateOf<List<SupabaseAuth.AdminPriceItem>>(emptyList()) }
        var fileName by rememberSaveable { mutableStateOf("") }
        var error by rememberSaveable { mutableStateOf("") }
        var message by rememberSaveable { mutableStateOf("") }
        var stores by remember { mutableStateOf<List<SupabaseAuth.StoreOption>>(emptyList()) }
        var selectedStores by remember { mutableStateOf<Set<String>>(emptySet()) }
        var loadingStores by remember { mutableStateOf(true) }
        var publishing by rememberSaveable { mutableStateOf(false) }
        var confirm by rememberSaveable { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        onCsvParsed = { parsed, name ->
            items = parsed
            fileName = name
            error = ""
            message = ""
        }
        onCsvError = {
            error = it
            message = ""
        }

        LaunchedEffect(Unit) {
            auth.getActiveStores()
                .onSuccess { stores = it; loadingStores = false }
                .onFailure {
                    error = it.message ?: "Could not load stores."
                    loadingStores = false
                }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("ADMIN CSV PUSH", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("CLOSE") }
            }

            Text(
                "Import a price CSV and remotely push it to one, selected, or all stores. " +
                    "Existing Store CSV Import is unchanged.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedButton(onClick = onPickCsv, modifier = Modifier.fillMaxWidth(), enabled = !publishing) {
                Text(if (fileName.isBlank()) "SELECT CSV" else "REPLACE CSV")
            }

            if (fileName.isNotBlank()) {
                Text(fileName + " • " + items.size + " SKUs ready")
            }

            if (items.isNotEmpty()) {
                Text("CSV PREVIEW", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                    items(items, key = { it.pluNo }) { item ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (item.pluCode.isBlank()) {
                                    item.pluNo.toString() + "  " + item.pluName
                                } else {
                                    item.pluNo.toString() + "  " + item.pluCode + "  " + item.pluName
                                },
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text("₹" + String.format("%.2f", item.newPrice))
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("SELECT STORES", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(selectedStores.size.toString() + "/" + stores.size)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { selectedStores = stores.map { it.id }.toSet() },
                    modifier = Modifier.weight(1f),
                    enabled = stores.isNotEmpty() && !publishing
                ) { Text("SELECT ALL") }

                OutlinedButton(
                    onClick = { selectedStores = emptySet() },
                    modifier = Modifier.weight(1f),
                    enabled = !publishing
                ) { Text("CLEAR") }
            }

            if (loadingStores) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(stores, key = { it.id }) { store ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = store.id in selectedStores,
                                onCheckedChange = { checked ->
                                    selectedStores = if (checked) selectedStores + store.id
                                    else selectedStores - store.id
                                },
                                enabled = !publishing
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(store.code)
                                Text(store.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)

            Button(
                onClick = { confirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !publishing && items.isNotEmpty() && selectedStores.isNotEmpty()
            ) {
                Text("PUSH " + items.size + " SKUS TO " + selectedStores.size + " STORE(S)")
            }

            if (publishing) CircularProgressIndicator(strokeWidth = 2.dp)
        }

        if (confirm) {
            AlertDialog(
                onDismissRequest = { if (!publishing) confirm = false },
                title = { Text("Confirm Admin CSV Push") },
                text = {
                    Text(
                        items.size.toString() + " SKU(s)\n" +
                            selectedStores.size + " store(s) selected\n\n" +
                            "This creates an Admin Push. Store prices remain pending until " +
                            "the physical Essae scale upload succeeds."
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
                                auth.publishAdminPriceUpdate(
                                    applyToAll = false,
                                    storeIds = selectedStores.toList(),
                                    items = items,
                                    note = "Admin CSV Push"
                                ).onSuccess { updateId ->
                                    auth.sendAdminPushNotification(updateId)
                                    message = "Published " + items.size + " SKU(s) to " +
                                        selectedStores.size + " store(s)."
                                    publishing = false
                                }.onFailure {
                                    error = it.message ?: "Admin CSV Push failed."
                                    publishing = false
                                }
                            }
                        },
                        enabled = !publishing
                    ) { Text("PUBLISH") }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = false }, enabled = !publishing) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }

    private fun parseCsv(reader: BufferedReader): Pair<List<SupabaseAuth.AdminPriceItem>, String> {
        val lines = reader.readLines().filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "CSV is empty." }

        val rows = lines.map { parseCsvLine(it) }
        val header = rows.first().map { normalizeHeader(it) }

        val pluIndex = header.indexOfFirst { it in setOf("PLU_NO", "PLUNO", "PLU", "NUMBER") }
        val nameIndex = header.indexOfFirst { it in setOf("PLU_NAME", "PLUNAME", "NAME", "PRODUCT_NAME", "PRODUCT") }
        val codeIndex = header.indexOfFirst { it in setOf("PLU_CODE", "PLUCODE", "CODE", "BARCODE") }
        val priceIndex = header.indexOfFirst { it in setOf("PRICE", "NEW_PRICE", "UNIT_PRICE", "UNITPRICE") }

        require(pluIndex >= 0) { "CSV needs a PLU_NO column." }
        require(priceIndex >= 0) { "CSV needs a PRICE column." }

        val result = mutableListOf<SupabaseAuth.AdminPriceItem>()
        val seen = mutableSetOf<Int>()

        rows.drop(1).forEachIndexed { offset, row ->
            val lineNo = offset + 2
            val plu = row.getOrNull(pluIndex)?.trim()?.toIntOrNull()
                ?: error("Invalid PLU number at CSV line " + lineNo + ".")
            val price = row.getOrNull(priceIndex)?.trim()?.replace("₹", "")?.toDoubleOrNull()
                ?: error("Invalid price at CSV line " + lineNo + ".")
            require(price >= 0.0) { "Negative price at CSV line " + lineNo + "." }
            require(seen.add(plu)) { "Duplicate PLU " + plu + " at CSV line " + lineNo + "." }

            val name = if (nameIndex >= 0) row.getOrNull(nameIndex)?.trim().orEmpty() else ""
            val code = if (codeIndex >= 0) row.getOrNull(codeIndex)?.trim().orEmpty() else ""
            result += SupabaseAuth.AdminPriceItem(
                pluNo = plu,
                pluName = name,
                newPrice = price,
                pluCode = code
            )
        }

        require(result.isNotEmpty()) { "CSV contains no price rows." }
        return result to "selected CSV"
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    out += current.toString()
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        require(!quoted) { "Unclosed quote in CSV." }
        out += current.toString()
        return out
    }

    private fun normalizeHeader(value: String): String =
        value.trim().removePrefix("\uFEFF").uppercase().replace(" ", "_")
}
