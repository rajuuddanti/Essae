@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.mahamart.essae

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahamart.essae.data.AppDatabase
import com.mahamart.essae.data.Plu
import com.mahamart.essae.data.PluDao
import com.mahamart.essae.data.PriceAudit
import com.mahamart.essae.network.EssaeTransport
import com.mahamart.essae.network.SupabaseRest
import com.mahamart.essae.network.SupabaseRest.PushItem
import com.mahamart.essae.network.SupabaseRest.PriceItem
import com.mahamart.essae.util.CsvImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CLEAR_PLU_PIN = "3331"
private const val PREFS_NAME = "scale_manager_settings"
private const val ADMIN_TOKEN = "admin_access_token"
private const val ADMIN_EMAIL = "admin_email"
private const val ADMIN_USER_ID = "admin_user_id"

private enum class ReportTab { CURRENT, CHANGES, UPLOADS, PUSH }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.create(applicationContext)
        setContent { EssaeApp(db) }
    }
}

class StorePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("store_settings", Context.MODE_PRIVATE)
    var scaleIp: String
        get() = prefs.getString("scaleIp", "192.168.100.200") ?: "192.168.100.200"
        set(value) { prefs.edit().putString("scaleIp", value).apply() }
    var scalePort: String
        get() = prefs.getString("scalePort", "4321") ?: "4321"
        set(value) { prefs.edit().putString("scalePort", value).apply() }
    var adminToken: String
        get() = prefs.getString(ADMIN_TOKEN, "") ?: ""
        set(value) { prefs.edit().putString(ADMIN_TOKEN, value).apply() }
    var adminEmail: String
        get() = prefs.getString(ADMIN_EMAIL, "") ?: ""
        set(value) { prefs.edit().putString(ADMIN_EMAIL, value).apply() }
    var adminUserId: String
        get() = prefs.getString(ADMIN_USER_ID, "") ?: ""
        set(value) { prefs.edit().putString(ADMIN_USER_ID, value).apply() }
    fun clearAdmin() = prefs.edit().remove(ADMIN_TOKEN).remove(ADMIN_EMAIL).remove(ADMIN_USER_ID).apply()
}

private fun deviceId(context: Context): String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty().ifBlank { "unknown-device" }

private fun deviceIp(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val link: LinkProperties = cm?.activeNetwork?.let { cm.getLinkProperties(it) } ?: return "0.0.0.0"
    return link.linkAddresses.firstOrNull { it.address.hostAddress?.contains(':') == false }
        ?.address?.hostAddress ?: "0.0.0.0"
}

class MainVm(
    private val context: Context,
    private val dao: PluDao,
    private val auditDao: com.mahamart.essae.data.AuditDao,
    private val prefs: StorePrefs
) : ViewModel() {
    val plus = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingAudits = auditDao.observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val auditHistory = auditDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var host by mutableStateOf(prefs.scaleIp)
        private set
    var port by mutableStateOf(prefs.scalePort)
        private set
    var status by mutableStateOf("READY")
        private set
    var csvStatus by mutableStateOf("No CSV update loaded")
        private set
    var cloudStatus by mutableStateOf("")
        private set
    var adminSession by mutableStateOf(loadAdminSession())
        private set
    var stores by mutableStateOf<List<SupabaseRest.Store>>(emptyList())
        private set
    var reportsRefreshTick by mutableStateOf(0)
        private set

    private val transport = EssaeTransport()
    private val supabase = SupabaseRest()

    init {
        viewModelScope.launch(Dispatchers.IO) { syncPendingAuditsInternal() }
    }

    private fun loadAdminSession(): SupabaseRest.AdminSession? = prefs.adminToken.takeIf { it.isNotBlank() }?.let { SupabaseRest.AdminSession(it, prefs.adminUserId, prefs.adminEmail) }

    fun updateHost(v: String) { host = v; prefs.scaleIp = v }
    fun updatePort(v: String) { port = v; prefs.scalePort = v }
    fun updateStatus(v: String) { status = v }

    fun loginAdmin(email: String, password: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val r = supabase.loginAdmin(email.trim(), password)) {
                is SupabaseRest.Result.Ok -> {
                    prefs.adminToken = r.value.accessToken
                    prefs.adminEmail = r.value.email
                    prefs.adminUserId = r.value.userId
                    adminSession = r.value
                    withContext(Dispatchers.Main) { onDone(true) }
                    loadStores()
                }
                is SupabaseRest.Result.Err -> {
                    cloudStatus = r.message
                    withContext(Dispatchers.Main) { onDone(false) }
                }
            }
        }
    }

    fun logoutAdmin() {
        prefs.clearAdmin()
        adminSession = null
        cloudStatus = ""
    }

    fun loadStores() {
        val token = adminSession?.accessToken ?: return
        viewModelScope.launch(Dispatchers.IO) {
            when (val r = supabase.getStores(token)) {
                is SupabaseRest.Result.Ok -> stores = r.value
                is SupabaseRest.Result.Err -> cloudStatus = r.message
            }
        }
    }

    fun testScale(onConnected: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            status = "TESTING"
            transport.testConnection(host, port.toIntOrNull() ?: 4321).fold(
                { status = "CONNECTED"; onConnected() },
                { status = "CONNECTION ERROR"; onError() }
            )
        }
    }

    fun importCsv(text: String) {
        val imported = CsvImporter.parse(text)
        if (imported.isEmpty()) {
            csvStatus = "CSV contains no valid PLUs. Expected: pluno,pluname,plucode,uom,unitprice"
            return
        }
        viewModelScope.launch {
            val before = plus.value.associateBy { it.number }
            val priceChanges = imported.count { item -> before[item.number]?.unitPrice?.let { it != item.unitPrice } == true }
            val newCount = imported.count { it.number !in before }
            dao.upsertAll(imported)
            csvStatus = "CSV loaded: ${imported.size} PLUs | New: $newCount | Price changes: $priceChanges"
            status = "Ready for direct scale upload"
        }
    }

    fun syncStore() {
        viewModelScope.launch(Dispatchers.IO) {
            syncPendingAuditsInternal()
            val ip = deviceIp(context)
            cloudStatus = "SYNC: checking $ip ..."
            when (val r = supabase.getPendingAdminPushes(ip)) {
                is SupabaseRest.Result.Err -> cloudStatus = "SYNC: ${r.message}"
                is SupabaseRest.Result.Ok -> {
                    if (r.value.isEmpty()) {
                        cloudStatus = "SYNC: no pending Admin updates"
                        return@launch
                    }
                    val updateIds = mutableListOf<String>()
                    val cloudItems = mutableListOf<PriceItem>()
                    r.value.forEach { push ->
                        push.items.forEach { item ->
                            val current = plus.value.firstOrNull { it.number == item.pluNo }
                            val oldPrice = current?.unitPrice ?: item.newPrice
                            dao.upsert(Plu(item.pluNo, item.pluName, current?.code ?: "", current?.uom ?: 0, item.newPrice))
                            auditDao.insert(
                                PriceAudit(
                                    deviceIp = ip,
                                    deviceId = deviceId(context),
                                    scaleIp = host,
                                    pluNo = item.pluNo,
                                    pluName = item.pluName,
                                    oldPrice = oldPrice,
                                    newPrice = item.newPrice,
                                    source = "ADMIN_PUSH"
                                )
                            )
                            cloudItems += PriceItem(item.pluNo, item.pluName, oldPrice, item.newPrice)
                        }
                        updateIds += push.updateId
                    }
                    supabase.logPendingPriceChanges(ip, deviceId(context), host, "ADMIN_PUSH", cloudItems)
                    val ack = supabase.markAdminPushesSynced(ip, updateIds)
                    cloudStatus = when (ack) {
                        is SupabaseRest.Result.Ok -> "SYNC: ${r.value.sumOf { it.itemCount }} price update(s) applied • RED / PENDING SCALE UPLOAD"
                        is SupabaseRest.Result.Err -> "SYNC applied locally, but ACK failed: ${ack.message}"
                    }
                }
            }
        }
    }

    private suspend fun syncPendingAuditsInternal() {
        val pending = pendingAudits.value
        if (pending.isEmpty()) return
        val grouped = pending.groupBy { it.source }
        grouped.forEach { (source, rows) ->
            supabase.logPendingPriceChanges(
                deviceIp(context), deviceId(context), host, source,
                rows.groupBy { it.pluNo }.map { (_, items) ->
                    val latest = items.maxByOrNull { it.changedAt }!!
                    val original = items.minByOrNull { it.changedAt }!!
                    PriceItem(latest.pluNo, latest.pluName, original.oldPrice, latest.newPrice)
                }
            )
        }
    }

    fun uploadAllDirect() {
        val all = plus.value.sortedBy { it.number }
        if (all.isEmpty()) { status = "Import a CSV first."; return }
        val nonWeigh = all.count { it.uom != 0 }
        if (nonWeigh > 0) { status = "PCS PLUs are not supported yet. $nonWeigh PCS PLU(s) found; upload is paused until PCS protocol is mapped."; return }

        viewModelScope.launch(Dispatchers.IO) {
            val ip = deviceIp(context)
            val dev = deviceId(context)
            var sessionId: String? = null
            val cloudItems = all.map { plu -> PriceItem(plu.number, plu.name, 0.0, plu.unitPrice) }
            when (val start = supabase.startScaleUpload(ip, dev, host, all.size)) {
                is SupabaseRest.Result.Ok -> sessionId = start.value
                is SupabaseRest.Result.Err -> cloudStatus = "Cloud upload session could not start: ${start.message}"
            }
            status = "Uploading ${all.size} PLUs directly to $host:$port ..."
            val uploadResult = transport.uploadSelectedDirect(host, port.toIntOrNull() ?: 4321, all) { done, total, plu ->
                status = "Uploading $done / $total — PLU ${plu.number} ${plu.name}"
            }
            uploadResult.fold(
                { message ->
                    status = message
                    if (sessionId != null) supabase.completeScaleUpload(sessionId!!, ip, dev, host, cloudItems).also {
                        if (it is SupabaseRest.Result.Ok) all.forEach { p -> auditDao.markUploadedForPrice(p.number, p.unitPrice, System.currentTimeMillis()) }
                    }
                },
                { error ->
                    val msg = error.message ?: "Unknown error"
                    status = "Direct bulk upload failed: $msg"
                    if (sessionId != null) supabase.failScaleUpload(sessionId!!, msg)
                }
            )
        }
    }

    fun updatePrice(plu: Plu, priceText: String) {
        val cleaned = priceText.trim().replace("₹", "").replace(",", "")
        val price = cleaned.toDoubleOrNull()
        if (price == null || price < 0) { status = "Invalid price for PLU ${plu.number}."; return }
        if (price == plu.unitPrice) { status = "PLU ${plu.number} price unchanged."; return }
        viewModelScope.launch(Dispatchers.IO) {
            val pending = auditDao.latestPending(plu.number)
            dao.upsert(plu.copy(unitPrice = price))
            auditDao.insert(
                PriceAudit(
                    deviceIp = deviceIp(context), deviceId = deviceId(context), scaleIp = host,
                    pluNo = plu.number, pluName = plu.name,
                    oldPrice = pending?.oldPrice ?: plu.unitPrice, newPrice = price, source = "MANUAL"
                )
            )
            when (val r = supabase.logPendingPriceChanges(
                deviceIp(context), deviceId(context), host, "MANUAL",
                listOf(PriceItem(plu.number, plu.name, pending?.oldPrice ?: plu.unitPrice, price))
            )) {
                is SupabaseRest.Result.Ok -> cloudStatus = "MANUAL change logged for PLU ${plu.number}"
                is SupabaseRest.Result.Err -> cloudStatus = "Manual change saved locally; cloud audit pending: ${r.message}"
            }
            status = "PLU ${plu.number} price changed to ₹${String.format(Locale.US, "%.2f", price)} • RED / PENDING SCALE UPLOAD"
        }
    }

    fun publishAdminPrice(pluNo: Int, pluName: String, price: Double, applyAll: Boolean, storeIds: List<String>, note: String, onDone: (String) -> Unit) {
        val token = adminSession?.accessToken ?: run { onDone("Admin login required"); return }
        viewModelScope.launch(Dispatchers.IO) {
            when (val r = supabase.publishPriceUpdate(token, applyAll, storeIds, listOf(PushItem(pluNo, pluName, price)), note)) {
                is SupabaseRest.Result.Ok -> withContext(Dispatchers.Main) { onDone("Admin Push published to ${if (applyAll) "all stores" else "selected stores"}.") }
                is SupabaseRest.Result.Err -> withContext(Dispatchers.Main) { onDone("Admin Push failed: ${r.message}") }
            }
        }
    }

    fun saveIpMapping(ip: String, storeCode: String, storeName: String, onDone: (String) -> Unit) {
        val token = adminSession?.accessToken ?: run { onDone("Admin login required"); return }
        viewModelScope.launch(Dispatchers.IO) {
            when (val r = supabase.adminSaveStoreIpMapping(token, ip, storeCode, storeName)) {
                is SupabaseRest.Result.Ok -> { loadStores(); withContext(Dispatchers.Main) { onDone("Mapping saved: $ip → $storeCode") } }
                is SupabaseRest.Result.Err -> withContext(Dispatchers.Main) { onDone("Mapping failed: ${r.message}") }
            }
        }
    }

    fun refreshAdmin() { reportsRefreshTick += 1; loadStores(); cloudStatus = "SYNC: Admin data refreshed" }

    fun clear() {
        viewModelScope.launch {
            dao.deleteAll()
            status = "PLUs cleared"
            csvStatus = "Local PLUs cleared"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EssaeApp(db: AppDatabase) {
    val context = LocalContext.current
    val vm: MainVm = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainVm(
                context.applicationContext, db.pluDao(), db.auditDao(), StorePrefs(context)
            ) as T
        }
    )
    val plus by vm.plus.collectAsState()
    val pendingAudits by vm.pendingAudits.collectAsState()
    var search by rememberSaveable { mutableStateOf("") }
    var showClear by rememberSaveable { mutableStateOf(false) }
    var clearPin by rememberSaveable { mutableStateOf("") }
    var clearPinError by rememberSaveable { mutableStateOf(false) }
    var showAdminLogin by rememberSaveable { mutableStateOf(false) }
    var showReports by rememberSaveable { mutableStateOf(false) }
    var showPush by rememberSaveable { mutableStateOf(false) }
    var showMapping by rememberSaveable { mutableStateOf(false) }

    val admin = vm.adminSession != null
    val filtered = if (search.isBlank()) plus else plus.filter { it.number.toString().contains(search, true) || it.name.contains(search, true) || it.code.contains(search, true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Could not read CSV")
        }.onSuccess(vm::importCsv).onFailure { vm.updateStatus("CSV import failed: ${it.message}") }
    }

    MahaMartTheme {
        if (admin && showReports) {
            ReportsScreen(vm = vm, onBack = { showReports = false })
        } else if (admin && showPush) {
            AdminPushScreen(vm = vm, onBack = { showPush = false })
        } else if (admin && showMapping) {
            AdminMappingScreen(vm = vm, onBack = { showMapping = false })
        } else if (admin) {
            AdminDashboard(
                vm = vm,
                onLabel = { context.startActivity(Intent(context, LabelDesignActivity::class.java)) },
                onReports = { showReports = true },
                onPush = { showPush = true },
                onMapping = { showMapping = true },
                onLogout = vm::logoutAdmin
            )
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                        title = {
                            Column(Modifier.combinedClickable(onClick = {}, onLongClick = { showAdminLogin = true })) {
                                Text("MAHAMART", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Scale Manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        },
                        actions = {
                            TextButton(onClick = { vm.syncStore() }) { Text("↻", fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { context.startActivity(Intent(context, LabelDesignActivity::class.java)) }) { Text("🏷", fontWeight = FontWeight.Bold) }
                        }
                    )
                }
            ) { padding ->
                StoreHome(
                    vm = vm,
                    plus = plus,
                    filtered = filtered,
                    pendingCount = pendingAudits.map { it.pluNo }.distinct().size,
                    search = search,
                    onSearch = { search = it },
                    onImport = { picker.launch(arrayOf("text/*", "text/csv", "application/csv")) },
                    onClear = { showClear = true },
                    padding = padding
                )
            }
        }

        if (showAdminLogin) AdminLoginDialog(vm = vm, onClose = { showAdminLogin = false })

        if (showClear) {
            AlertDialog(
                onDismissRequest = { showClear = false; clearPin = ""; clearPinError = false },
                title = { Text("Clear Local PLUs?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This will permanently delete all PLUs stored on this phone.")
                        OutlinedTextField(value = clearPin, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { clearPin = it; clearPinError = false } }, label = { Text("Enter PIN") }, singleLine = true)
                        if (clearPinError) Text("Incorrect PIN.", color = scaleRedForUi(), fontWeight = FontWeight.Bold)
                    }
                },
                confirmButton = { Button(colors = mahaMartButtonColors(), onClick = { if (clearPin == CLEAR_PLU_PIN) { vm.clear(); showClear = false; clearPin = "" } else clearPinError = true }) { Text("CLEAR") } },
                dismissButton = { TextButton(onClick = { showClear = false }) { Text("CANCEL") } }
            )
        }
    }
}

@Composable
private fun StoreHome(
    vm: MainVm,
    plus: List<Plu>,
    filtered: List<Plu>,
    pendingCount: Int,
    search: String,
    onSearch: (String) -> Unit,
    onImport: () -> Unit,
    onClear: () -> Unit,
    padding: androidx.compose.foundation.layout.PaddingValues
) {
    LazyColumn(Modifier.padding(padding).padding(horizontal = 14.dp, vertical = 10.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ScaleSectionTitle("SCALE CONNECTION", "Essae SI-810PR network interface")
            Spacer(Modifier.height(6.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, scaleBorderColor())) {
                Column(Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(vm.host, vm::updateHost, Modifier.weight(1f), label = { Text("Scale IP") }, singleLine = true)
                        OutlinedTextField(vm.port, vm::updatePort, Modifier.width(105.dp), label = { Text("Port") }, singleLine = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.testScale({}, {}) }, colors = mahaMartButtonColors(), modifier = Modifier.fillMaxWidth()) { Text("TEST CONNECTION") }
                    Spacer(Modifier.height(8.dp))
                    ControlStatus(vm.status)
                    if (vm.cloudStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(vm.cloudStatus, color = if (vm.cloudStatus.contains("failed", true) || vm.cloudStatus.contains("error", true)) scaleRedForUi() else scaleTextSecondaryColor(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            ScaleSectionTitle("PLU DATABASE", "${plus.size} PLUs stored on this phone")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, Modifier.weight(1f)) { Text("IMPORT CSV", fontWeight = FontWeight.Bold) }
                Button(onClick = vm::uploadAllDirect, enabled = plus.isNotEmpty(), colors = mahaMartButtonColors(), modifier = Modifier.weight(1f)) { Text("UPLOAD ALL", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(scaleSurfaceAltColor()), border = BorderStroke(1.dp, scaleBorderColor())) {
                Column(Modifier.padding(12.dp)) {
                    Text("CSV UPDATE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(vm.csvStatus)
                }
            }
            if (pendingCount > 0) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer), border = BorderStroke(2.dp, scaleRedForUi())) {
                    Text("$pendingCount SKU(s) are RED / PENDING SCALE UPLOAD", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onClear, Modifier.fillMaxWidth()) { Text("CLEAR LOCAL PLUs") }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("PLU LIST", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Tap a SKU to edit its price", style = MaterialTheme.typography.bodySmall, color = scaleTextSecondaryColor())
                }
                if (pendingCount > 0) Text("$pendingCount PENDING", color = scaleRedForUi(), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), label = { Text("Search SKU / Name / Barcode") }, singleLine = true)
        }
        if (filtered.isEmpty()) item { EmptyPanel(if (plus.isEmpty()) "No PLUs loaded. Import your CSV." else "No PLU matches your search.") }
        else items(filtered, key = { it.number }) { plu ->
            val pending = vm.pendingAudits.value.any { it.pluNo == plu.number }
            PluCard(plu, pending) { vm.updatePrice(plu, it) }
        }
    }
}

@Composable
private fun AdminLoginDialog(vm: MainVm, onClose: () -> Unit) {
    var email by remember { mutableStateOf(vm.adminSession?.email ?: "itteam@mahamartco.in") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Admin Login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (error.isNotBlank()) Text(error, color = scaleRedForUi(), fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { Button(colors = mahaMartButtonColors(), enabled = !loading, onClick = { loading = true; vm.loginAdmin(email, password) { ok -> loading = false; if (ok) onClose() else error = vm.cloudStatus } }) { Text(if (loading) "LOGGING IN..." else "LOGIN") } },
        dismissButton = { TextButton(onClick = onClose) { Text("CANCEL") } }
    )
}

@Composable
private fun AdminDashboard(vm: MainVm, onLabel: () -> Unit, onReports: () -> Unit, onPush: () -> Unit, onMapping: () -> Unit, onLogout: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column { Text("MAHAMART", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text("Admin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    TextButton(onClick = onLabel) { Text("LABEL") }
                    TextButton(onClick = onReports) { Text("REPORTS") }
                    TextButton(onClick = { vm.refreshAdmin() }) { Text("↻ SYNC") }
                    TextButton(onClick = onLogout) { Text("⏻") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, scaleBorderColor())) {
                    Column(Modifier.padding(16.dp)) {
                        Text("ADMIN CONTROL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Push price changes to all stores or selected stores. Stores receive them only when they press Sync.", color = scaleTextSecondaryColor())
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onPush, colors = mahaMartButtonColors(), modifier = Modifier.fillMaxWidth()) { Text("PUBLISH PRICE UPDATE") }
                    }
                }
            }
            item {
                Text("CONNECTED STORE MAPPINGS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                vm.stores.forEach { s ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, scaleBorderColor())) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${s.code} • ${s.name}", fontWeight = FontWeight.Bold)
                            Text("ACTIVE", color = Color(0xFF16803A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (vm.cloudStatus.isNotBlank()) item { Text(vm.cloudStatus, color = scaleTextSecondaryColor(), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun AdminPushScreen(vm: MainVm, onBack: () -> Unit) {
    var pluNo by rememberSaveable { mutableStateOf("") }
    var pluName by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var allStores by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var message by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Publish Price Update", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(pluNo, { pluNo = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("PLU Number") }, singleLine = true) }
            item { OutlinedTextField(pluName, { pluName = it }, Modifier.fillMaxWidth(), label = { Text("PLU Name") }, singleLine = true) }
            item { OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("New Price") }, singleLine = true) }
            item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") }, singleLine = true) }
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, scaleBorderColor())) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Checkbox(allStores, { allStores = it }); Text("Push to ALL stores", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold) }
                        if (!allStores) {
                            Text("Select stores", fontWeight = FontWeight.Bold)
                            vm.stores.forEach { store ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val checked = store.id in selected
                                    Checkbox(checked, { selected = if (it) selected + store.id else selected - store.id })
                                    Text("${store.code} • ${store.name}", Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    colors = mahaMartButtonColors(), modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val n = pluNo.toIntOrNull(); val p = price.toDoubleOrNull()
                        if (n == null || p == null || p < 0 || pluName.isBlank()) message = "Enter PLU number, name, and a valid price."
                        else if (!allStores && selected.isEmpty()) message = "Select at least one store."
                        else vm.publishAdminPrice(n, pluName.trim(), p, allStores, selected.toList(), note.trim()) { message = it }
                    }
                ) { Text("PUBLISH") }
            }
            if (message.isNotBlank()) item { Text(message, color = if (message.contains("failed", true)) scaleRedForUi() else scaleTextSecondaryColor(), fontWeight = FontWeight.SemiBold) }
        }
    }
}


@Composable
private fun AdminMappingScreen(vm: MainVm, onBack: () -> Unit) {
    var ip by rememberSaveable { mutableStateOf("") }
    var storeCode by rememberSaveable { mutableStateOf("") }
    var storeName by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text("Store IP Mapping", fontWeight = FontWeight.Bold) }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, scaleBorderColor())) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Assign a discovered phone IP to a MahaMart store.", fontWeight = FontWeight.Bold)
                        OutlinedTextField(ip, { ip = it }, Modifier.fillMaxWidth(), label = { Text("Device IP") }, singleLine = true)
                        OutlinedTextField(storeCode, { storeCode = it.uppercase(Locale.US) }, Modifier.fillMaxWidth(), label = { Text("Store Code (e.g. MM001)") }, singleLine = true)
                        OutlinedTextField(storeName, { storeName = it }, Modifier.fillMaxWidth(), label = { Text("Store Name") }, singleLine = true)
                        Button(colors = mahaMartButtonColors(), modifier = Modifier.fillMaxWidth(), onClick = {
                            if (ip.isBlank() || storeCode.isBlank() || storeName.isBlank()) message = "Enter IP, store code and store name."
                            else {
                                vm.saveIpMapping(ip.trim(), storeCode.trim(), storeName.trim()) { message = it }
                            }
                        }) { Text("SAVE MAPPING") }
                        if (message.isNotBlank()) Text(message, color = if (message.contains("failed", true) || message.contains("required", true)) scaleRedForUi() else scaleTextSecondaryColor(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { Text("Store master", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(vm.stores) { s -> Text("${s.code} • ${s.name}") }
        }
    }
}

@Composable
private fun ReportsScreen(vm: MainVm, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(ReportTab.CURRENT.name) }
    var preset by rememberSaveable { mutableStateOf("Today") }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var filter by remember { mutableStateOf(todayFilter()) }
    var rows by remember { mutableStateOf<List<SupabaseRest.ReportRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun load() {
        val token = vm.adminSession?.accessToken ?: return
        loading = true; error = ""
        vm.viewModelScope.launch(Dispatchers.IO) {
            val r = when (ReportTab.valueOf(tab)) {
                ReportTab.CURRENT -> vm.runReport { vmReportCurrent(token, filter) }
                ReportTab.CHANGES -> vm.runReport { vmReportChanges(token, filter) }
                ReportTab.UPLOADS -> vm.runReport { vmReportUploads(token, filter) }
                ReportTab.PUSH -> vm.runReport { vmReportPush(token, filter) }
            }
            withContext(Dispatchers.Main) {
                loading = false
                when (r) { is SupabaseRest.Result.Ok -> rows = r.value; is SupabaseRest.Result.Err -> error = r.message }
            }
        }
    }

    // Initial/changed tab/filter load.
    androidx.compose.runtime.LaunchedEffect(tab, filter, vm.reportsRefreshTick) { load() }

    val exportContext = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val csv = buildReportCsv(rows, tab)
            exportContext.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                actions = { TextButton(onClick = { exportLauncher.launch("mahamart_${tab.lowercase()}_${LocalDate.now()}.csv") }) { Text("EXPORT") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { preset = if (preset == "Today") "Last 7 Days" else "Today"; filter = presetFilter(preset) }) { Text("$preset ▼", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { filterOpen = true }) { Text("FILTER", color = scaleRedForUi(), fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("CURRENT", "CHANGES", "UPLOADS", "PUSH").forEach { label -> TextButton(onClick = { tab = label }) { Text(label, color = if (tab == label) scaleRedForUi() else scaleTextSecondaryColor(), fontWeight = FontWeight.Bold) } }
            }
            Divider()
            if (loading) Text("Loading report...", Modifier.padding(16.dp))
            if (error.isNotBlank()) Text(error, Modifier.padding(16.dp), color = scaleRedForUi())
            LazyColumn(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows) { row -> ReportRowCard(tab, row) }
                if (!loading && rows.isEmpty() && error.isBlank()) item { EmptyPanel("No data matches these filters.") }
            }
        }
    }

    if (filterOpen) {
        ReportFilterDialog(current = filter, onDismiss = { filterOpen = false }, onApply = { f, chosenPreset -> filter = f; preset = chosenPreset; filterOpen = false })
    }
}

// These helpers deliberately isolate Supabase calls from the Compose screen.
private suspend fun MainVm.runReport(block: suspend () -> SupabaseRest.Result<List<SupabaseRest.ReportRow>>): SupabaseRest.Result<List<SupabaseRest.ReportRow>> = withContext(Dispatchers.IO) { block() }
private val vmReportClient = SupabaseRest()
private suspend fun vmReportCurrent(token: String, filter: SupabaseRest.ReportFilter) = vmReportClient.getCurrentPrices(token, filter)
private suspend fun vmReportChanges(token: String, filter: SupabaseRest.ReportFilter) = vmReportClient.getPriceChanges(token, filter)
private suspend fun vmReportUploads(token: String, filter: SupabaseRest.ReportFilter) = vmReportClient.getUploads(token, filter)
private suspend fun vmReportPush(token: String, filter: SupabaseRest.ReportFilter) = vmReportClient.getPushReport(token, filter)

@Composable
private fun ReportRowCard(tab: String, row: SupabaseRest.ReportRow) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, scaleBorderColor())) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${row.storeCode.ifBlank { "UNMAPPED" }} • ${row.storeName.ifBlank { "Unknown Store" }}", fontWeight = FontWeight.Bold)
            val ip = row.deviceIp.takeIf { it.isNotBlank() }?.let { "IP $it • " } ?: ""
            Text("$ip${if (row.pluNo > 0) "PLU ${row.pluNo} • ${row.pluName}" else row.pluName}", color = scaleTextSecondaryColor())
            when (tab) {
                "CURRENT" -> Text("₹${String.format(Locale.US, "%.2f", row.unitPrice ?: 0.0)} • ${row.date}", fontWeight = FontWeight.SemiBold)
                "CHANGES" -> Text("₹${String.format(Locale.US, "%.2f", row.oldPrice ?: 0.0)} → ₹${String.format(Locale.US, "%.2f", row.newPrice ?: 0.0)} • ${row.source} • ${row.status}", fontWeight = FontWeight.SemiBold)
                "UPLOADS" -> Text("${row.status} • ${row.itemCount} PLUs • ${row.date}", fontWeight = FontWeight.SemiBold)
                "PUSH" -> Text("${row.source} • ${row.status} • ${row.itemCount} SKU(s) • ${row.date}", fontWeight = FontWeight.SemiBold)
            }
            if (row.errorMessage.isNotBlank()) Text(row.errorMessage, color = scaleRedForUi())
        }
    }
}

@Composable
private fun ReportFilterDialog(current: SupabaseRest.ReportFilter, onDismiss: () -> Unit, onApply: (SupabaseRest.ReportFilter, String) -> Unit) {
    var store by remember { mutableStateOf(current.storeCode.orEmpty()) }
    var ip by remember { mutableStateOf(current.deviceIp.orEmpty()) }
    var plu by remember { mutableStateOf(current.plu.orEmpty()) }
    var from by remember { mutableStateOf(current.from?.take(10).orEmpty()) }
    var to by remember { mutableStateOf(current.to?.take(10).orEmpty()) }
    var status by remember { mutableStateOf(current.status ?: "ALL") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FILTER REPORT") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(store, { store = it }, Modifier.fillMaxWidth(), label = { Text("Store code (e.g. MM001)") }, singleLine = true)
                OutlinedTextField(ip, { ip = it }, Modifier.fillMaxWidth(), label = { Text("IP address") }, singleLine = true)
                OutlinedTextField(plu, { plu = it }, Modifier.fillMaxWidth(), label = { Text("SKU / PLU / name") }, singleLine = true)
                OutlinedTextField(from, { from = it }, Modifier.fillMaxWidth(), label = { Text("From yyyy-MM-dd") }, singleLine = true)
                OutlinedTextField(to, { to = it }, Modifier.fillMaxWidth(), label = { Text("To yyyy-MM-dd") }, singleLine = true)
                OutlinedTextField(status, { status = it.uppercase(Locale.US) }, Modifier.fillMaxWidth(), label = { Text("Status (ALL/PENDING/SYNCED/UPLOADED/FAILED/COMPLETED)") }, singleLine = true)
                Text("Tip: use store code, IP, or SKU fields individually or together.", color = scaleTextSecondaryColor())
            }
        },
        confirmButton = { Button(colors = mahaMartButtonColors(), onClick = {
            val dateRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
            val fromDate = from.takeIf { it.matches(dateRegex) }?.let { LocalDate.parse(it) }
            val toDate = to.takeIf { it.matches(dateRegex) }?.let { LocalDate.parse(it) }
            val fromStamp = fromDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toString()
            val toStamp = toDate?.plusDays(1)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toString()
            onApply(SupabaseRest.ReportFilter(store.ifBlank { null }, ip.ifBlank { null }, plu.ifBlank { null }, fromStamp, toStamp, status.ifBlank { null }), "Custom")
        }) { Text("APPLY FILTER") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}

private fun localStartStamp(date: LocalDate): String = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

private fun todayFilter(): SupabaseRest.ReportFilter {
    val d = LocalDate.now()
    return SupabaseRest.ReportFilter(from = localStartStamp(d), to = localStartStamp(d.plusDays(1)))
}

private fun presetFilter(preset: String): SupabaseRest.ReportFilter {
    val today = LocalDate.now()
    val from = when (preset) {
        "Last 7 Days" -> today.minusDays(6)
        "Last 30 Days" -> today.minusDays(29)
        "This Month" -> today.withDayOfMonth(1)
        "Yesterday" -> today.minusDays(1)
        else -> today
    }
    val to = if (preset == "Yesterday") today else today.plusDays(1)
    return SupabaseRest.ReportFilter(from = localStartStamp(from), to = localStartStamp(to))
}

private fun buildReportCsv(rows: List<SupabaseRest.ReportRow>, tab: String): String {
    val b = StringBuilder()
    b.append("Store,Store Name,IP,PLU,Item,Old Price,New Price,Unit Price,Source,Status,Date,Uploaded At,Scale IP,Item Count,Error\n")
    rows.forEach { r ->
        val values = listOf(r.storeCode, r.storeName, r.deviceIp, r.pluNo.takeIf { it > 0 }?.toString().orEmpty(), r.pluName, r.oldPrice?.toString().orEmpty(), r.newPrice?.toString().orEmpty(), r.unitPrice?.toString().orEmpty(), r.source, r.status, r.date, r.uploadedAt.orEmpty(), r.scaleIp, r.itemCount.toString(), r.errorMessage)
        b.append(values.joinToString(",") { csvEscape(it) }).append('\n')
    }
    return b.toString()
}
private fun csvEscape(s: String): String = "\"${s.replace("\"", "\"\"")}\""

@Composable
private fun ScaleSectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scaleDarkColor()); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scaleTextSecondaryColor()) }
        Box(Modifier.width(4.dp).height(28.dp).background(scaleRedForUi(), RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun ControlStatus(text: String) {
    val color = when (text) { "CONNECTED" -> Color(0xFF16803A); "CONNECTION ERROR" -> Color(0xFFD00019); else -> Color.Black }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, color)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("STATUS", fontWeight = FontWeight.Bold); Text(if (text == "TESTING") "TESTING..." else text, color = color, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun EmptyPanel(message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), border = BorderStroke(1.dp, scaleBorderColor())) { Text(message, Modifier.padding(14.dp), color = scaleTextSecondaryColor()) }
}

@Composable
fun PluCard(plu: Plu, priceChanged: Boolean, onSavePrice: (String) -> Unit) {
    var show by remember(plu.number, plu.unitPrice) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { show = true }, colors = CardDefaults.cardColors(if (priceChanged) MaterialTheme.colorScheme.primaryContainer else Color.White), border = BorderStroke(if (priceChanged) 2.dp else 1.dp, if (priceChanged) MaterialTheme.colorScheme.primary else scaleBorderColor())) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.width(4.dp).height(58.dp).background(if (priceChanged) scaleRedForUi() else Color.Transparent, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${plu.number}  ${plu.name}", fontWeight = FontWeight.Bold, color = if (priceChanged) MaterialTheme.colorScheme.primary else scaleDarkColor())
                    if (priceChanged) Text("CHANGED", color = scaleRedForUi(), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${plu.code} • ${if (plu.uom == 1) "PCS" else "WEIGH"}", color = scaleTextSecondaryColor())
                    Text("₹${String.format(Locale.US, "%.2f", plu.unitPrice)}", fontWeight = FontWeight.Bold, color = if (priceChanged) scaleRedForUi() else scaleDarkColor())
                }
                Text(if (priceChanged) "Price changed • tap to edit" else "Tap to edit price", style = MaterialTheme.typography.labelSmall, color = if (priceChanged) scaleRedForUi() else scaleTextSecondaryColor())
            }
        }
    }
    if (show) {
        var price by remember(plu.number, plu.unitPrice) { mutableStateOf(String.format(Locale.US, "%.2f", plu.unitPrice)) }
        AlertDialog(onDismissRequest = { show = false }, title = { Text("Update Price — PLU ${plu.number}") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(plu.name, fontWeight = FontWeight.Bold); OutlinedTextField(price, { price = it }, label = { Text("Unit Price") }, prefix = { Text("₹") }, singleLine = true) }
        }, confirmButton = { Button(colors = mahaMartButtonColors(), onClick = { onSavePrice(price); show = false }) { Text("SAVE PRICE") } }, dismissButton = { TextButton(onClick = { show = false }) { Text("CANCEL") } })
    }
}
