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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
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
    var newPrice by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    var stores by remember { mutableStateOf<List<SupabaseAuth.StoreOption>>(emptyList()) }
    var selectedStoreIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var applyToAll by rememberSaveable { mutableStateOf(true) }

    var loadingStores by rememberSaveable { mutableStateOf(true) }
    var publishing by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        auth.getActiveStores()
            .onSuccess {
                stores = it
                selectedStoreIds = it.map { store -> store.id }.toSet()
                loadingStores = false
            }
            .onFailure {
                error = it.message ?: "Could not load stores."
                loadingStores = false
            }
    }

    val filteredPlus = remember(allPlus, search) {
        val q = search.trim()
        if (q.isBlank()) {
            allPlus.take(80)
        } else {
            allPlus.filter {
                it.number.toString().contains(q, ignoreCase = true) ||
                    it.name.contains(q, ignoreCase = true) ||
                    it.code.contains(q, ignoreCase = true)
            }.take(80)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ADMIN PUSH",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("CLOSE") }
        }

        Text(
            "Publish a price change to all stores or selected stores.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search PLU / SKU / item") },
            singleLine = true
        )

        if (selectedPlu == null) {
            Text("Select an item", style = MaterialTheme.typography.labelLarge)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(170.dp)
            ) {
                items(filteredPlus, key = { it.number }) { plu ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPlu = plu
                                newPrice = ""
                                message = ""
                                error = ""
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(plu.name.ifBlank { "Unnamed PLU" })
                            Text(
                                "PLU " + plu.number + " • " + plu.code,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("₹" + String.format("%.2f", plu.unitPrice))
                    }
                }
            }
        } else {
            val plu = selectedPlu!!
            Text("Selected item", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(plu.name, fontWeight = FontWeight.SemiBold)
                    Text("PLU " + plu.number + " • " + plu.code)
                    Text(
                        "Current phone price: ₹" + String.format("%.2f", plu.unitPrice),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { selectedPlu = null }) { Text("CHANGE") }
            }
        }

        OutlinedTextField(
            value = newPrice,
            onValueChange = {
                if (it.matches(Regex("^\\d{0,8}(\\.\\d{0,2})?$"))) newPrice = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("New price") },
            prefix = { Text("₹") },
            singleLine = true
        )

        Text("Stores", style = MaterialTheme.typography.labelLarge)

        if (loadingStores) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = applyToAll,
                        role = Role.Checkbox,
                        onValueChange = {
                            applyToAll = it
                            if (it) selectedStoreIds = stores.map { store -> store.id }.toSet()
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = applyToAll, onCheckedChange = null)
                Spacer(Modifier.width(4.dp))
                Text("ALL STORES (" + stores.size + ")")
            }

            if (!applyToAll) {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    items(stores, key = { it.id }) { store ->
                        val checked = selectedStoreIds.contains(store.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        selectedStoreIds =
                                            if (it) selectedStoreIds + store.id
                                            else selectedStoreIds - store.id
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.width(4.dp))
                            Text(store.code + " — " + store.name)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note (optional)") },
            singleLine = true
        )

        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(2.dp))

        Button(
            onClick = { confirm = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !publishing &&
                selectedPlu != null &&
                newPrice.toDoubleOrNull() != null &&
                (applyToAll || selectedStoreIds.isNotEmpty())
        ) {
            Text("PUBLISH PRICE")
        }

        if (publishing) {
            CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }

    if (confirm && selectedPlu != null) {
        val targetText = if (applyToAll) "ALL ACTIVE STORES"
        else selectedStoreIds.size.toString() + " SELECTED STORE(S)"

        AlertDialog(
            onDismissRequest = { if (!publishing) confirm = false },
            title = { Text("Confirm Admin Push") },
            text = {
                Text(
                    selectedPlu!!.name + "\n" +
                        "PLU " + selectedPlu!!.number + "\n" +
                        "₹" + String.format("%.2f", selectedPlu!!.unitPrice) +
                        " → ₹" + String.format("%.2f", newPrice.toDouble()) + "\n" +
                        "Target: " + targetText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirm = false
                        publishing = true
                        error = ""
                        message = ""

                        val item = SupabaseAuth.AdminPriceItem(
                            pluNo = selectedPlu!!.number,
                            pluName = selectedPlu!!.name,
                            newPrice = newPrice.toDouble()
                        )

                        scope.launch {
                            auth.publishAdminPriceUpdate(
                                applyToAll = applyToAll,
                                storeIds = selectedStoreIds.toList(),
                                items = listOf(item),
                                note = note
                            ).onSuccess { updateId ->
                                message = "Published successfully. Update ID: " + updateId
                            }.onFailure {
                                error = it.message ?: "Admin Push failed."
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
