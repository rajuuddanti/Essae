package com.mahamart.essae

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahamart.essae.cloud.SupabaseAuth

@Composable
fun AdminDeviceRegistration(
    auth: SupabaseAuth,
    onBack: () -> Unit
) {
    var stores by remember { mutableStateOf<List<SupabaseAuth.StoreOption>>(emptyList()) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var generating by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var selectedStore by remember { mutableStateOf<SupabaseAuth.StoreOption?>(null) }
    var showStorePicker by rememberSaveable { mutableStateOf(true) }
    var registrationCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        auth.getActiveStores()
            .onSuccess {
                stores = it
                loading = false
            }
            .onFailure {
                error = it.message ?: "Could not load stores."
                loading = false
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Store Device Registration",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Select a store and generate a one-time code for one physical Android device.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (loading) {
            Text("Loading stores...")
        } else if (stores.isEmpty()) {
            Text("No active stores found.")
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showStorePicker = true },
                colors = CardDefaults.cardColors()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        selectedStore?.let { "${it.code} — ${it.name}" }
                            ?: "Tap to select a store"
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Store",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (selectedStore != null) {
                Button(
                    onClick = {
                        generating = true
                        error = ""
                        registrationCode = ""
                    },
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (generating) "GENERATING..." else "GENERATE REGISTRATION CODE")
                }
            }

            if (registrationCode.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "REGISTRATION CODE",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            registrationCode,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Valid for 60 minutes and usable once.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.weight(1f))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BACK")
        }
    }

    if (showStorePicker && stores.isNotEmpty()) {
        StorePickerDialog(
            stores = stores,
            onSelect = {
                selectedStore = it
                registrationCode = ""
                error = ""
                showStorePicker = false
            },
            onDismiss = {
                showStorePicker = false
                if (selectedStore == null) {
                    selectedStore = stores.first()
                }
            }
        )
    }

    if (generating && selectedStore != null) {
        LaunchedEffect(selectedStore?.code, generating) {
            auth.generateStoreDeviceCode(
                selectedStore!!.code,
                60
            ).onSuccess {
                registrationCode = it
                generating = false
            }.onFailure {
                error = it.message ?: "Could not generate registration code."
                generating = false
            }
        }
    }
}

@Composable
private fun StorePickerDialog(
    stores: List<SupabaseAuth.StoreOption>,
    onSelect: (SupabaseAuth.StoreOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Store") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(stores, key = { it.code }) { store ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(store) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                "${store.code}  ${store.name}"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
