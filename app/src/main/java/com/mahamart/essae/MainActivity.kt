package com.mahamart.essae

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.painterResource
import com.mahamart.essae.data.AppDatabase
import com.mahamart.essae.data.Plu
import com.mahamart.essae.data.PluDao
import com.mahamart.essae.data.PriceChangeAudit
import com.mahamart.essae.data.PriceChangeAuditDao
import com.mahamart.essae.cloud.ManualPriceAuditSync
import com.mahamart.essae.cloud.StoreAdminPushSync
import com.mahamart.essae.network.EssaeTransport
import com.mahamart.essae.util.CsvImporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CLEAR_PLU_PIN = "3331"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.create(applicationContext)

        setContent {
            EssaeApp(db)
        }
    }
}

class StorePrefs(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "store_settings",
            Context.MODE_PRIVATE
        )

    var scaleIp: String
        get() = prefs.getString(
            "scaleIp",
            "192.168.100.200"
        ) ?: "192.168.100.200"

        set(value) {
            prefs.edit()
                .putString("scaleIp", value)
                .apply()
        }

    var scalePort: String
        get() = prefs.getString(
            "scalePort",
            "4321"
        ) ?: "4321"

        set(value) {
            prefs.edit()
                .putString("scalePort", value)
                .apply()
        }
}

class MainVm(
    private val dao: PluDao,
    private val auditDao: PriceChangeAuditDao,
    private val prefs: StorePrefs,
    context: Context
) : ViewModel() {

    val plus = dao.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    var host by mutableStateOf(prefs.scaleIp)
        private set

    var port by mutableStateOf(prefs.scalePort)
        private set

    var status by mutableStateOf("READY")
        private set

    var csvStatus by mutableStateOf("No CSV update loaded")
        private set

    private val transport = EssaeTransport()
    private val auditSync = ManualPriceAuditSync(context.applicationContext)
    private val adminPushSync = StoreAdminPushSync(context.applicationContext)

    /*
     * UI marker for the current local edit session.
     * A separate Room audit keeps the actual change history.
     */
    var changedPluNumbers by mutableStateOf(
        emptySet<Int>()
    )
        private set

    fun updateHost(v: String) {
        host = v
        prefs.scaleIp = v
    }

    fun updatePort(v: String) {
        port = v
        prefs.scalePort = v
    }

    fun updateStatus(v: String) {
        status = v
    }

    fun testScale(
        onConnected: () -> Unit,
        onError: () -> Unit
    ) {
        viewModelScope.launch {
            status = "TESTING"

            transport.testConnection(
                host,
                port.toIntOrNull() ?: 4321
            ).fold(
                {
                    status = "CONNECTED"
                    onConnected()
                },
                {
                    status = "CONNECTION ERROR"
                    onError()
                }
            )
        }
    }

    fun importCsv(text: String) {

        val imported = CsvImporter.parse(text)

        if (imported.isEmpty()) {
            csvStatus =
                "CSV contains no valid PLUs. Expected: " +
                        "pluno,pluname,plucode,uom,unitprice"
            return
        }

        viewModelScope.launch {

            val before =
                plus.value.associateBy { it.number }

            val priceChanges =
                imported.count { item ->
                    val old = before[item.number]
                    old != null &&
                            old.unitPrice != item.unitPrice
                }

            val newPluCount =
                imported.count {
                    it.number !in before
                }

            dao.upsertAll(imported)

            /*
             * A newly imported CSV starts a fresh
             * visual editing session.
             */
            changedPluNumbers = emptySet()

            csvStatus =
                "CSV loaded: ${imported.size} PLUs | " +
                        "New: $newPluCount | " +
                        "Price changes: $priceChanges"

            status =
                "Ready for direct scale upload"
        }
    }

    fun uploadAllDirect() {

        val all =
            plus.value.sortedBy {
                it.number
            }

        if (all.isEmpty()) {
            status = "Import a CSV first."
            return
        }

        val nonWeigh =
            all.count {
                it.uom != 0
            }

        if (nonWeigh > 0) {
            status =
                "PCS PLUs are not supported yet. " +
                        "${nonWeigh} PCS PLU(s) found; " +
                        "upload is paused until PCS protocol is mapped."
            return
        }

        viewModelScope.launch {

            status =
                "Uploading ${all.size} PLUs directly to " +
                        "$host:$port ..."

            status =
                transport.uploadSelectedDirect(
                    host,
                    port.toIntOrNull() ?: 4321,
                    all
                ) { done, total, plu ->

                    status =
                        "Uploading $done / $total — " +
                                "PLU ${plu.number} ${plu.name}"

                }.fold(
                    { it },
                    {
                        "Direct bulk upload failed: ${
                            it.message ?: "Unknown error"
                        }"
                    }
                )
        }
    }

    fun updatePrice(
        plu: Plu,
        priceText: String
    ) {

        val cleaned =
            priceText
                .trim()
                .replace("₹", "")
                .replace(",", "")

        val price =
            cleaned.toDoubleOrNull()

        if (price == null || price < 0) {
            status =
                "Invalid price for PLU ${plu.number}."
            return
        }

        val changed = plu.unitPrice != price

        viewModelScope.launch {

            dao.upsert(
                plu.copy(
                    unitPrice = price
                )
            )

            if (changed) {
                changedPluNumbers =
                    changedPluNumbers + plu.number

                val audit = PriceChangeAudit(
                    pluNo = plu.number,
                    pluName = plu.name,
                    oldPrice = plu.unitPrice,
                    newPrice = price,
                    source = "MANUAL",
                    status = "PENDING",
                    deviceIp = auditSync.deviceIp(),
                    deviceId = auditSync.deviceId(),
                    scaleIp = host
                )

                val auditId = auditDao.insert(audit)
                val stored = audit.copy(id = auditId)

                if (auditSync.pushManualChange(stored)) {
                    auditDao.markCloudSynced(listOf(auditId))
                    status =
                        "PLU ${plu.number} manually changed — audit recorded."
                } else {
                    status =
                        "PLU ${plu.number} manually changed — audit saved; cloud sync pending."
                }
            } else {
                status =
                    "PLU ${plu.number} price unchanged."
            }
        }
    }

    fun syncAdminPush() {
        viewModelScope.launch {
            val result = adminPushSync.pullAndApply(
                currentPlus = plus.value,
                upsert = { plu -> dao.upsert(plu) },
                insertAudit = { audit -> auditDao.insert(audit) }
            )

            result.onSuccess { count ->
                if (count > 0) {
                    changedPluNumbers =
                        changedPluNumbers + auditDao.getUnsynced()
                            .filter { it.source == "ADMIN_PUSH" }
                            .map { it.pluNo }
                            .toSet()

                    status = "Admin Push synced: $count price(s)."
                }
            }
        }
    }

    fun retryUnsyncedAudits() {
        viewModelScope.launch {
            val pending = auditDao.getUnsynced()
            if (pending.isEmpty()) return@launch

            val syncedIds = mutableListOf<Long>()
            for (audit in pending) {
                if (auditSync.pushManualChange(audit)) {
                    syncedIds += audit.id
                }
            }

            if (syncedIds.isNotEmpty()) {
                auditDao.markCloudSynced(syncedIds)
                status = "${syncedIds.size} manual audit(s) synced."
            }
        }
    }

    fun clear() {
        viewModelScope.launch {

            dao.deleteAll()

            changedPluNumbers =
                emptySet()

            csvStatus =
                "Local PLUs cleared"

            status =
                "PLUs cleared"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EssaeApp(db: AppDatabase) {

    val context =
        LocalContext.current

    val vm: MainVm =
        viewModel(
            factory =
                object :
                    ViewModelProvider.Factory {

                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(
                        modelClass: Class<T>
                    ): T {

                        return MainVm(
                            db.pluDao(),
                            db.priceChangeAuditDao(),
                            StorePrefs(context),
                            context
                        ) as T
                    }
                }
        )

    LaunchedEffect(Unit) {
        val seedPrefs = context.getSharedPreferences(
            "data_seed",
            Context.MODE_PRIVATE
        )

        if (!seedPrefs.getBoolean("plu_master_seeded", false)) {
            if (db.pluDao().count() == 0) {
                runCatching {
                    val bundledCsv = context.assets.open("PLU_MASTER.csv")
                        .bufferedReader()
                        .use { it.readText() }

                    val bundledPlus = CsvImporter.parse(bundledCsv)

                    if (bundledPlus.isNotEmpty()) {
                        db.pluDao().upsertAll(
                            bundledPlus.map { it.copy(unitPrice = 0.0) }
                        )
                    }
                }
            }

            seedPrefs.edit()
                .putBoolean("plu_master_seeded", true)
                .apply()
        }

        vm.retryUnsyncedAudits()
    }

    val pluList by
    vm.plus.collectAsState()

    var searchQuery by
    rememberSaveable {
        mutableStateOf("")
    }

    var showClearPluDialog by
    rememberSaveable {
        mutableStateOf(false)
    }

    var clearPluPin by
    rememberSaveable {
        mutableStateOf("")
    }

    var clearPinError by
    rememberSaveable {
        mutableStateOf(false)
    }

    var deviceRegistered by rememberSaveable {
        mutableStateOf(false)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val prefs = context.getSharedPreferences(
                    "store_device_registration",
                    Context.MODE_PRIVATE
                )
                deviceRegistered = prefs.getBoolean("registered", false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val q =
        searchQuery.trim()

    val filteredPluList =
        if (q.isEmpty()) {
            pluList
        } else {
            pluList.filter { plu ->
                plu.number
                    .toString()
                    .contains(
                        q,
                        ignoreCase = true
                    ) ||
                        plu.name.contains(
                            q,
                            ignoreCase = true
                        ) ||
                        plu.code
                            .toString()
                            .contains(
                                q,
                                ignoreCase = true
                            )
            }
        }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {

                runCatching {

                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }
                        ?: error(
                            "Could not read CSV file"
                        )

                }
                    .onSuccess(vm::importCsv)
                    .onFailure {
                        vm.updateStatus(
                            "CSV import failed: ${it.message}"
                        )
                    }
            }
        }

    MahaMartTheme {

        Scaffold(

            containerColor =
                MaterialTheme
                    .colorScheme
                    .background,

            topBar = {

                TopAppBar(

                    colors =
                        TopAppBarDefaults
                            .topAppBarColors(
                                containerColor =
                                    Color.White,
                                titleContentColor =
                                    scaleDarkColor(),
                                actionIconContentColor =
                                    scaleRedForUi()
                            ),

                    title = {

                        Column {

                            Text(
                                "MAHAMART",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                "Scale Manager",
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            context.startActivity(
                                                Intent(
                                                    context,
                                                    AdminActivity::class.java
                                                )
                                            )
                                        }
                                    )
                                },
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    },

                    actions = {

                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        StoreDeviceRegistrationActivity::class.java
                                    )
                                )
                            }
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (deviceRegistered) {
                                        R.drawable.ic_device_registered
                                    } else {
                                        R.drawable.ic_device_unregistered
                                    }
                                ),
                                contentDescription = if (deviceRegistered) {
                                    "Registered store device"
                                } else {
                                    "Register this store device"
                                }
                            )
                        }

                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        LabelDesignActivity::class.java
                                    )
                                )
                            }
                        ) {
                            Text(
                                "LABEL DESIGN",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                )
            }

        ) { padding ->

            LazyColumn(

                modifier =
                    Modifier
                        .padding(padding)
                        .padding(
                            horizontal = 14.dp,
                            vertical = 10.dp
                        )
                        .fillMaxSize(),

                verticalArrangement =
                    Arrangement.spacedBy(10.dp)

            ) {

                /*
                 * DEVICE / CONNECTION PANEL
                 */
                item {

                    ScaleSectionTitle(
                        title = "SCALE CONNECTION",
                        subtitle = "Essae SI-810PR network interface"
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Card(

                        modifier =
                            Modifier.fillMaxWidth(),

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    Color.White
                            ),

                        border =
                            BorderStroke(
                                1.dp,
                                scaleBorderColor()
                            ),

                        shape =
                            RoundedCornerShape(8.dp)
                    ) {

                        Column(
                            Modifier.padding(14.dp)
                        ) {

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {

                                Row(
                                    horizontalArrangement =
                                        Arrangement.spacedBy(7.dp)
                                ) {

                                    Box(
                                        modifier =
                                            Modifier
                                                .width(9.dp)
                                                .height(9.dp)
                                                .background(
                                                    scaleRedForUi(),
                                                    RoundedCornerShape(
                                                        50
                                                    )
                                                )
                                    )

                                    Text(
                                        "SCALE DEVICE",
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                }

                                Text(
                                    "TCP / IP",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelSmall,
                                    color =
                                        scaleTextSecondaryColor()
                                )
                            }

                            Spacer(
                                Modifier.height(12.dp)
                            )

                            Row(

                                modifier =
                                    Modifier.fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp)

                            ) {

                                OutlinedTextField(

                                    value =
                                        vm.host,

                                    onValueChange =
                                        vm::updateHost,

                                    modifier =
                                        Modifier.weight(1f),

                                    label = {
                                        Text("Scale IP")
                                    },

                                    singleLine = true
                                )

                                OutlinedTextField(

                                    value =
                                        vm.port,

                                    onValueChange =
                                        vm::updatePort,

                                    modifier =
                                        Modifier.width(105.dp),

                                    label = {
                                        Text("Port")
                                    },

                                    singleLine = true
                                )
                            }

                            Spacer(
                                Modifier.height(8.dp)
                            )

                            Button(

                                onClick = {
                                    vm.testScale(
                                        onConnected = {
                                            ConnectionFeedback.connected(context)
                                        },
                                        onError = {
                                            ConnectionFeedback.error(context)
                                        }
                                    )
                                },

                                colors =
                                    mahaMartButtonColors(),

                                modifier =
                                    Modifier.fillMaxWidth(),

                                shape =
                                    RoundedCornerShape(6.dp)

                            ) {

                                Text(
                                    "TEST CONNECTION",
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }

                            Spacer(
                                Modifier.height(8.dp)
                            )

                            ControlStatus(
                                text = vm.status
                            )
                        }
                    }
                }


                /*
                 * PLU DATABASE PANEL
                 */
                item {

                    Spacer(
                        Modifier.height(2.dp)
                    )

                    ScaleSectionTitle(
                        title = "PLU DATABASE",
                        subtitle =
                            "${pluList.size} PLUs currently stored on this phone"
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedButton(

                            onClick = {
                                picker.launch(
                                    arrayOf(
                                        "text/*",
                                        "text/csv",
                                        "application/csv"
                                    )
                                )
                            },

                            modifier =
                                Modifier.weight(1f),

                            shape =
                                RoundedCornerShape(6.dp)

                        ) {

                            Text(
                                "IMPORT CSV",
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Button(

                            onClick =
                                vm::uploadAllDirect,

                            enabled =
                                pluList.isNotEmpty(),

                            colors =
                                mahaMartButtonColors(),

                            modifier =
                                Modifier.weight(1f),

                            shape =
                                RoundedCornerShape(6.dp)

                        ) {

                            Text(
                                "UPLOAD ALL",
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Card(

                        modifier =
                            Modifier.fillMaxWidth(),

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    scaleSurfaceAltColor()
                            ),

                        border =
                            BorderStroke(
                                1.dp,
                                scaleBorderColor()
                            ),

                        shape =
                            RoundedCornerShape(8.dp)

                    ) {

                        Column(
                            Modifier.padding(12.dp)
                        ) {

                            Text(
                                "CSV UPDATE",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    scaleTextSecondaryColor()
                            )

                            Spacer(
                                Modifier.height(3.dp)
                            )

                            Text(
                                vm.csvStatus
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    OutlinedButton(

                        onClick = {
                            showClearPluDialog = true
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(6.dp)

                    ) {

                        Text(
                            "CLEAR LOCAL PLUs"
                        )
                    }
                }


                /*
                 * SEARCH / LIST
                 */
                item {

                    Spacer(
                        Modifier.height(2.dp)
                    )

                    Row(

                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween

                    ) {

                        Column {

                            Text(
                                "PLU LIST",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                "Tap a SKU to edit its price",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                                color =
                                    scaleTextSecondaryColor()
                            )
                        }

                        if (
                            vm.changedPluNumbers.isNotEmpty()
                        ) {

                            Text(
                                "${vm.changedPluNumbers.size} CHANGED",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                color =
                                    scaleRedForUi(),
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    OutlinedTextField(

                        value =
                            searchQuery,

                        onValueChange = {
                            searchQuery = it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text(
                                "Search SKU / Name / Barcode"
                            )
                        },

                        placeholder = {
                            Text(
                                "Search by number, product or code"
                            )
                        },

                        singleLine = true,

                        shape =
                            RoundedCornerShape(7.dp)
                    )

                    if (searchQuery.isNotBlank()) {

                        Spacer(
                            Modifier.height(4.dp)
                        )

                        Text(
                            "${filteredPluList.size} result(s) from ${pluList.size} PLUs",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                            color =
                                scaleTextSecondaryColor()
                        )
                    }
                }


                if (pluList.isEmpty()) {

                    item {

                        EmptyPanel(
                            "No PLUs loaded. Import your CSV."
                        )
                    }

                } else if (filteredPluList.isEmpty()) {

                    item {

                        EmptyPanel(
                            "No PLU matches your search."
                        )
                    }

                } else {

                    items(
                        filteredPluList,
                        key = {
                            it.number
                        }
                    ) { plu ->

                        PluCard(

                            plu = plu,

                            priceChanged =
                                plu.number in
                                        vm.changedPluNumbers,

                            onSavePrice = {
                                    priceText ->

                                vm.updatePrice(
                                    plu,
                                    priceText
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showClearPluDialog) {
        AlertDialog(
            onDismissRequest = {
                showClearPluDialog = false
                clearPluPin = ""
                clearPinError = false
            },

            title = {
                Text("Clear Local PLUs?")
            },

            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "This will permanently delete all PLUs stored on this phone. " +
                                "This action cannot be undone."
                    )

                    OutlinedTextField(
                        value = clearPluPin,
                        onValueChange = {
                            if (it.length <= 4 &&
                                it.all { char -> char.isDigit() }
                            ) {
                                clearPluPin = it
                                clearPinError = false
                            }
                        },
                        label = {
                            Text("Enter PIN")
                        },
                        placeholder = {
                            Text("4-digit PIN")
                        },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.NumberPassword
                            ),
                        isError = clearPinError
                    )

                    if (clearPinError) {
                        Text(
                            "Incorrect PIN. PLUs were not deleted.",
                            color = Color(0xFFD00019),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },

            confirmButton = {
                Button(
                    colors = mahaMartButtonColors(),
                    onClick = {
                        if (clearPluPin == CLEAR_PLU_PIN) {
                            vm.clear()
                            showClearPluDialog = false
                            clearPluPin = ""
                            clearPinError = false
                        } else {
                            clearPinError = true
                        }
                    }
                ) {
                    Text("CLEAR")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showClearPluDialog = false
                        clearPluPin = ""
                        clearPinError = false
                    }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
}


/*
 * Section heading for the technical control-panel UI.
 */
@Composable
private fun ScaleSectionTitle(
    title: String,
    subtitle: String
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Column {

            Text(
                title,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold,
                color =
                    scaleDarkColor()
            )

            Text(
                subtitle,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    scaleTextSecondaryColor()
            )
        }

        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(
                        scaleRedForUi(),
                        RoundedCornerShape(2.dp)
                    )
        )
    }
}


/*
 * Technical status panel.
 */
@Composable
private fun ControlStatus(
    text: String
) {
    val statusColor =
        when {
            text == "CONNECTED" -> Color(0xFF16803A)
            text == "CONNECTION ERROR" -> Color(0xFFD00019)
            else -> Color.Black
        }

    val displayText =
        when {
            text == "TESTING" -> "TESTING..."
            else -> text
        }

    Card(
        modifier =
            Modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(
                containerColor = Color.White
            ),

        border =
            BorderStroke(
                1.dp,
                statusColor
            ),

        shape =
            RoundedCornerShape(6.dp)
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 9.dp
                ),

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Text(
                "STATUS",
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    Color.Black,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                displayText,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    statusColor,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}


/*
 * Empty state.
 */
@Composable
private fun EmptyPanel(
    message: String
) {

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color.White
            ),

        border =
            BorderStroke(
                1.dp,
                scaleBorderColor()
            ),

        shape =
            RoundedCornerShape(8.dp)

    ) {

        Text(
            message,
            modifier =
                Modifier.padding(14.dp),
            color =
                scaleTextSecondaryColor()
        )
    }
}


/*
 * PLU database row.
 *
 * Normal:
 * white / neutral
 *
 * Changed:
 * light MahaMart red + red border
 */
@Composable
fun PluCard(
    plu: Plu,
    priceChanged: Boolean,
    onSavePrice: (String) -> Unit
) {

    var showPriceDialog by remember(
        plu.number,
        plu.unitPrice
    ) {
        mutableStateOf(false)
    }


    val cardColor =

        if (priceChanged) {

            MaterialTheme
                .colorScheme
                .primaryContainer

        } else {

            Color.White
        }


    val borderColor =

        if (priceChanged) {

            MaterialTheme
                .colorScheme
                .primary

        } else {

            scaleBorderColor()
        }


    Card(

        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    showPriceDialog = true
                },

        colors =
            CardDefaults.cardColors(
                containerColor =
                    cardColor
            ),

        border =
            BorderStroke(
                if (priceChanged) {
                    2.dp
                } else {
                    1.dp
                },
                borderColor
            ),

        shape =
            RoundedCornerShape(7.dp)

    ) {

        Row(
            modifier =
                Modifier.padding(12.dp)
        ) {

            /*
             * Small red activity marker.
             */
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .height(58.dp)
                        .background(
                            if (priceChanged) {
                                scaleRedForUi()
                            } else {
                                Color.Transparent
                            },
                            RoundedCornerShape(2.dp)
                        )
            )


            Spacer(
                Modifier.width(10.dp)
            )


            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween

                ) {

                    Text(

                        "${plu.number}  ${plu.name}",

                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            if (priceChanged) {
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            } else {
                                scaleDarkColor()
                            }
                    )


                    if (priceChanged) {

                        Text(

                            "CHANGED",

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }


                Spacer(
                    Modifier.height(3.dp)
                )


                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween

                ) {

                    Text(
                        "${plu.code}  •  ${
                            if (plu.uom == 1) {
                                "PCS"
                            } else {
                                "WEIGH"
                            }
                        }",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            scaleTextSecondaryColor()
                    )


                    Text(

                        "₹${String.format("%.2f", plu.unitPrice)}",

                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            if (priceChanged) {
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            } else {
                                scaleDarkColor()
                            }
                    )
                }


                Text(

                    if (priceChanged) {
                        "Price changed • tap to edit"
                    } else {
                        "Tap to edit price"
                    },

                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,

                    color =
                        if (priceChanged) {
                            MaterialTheme
                                .colorScheme
                                .primary
                        } else {
                            scaleTextSecondaryColor()
                        }
                )
            }
        }
    }


    /*
     * Price editor.
     */
    if (showPriceDialog) {

        var priceText by remember(
            plu.number,
            plu.unitPrice
        ) {

            mutableStateOf(
                String.format(
                    "%.2f",
                    plu.unitPrice
                )
            )
        }


        AlertDialog(

            onDismissRequest = {
                showPriceDialog = false
            },

            title = {
                Text(
                    "Update Price — PLU ${plu.number}"
                )
            },

            text = {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        plu.name,
                        style =
                            MaterialTheme
                                .typography
                                .titleSmall,
                        fontWeight =
                            FontWeight.Bold
                    )

                    if (priceChanged) {

                        Text(
                            "This SKU was changed during this session.",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
                    }

                    OutlinedTextField(

                        value =
                            priceText,

                        onValueChange = {
                            priceText = it
                        },

                        label = {
                            Text("Unit Price")
                        },

                        prefix = {
                            Text("₹")
                        },

                        singleLine = true
                    )
                }
            },

            confirmButton = {

                Button(

                    colors =
                        mahaMartButtonColors(),

                    onClick = {

                        onSavePrice(
                            priceText
                        )

                        showPriceDialog = false
                    }

                ) {

                    Text(
                        "SAVE PRICE"
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showPriceDialog = false
                    }
                ) {

                    Text(
                        "CANCEL"
                    )
                }
            }
        )
    }
}

