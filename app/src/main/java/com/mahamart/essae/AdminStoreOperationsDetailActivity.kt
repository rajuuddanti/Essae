package com.mahamart.essae

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahamart.essae.cloud.SupabaseAuth
import kotlinx.coroutines.launch
import com.mahamart.essae.util.TimeFormat

class AdminStoreOperationsDetailActivity : ComponentActivity() {
    private lateinit var auth: SupabaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = SupabaseAuth(applicationContext)
        val storeCode = intent.getStringExtra(EXTRA_STORE_CODE).orEmpty()

        setContent {
            MahaMartTheme {
                AdminStoreOperationsDetailScreen(
                    auth = auth,
                    storeCode = storeCode,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_STORE_CODE = "store_code"
    }
}

@Composable
private fun AdminStoreOperationsDetailScreen(
    auth: SupabaseAuth,
    storeCode: String,
    onClose: () -> Unit
) {
    var detail by remember { mutableStateOf<SupabaseAuth.StoreOperationsDetail?>(null) }
    var storeName by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf("") }
    var selectedHistoryTab by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        error = ""
        auth.getStoreOperationsDetail(storeCode)
            .onSuccess {
                detail = it
                storeName = it.storeName
                loading = false
            }
            .onFailure {
                error = it.message ?: "Could not load store details."
                loading = false
            }
    }

    LaunchedEffect(storeCode) {
        if (storeCode.isBlank()) {
            error = "Store code is missing."
            loading = false
        } else {
            refresh()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    storeCode,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    storeName.ifBlank { storeCode },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = { scope.launch { refresh() } }) {
                Text("REFRESH")
            }
        }

        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        if (loading && detail == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        }

        detail?.let { d ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val pendingCount = d.pendingAdminPushes.size + d.pendingChanges.size
                    SectionTitle(
                        if (pendingCount > 0) "PENDING PRICES • $pendingCount"
                        else "PENDING PRICES"
                    )
                }
                if (d.pendingAdminPushes.isEmpty() && d.pendingChanges.isEmpty()) {
                    item { EmptyText("No pending price changes.") }
                } else {
                    items(d.pendingAdminPushes) { row -> PendingAdminPushCard(row) }
                    items(d.pendingChanges) { row -> PriceChangeCard(row) }
                }

                item {
                    StoreHistoryTabs(
                        selectedTab = selectedHistoryTab,
                        onTabSelected = { selectedHistoryTab = it }
                    )
                }

                when (selectedHistoryTab) {
                    0 -> {
                        if (d.recentChanges.isEmpty()) {
                            item { EmptyText("No price-change history.") }
                        } else {
                            items(d.recentChanges.take(30)) { row -> PriceChangeCard(row) }
                        }
                    }
                    1 -> {
                        if (d.uploads.isEmpty()) {
                            item { EmptyText("No scale-upload history.") }
                        } else {
                            items(d.uploads) { row -> UploadCard(row) }
                        }
                    }
                    else -> {
                        if (d.pushes.isEmpty()) {
                            item { EmptyText("No Admin Push history.") }
                        } else {
                            items(d.pushes) { row -> PushCard(row) }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text("CLOSE")
        }
    }
}

@Composable
private fun StoreHistoryTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        "RECENT PRICE CHANGES",
        "UPLOAD HISTORY",
        "ADMIN PUSH HISTORY"
    )

    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        edgePadding = 0.dp
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun PriceChangeCard(row: SupabaseAuth.PriceChangeRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    row.pluNo.toString() + "  " + row.pluName.ifBlank { "Unnamed PLU" },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(row.status, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Text(
                "₹" + String.format("%.2f", row.oldPrice) + " → ₹" +
                    String.format("%.2f", row.newPrice) + "  •  " + row.source,
                style = MaterialTheme.typography.bodySmall
            )
            Text("Changed: " + TimeFormat.ist(row.changedAt), style = MaterialTheme.typography.bodySmall)
            if (row.uploadedAt != null) {
                Text(
                "Uploaded: " + (row.uploadedAt?.let { TimeFormat.ist(it) } ?: "Not uploaded"),
                style = MaterialTheme.typography.bodySmall
            )
            }
        }
    }
}

@Composable
private fun UploadCard(row: SupabaseAuth.ScaleUploadRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(row.status, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(row.pluCount.toString() + " PLUs", fontSize = 11.sp)
            }
            Text("Device: " + row.deviceId.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
            Text("Scale: " + row.scaleIp.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
            Text("Started: " + TimeFormat.ist(row.startedAt), style = MaterialTheme.typography.bodySmall)
            Text("Completed: " + TimeFormat.ist(row.completedAt), style = MaterialTheme.typography.bodySmall)
            if (row.errorMessage != null) {
                Text(
                    row.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PushCard(row: SupabaseAuth.PricePushRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        row.items.size == 1 -> "1 PLU • " + row.items.first().pluName
                        row.items.isNotEmpty() -> row.items.size.toString() + " PLUs • " +
                            row.items.first().pluName
                        else -> row.itemCount.toString() + " PLUs • " + row.mode
                    },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(row.status, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Text("By: " + row.createdByName.ifBlank { "Admin" }, style = MaterialTheme.typography.bodySmall)
            Text("Created: " + TimeFormat.ist(row.createdAt), style = MaterialTheme.typography.bodySmall)
            Text("Synced: " + TimeFormat.ist(row.syncedAt), style = MaterialTheme.typography.bodySmall)
            Text(
                "Uploaded: " + (row.uploadedAt?.let { TimeFormat.ist(it) } ?: "Not uploaded"),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PendingAdminPushCard(row: SupabaseAuth.PendingAdminPushItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    row.pluNo.toString() + "  " + row.pluName,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "PENDING",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Text(
                "Admin Push • ₹" + String.format("%.2f", row.newPrice),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Pushed: " + TimeFormat.ist(row.pushedAt),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Waiting for scale upload",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

