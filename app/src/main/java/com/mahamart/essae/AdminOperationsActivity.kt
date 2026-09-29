package com.mahamart.essae

import android.os.Bundle
import android.content.Intent
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahamart.essae.cloud.SupabaseAuth
import com.mahamart.essae.util.TimeFormat

class AdminOperationsActivity : ComponentActivity() {
    private lateinit var auth: SupabaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuth(applicationContext)

        setContent {
            MahaMartTheme {
                AdminOperationsScreen(auth = auth, onClose = { finish() })
            }
        }
    }
}

@Composable
private fun AdminOperationsScreen(
    auth: SupabaseAuth,
    onClose: () -> Unit
) {
    var rows by remember { mutableStateOf<List<SupabaseAuth.StoreOperations>>(emptyList()) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun refresh() {
        loading = true
        error = ""
        auth.getOperationsSnapshot()
            .onSuccess {
                rows = it
                loading = false
            }
            .onFailure {
                error = it.message ?: "Could not load operations."
                loading = false
            }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val pendingStores = rows.count { it.pendingCount > 0 }
    val registeredStores = rows.count { it.deviceCount > 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "STORE OPERATIONS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Live operational view from existing device, price-change and upload records.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(
                onClick = { scope.launch { refresh() } }
            ) {
                Text("REFRESH")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                title = "STORES",
                value = rows.size.toString(),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "REGISTERED",
                value = registeredStores.toString(),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "PENDING",
                value = pendingStores.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (loading && rows.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows, key = { it.storeCode }) { row ->
                    StoreOperationsCard(row) {
                        context.startActivity(
                            Intent(context, AdminStoreOperationsDetailActivity::class.java)
                                .putExtra(
                                    AdminStoreOperationsDetailActivity.EXTRA_STORE_CODE,
                                    row.storeCode
                                )
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CLOSE")
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StoreOperationsCard(
    row: SupabaseAuth.StoreOperations,
    onOpen: () -> Unit
) {
    val hasPending = row.pendingCount > 0
    val deviceText = when {
        row.deviceCount == 0 -> "NO DEVICE"
        row.activeDeviceCount == 1 -> "1 ACTIVE DEVICE"
        else -> "${row.activeDeviceCount} ACTIVE DEVICES"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${row.storeCode}  ${row.storeName}",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        deviceText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Text(
                    if (hasPending) "${row.pendingCount} PENDING" else "NO PENDING",
                    color = if (hasPending)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Text(
                "Last seen: ${TimeFormat.ist(row.lastSeenAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Last completed upload: ${TimeFormat.ist(row.lastUploadAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OPEN STORE")
            }
        }
    }
}

