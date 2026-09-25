package com.mahamart.essae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahamart.essae.cloud.SupabaseAuth

class AdminStoreDeviceMappingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MahaMartTheme {
                AdminStoreDeviceMappingScreen(
                    auth = SupabaseAuth(applicationContext),
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun AdminStoreDeviceMappingScreen(
    auth: SupabaseAuth,
    onBack: () -> Unit
) {
    var devices by remember { mutableStateOf<List<SupabaseAuth.StoreDevice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    fun load() {
        loading = true
        error = ""
    }

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        auth.getRegisteredStoreDevices()
            .onSuccess {
                devices = it
                loading = false
            }
            .onFailure {
                error = it.message ?: "Could not load registered devices."
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
            "Store / Device Mapping",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Registered physical Android devices mapped to their permanent store.",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { load() },
                modifier = Modifier.weight(1f),
                enabled = !loading
            ) {
                Text("REFRESH")
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                enabled = !loading
            ) {
                Text("BACK")
            }
        }

        when {
            loading -> {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
            }

            error.isNotBlank() -> {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error
                )
            }

            devices.isEmpty() -> {
                Text("No store devices are registered yet.")
            }

            else -> {
                Text(
                    devices.size.toString() + " registered device" +
                        if (devices.size == 1) "" else "s",
                    style = MaterialTheme.typography.labelLarge
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = devices,
                        key = { it.id }
                    ) { device ->
                        DeviceCard(device)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: SupabaseAuth.StoreDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                device.storeCode + " — " + device.storeName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (device.active) "ACTIVE" else "INACTIVE",
                color = if (device.active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Bold
            )

            Text("DEVICE: " + device.deviceName.ifBlank { "Android Device" })
            Text("DEVICE ID: " + device.deviceId)
            Text("IP: " + device.deviceIp.ifBlank { "Not available" })
            Text("REGISTERED: " + device.registeredAt)
            Text("LAST SEEN: " + (device.lastSeenAt ?: "Not reported"))
        }
    }
}
