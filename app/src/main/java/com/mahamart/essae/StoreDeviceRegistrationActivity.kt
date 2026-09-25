package com.mahamart.essae

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahamart.essae.cloud.StoreDeviceRegistrationClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

class StoreDeviceRegistrationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MahaMartTheme {
                StoreDeviceRegistrationScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

private const val PREFS = "store_device_registration"
private const val KEY_REGISTERED = "registered"
private const val KEY_STORE_CODE = "store_code"
private const val KEY_STORE_NAME = "store_name"
private const val KEY_DEVICE_ID = "device_id"
private const val KEY_DEVICE_NAME = "device_name"
private const val KEY_DEVICE_IP = "device_ip"
private const val KEY_REGISTERED_AT = "registered_at"

@Composable
private fun StoreDeviceRegistrationScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }

    var registered by rememberSaveable {
        mutableStateOf(prefs.getBoolean(KEY_REGISTERED, false))
    }
    var storeCode by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_STORE_CODE, "") ?: "")
    }
    var storeName by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_STORE_NAME, "") ?: "")
    }
    var deviceIp by rememberSaveable {
        mutableStateOf(prefs.getString(KEY_DEVICE_IP, "") ?: "")
    }
    var code by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var registering by rememberSaveable { mutableStateOf(false) }

    val deviceId = remember {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
    }

    val deviceName = remember {
        Build.MODEL.ifBlank { "Android Device" }
    }

    LaunchedEffect(Unit) {
        if (!registered) {
            deviceIp = withContext(Dispatchers.IO) {
                findLocalIpv4()
            }
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
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        if (registered) {
            Text(
                "This phone is already registered.",
                style = MaterialTheme.typography.bodyMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("STORE", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "$storeCode — $storeName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("DEVICE", style = MaterialTheme.typography.labelSmall)
                    Text(deviceName)
                    Spacer(Modifier.height(6.dp))
                    Text("DEVICE ID", style = MaterialTheme.typography.labelSmall)
                    Text(deviceId)
                    Spacer(Modifier.height(6.dp))
                    Text("IP", style = MaterialTheme.typography.labelSmall)
                    Text(if (deviceIp.isBlank()) "Not available" else deviceIp)
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK")
            }

            return@Column
        }

        Text(
            "Enter the one-time code generated by Admin for this physical phone.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it
                status = ""
            },
            label = { Text("Registration Code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Device: $deviceName",
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            "Device ID: $deviceId",
            style = MaterialTheme.typography.bodySmall
        )

        if (deviceIp.isNotBlank()) {
            Text(
                "Current IP: $deviceIp",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Button(
            onClick = {
                registering = true
                status = "Registering device..."

                kotlinx.coroutines.CoroutineScope(
                    androidx.compose.runtime.rememberCoroutineScope().coroutineContext
                ).launch {
                    StoreDeviceRegistrationClient(context)
                        .registerDevice(
                            registrationCode = code,
                            deviceId = deviceId,
                            deviceName = deviceName,
                            deviceIp = deviceIp
                        )
                        .onSuccess { result ->
                            prefs.edit()
                                .putBoolean(KEY_REGISTERED, true)
                                .putString(KEY_STORE_CODE, result.storeCode)
                                .putString(KEY_STORE_NAME, result.storeName)
                                .putString(KEY_DEVICE_ID, result.deviceId)
                                .putString(KEY_DEVICE_NAME, result.deviceName)
                                .putString(KEY_DEVICE_IP, result.deviceIp)
                                .putString(KEY_REGISTERED_AT, result.registeredAt)
                                .apply()

                            storeCode = result.storeCode
                            storeName = result.storeName
                            deviceIp = result.deviceIp
                            registered = true
                            registering = false
                            status = "Device registered successfully."
                        }
                        .onFailure {
                            registering = false
                            status = it.message ?: "Device registration failed."
                        }
                }
            },
            enabled = code.trim().isNotBlank() && !registering,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (registering) "REGISTERING..." else "REGISTER DEVICE")
        }

        if (status.isNotBlank()) {
            Text(
                status,
                color = if (registered) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
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
}

private fun findLocalIpv4(): String {
    return runCatching {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull {
                !it.isLoopbackAddress &&
                    it is Inet4Address &&
                    !it.hostAddress.isNullOrBlank()
            }
            ?.hostAddress
            .orEmpty()
    }.getOrDefault("")
}
