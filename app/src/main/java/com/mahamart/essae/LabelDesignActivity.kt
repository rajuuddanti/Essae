package com.mahamart.essae

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahamart.essae.network.EssaeLabelTransport
import kotlinx.coroutines.launch

class LabelDesignActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LabelDesignScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelDesignScreen() {

    val context = LocalContext.current

    val prefs = remember {
        StorePrefs(context)
    }

    val scope = rememberCoroutineScope()

    var host by remember {
        mutableStateOf(prefs.scaleIp)
    }

    var port by remember {
        mutableStateOf(prefs.scalePort)
    }

    var selectedSlot by remember {
        mutableStateOf(LabelDesignStore.Slot.WEIGHT_ONLY)
    }

    var selectedFileName by remember {
        mutableStateOf(LabelDesignStore.displayFileName(LabelDesignStore.Slot.WEIGHT_ONLY))
    }

    var status by remember {
        mutableStateOf("READY")
    }

    var isBusy by remember {
        mutableStateOf(false)
    }

    var connectionState by remember {
        mutableStateOf(ConnectionState.READY)
    }

    val transport = remember {
        EssaeLabelTransport()
    }

    LaunchedEffect(Unit) {
        runCatching {
            LabelDesignStore.ensureBundled(context)
        }.onFailure {
            status = "DESIGN LOAD ERROR"
            connectionState = ConnectionState.ERROR
        }
    }

    val filePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {
                try {
                    LabelDesignStore.importInto(
                        context,
                        selectedSlot,
                        uri
                    )

                    selectedFileName =
                        LabelDesignStore.displayFileName(selectedSlot)

                    status =
                        selectedSlot.title.uppercase() + " REPLACED"

                    connectionState =
                        ConnectionState.READY

                } catch (_: Exception) {
                    status = "IMPORT ERROR"
                    connectionState = ConnectionState.ERROR
                    playErrorFeedback(context)
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

                                navigationIconContentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
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
                                "Label Design",

                                style =
                                    MaterialTheme
                                        .typography
                                        .titleLarge,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    },

                    navigationIcon = {

                        TextButton(
                            onClick = {
                                finishActivity(context)
                            }
                        ) {

                            Text(
                                "BACK",

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

            Column(

                modifier =
                    Modifier
                        .padding(padding)
                        .padding(14.dp)
                        .fillMaxSize(),

                verticalArrangement =
                    Arrangement.spacedBy(10.dp)

            ) {

                LabelSectionTitle(
                    "LABEL DESIGN",
                    "Direct Essae label design tools"
                )

                // ---------------------------------------------------------
                // SCALE CONNECTION
                // ---------------------------------------------------------

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

                        Text(
                            "SCALE CONNECTION",

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
                            Modifier.height(8.dp)
                        )

                        Row(

                            Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)

                        ) {

                            OutlinedTextField(

                                value =
                                    host,

                                onValueChange = {

                                    host = it
                                    prefs.scaleIp = it

                                    status =
                                        "READY"

                                    connectionState =
                                        ConnectionState.READY
                                },

                                modifier =
                                    Modifier.weight(1f),

                                label = {
                                    Text("Scale IP")
                                },

                                singleLine = true
                            )

                            OutlinedTextField(

                                value =
                                    port,

                                onValueChange = {

                                    port = it
                                    prefs.scalePort = it

                                    status =
                                        "READY"

                                    connectionState =
                                        ConnectionState.READY
                                },

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

                        OutlinedButton(

                            onClick = {

                                if (isBusy) {
                                    return@OutlinedButton
                                }

                                val portNumber =
                                    port.toIntOrNull()

                                if (portNumber == null) {

                                    status =
                                        "CONNECTION ERROR"

                                    connectionState =
                                        ConnectionState.ERROR

                                    playErrorFeedback(
                                        context
                                    )

                                    return@OutlinedButton
                                }

                                isBusy = true

                                status =
                                    "CONNECTING..."

                                connectionState =
                                    ConnectionState.READY

                                scope.launch {

                                    val result =
                                        transport.testConnection(
                                            host.trim(),
                                            portNumber
                                        )

                                    result.fold(

                                        onSuccess = {

                                            status =
                                                "CONNECTED"

                                            connectionState =
                                                ConnectionState.CONNECTED

                                            isBusy =
                                                false

                                            playConnectedFeedback(
                                                context
                                            )
                                        },

                                        onFailure = {

                                            status =
                                                "CONNECTION ERROR"

                                            connectionState =
                                                ConnectionState.ERROR

                                            isBusy =
                                                false

                                            playErrorFeedback(
                                                context
                                            )
                                        }
                                    )
                                }
                            },

                            enabled =
                                !isBusy,

                            modifier =
                                Modifier.fillMaxWidth(),

                            shape =
                                RoundedCornerShape(6.dp)

                        ) {

                            Text(
                                if (isBusy)
                                    "CONNECTING..."
                                else
                                    "TEST CONNECTION",

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }

                LabelSectionTitle(
                    "LABEL DESIGN",
                    "Choose one of the two preloaded designs"
                )

                TabRow(
                    selectedTabIndex =
                        selectedSlot.ordinal
                ) {

                    LabelDesignStore.Slot.values().forEach { slot ->

                        Tab(
                            selected = selectedSlot == slot,
                            onClick = {
                                selectedSlot = slot
                                selectedFileName =
                                    LabelDesignStore.displayFileName(slot)
                                status = "READY"
                                connectionState = ConnectionState.READY
                            },
                            text = {
                                Text(
                                    slot.title,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(
                        1.dp,
                        scaleBorderColor()
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        Modifier.padding(12.dp)
                    ) {
                        Text(
                            selectedSlot.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = scaleDarkColor()
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(selectedFileName)

                        Spacer(Modifier.height(4.dp))

                        Text(
                            if (selectedSlot ==
                                LabelDesignStore.Slot.WEIGHT_ONLY
                            ) {
                                "Weight-only label"
                            } else {
                                "Weight + ₹ price label"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scaleTextSecondaryColor()
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "text/plain",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "IMPORT / REPLACE DESIGN",
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(

                    colors =
                        mahaMartButtonColors(),

                    onClick = {

                        val portNumber =
                            port.toIntOrNull()

                        if (portNumber == null) {

                            status =
                                "CONNECTION ERROR"

                            connectionState =
                                ConnectionState.ERROR

                            playErrorFeedback(
                                context
                            )

                            return@Button
                        }

                        scope.launch {

                            isBusy = true

                            status =
                                "READING DESIGN..."

                            connectionState =
                                ConnectionState.READY

                            try {

                                val designBytes =
                                    LabelDesignStore.read(
                                        context,
                                        selectedSlot
                                    )

                                if (
                                    designBytes == null ||
                                    designBytes.isEmpty()
                                ) {

                                    status =
                                        "DESIGN READ ERROR"

                                    connectionState =
                                        ConnectionState.ERROR

                                    playErrorFeedback(
                                        context
                                    )

                                    isBusy =
                                        false

                                    return@launch
                                }

                                status =
                                    "UPLOADING..."

                                val result =
                                    transport.uploadLabelDesign(
                                        host.trim(),
                                        portNumber,
                                        designBytes
                                    )

                                result.fold(

                                    onSuccess = {

                                        status =
                                            "UPLOAD COMPLETE"

                                        connectionState =
                                            ConnectionState.CONNECTED

                                        isBusy =
                                            false

                                        playConnectedFeedback(
                                            context
                                        )
                                    },

                                    onFailure = {

                                        status =
                                            "UPLOAD ERROR"

                                        connectionState =
                                            ConnectionState.ERROR

                                        isBusy =
                                            false

                                        playErrorFeedback(
                                            context
                                        )
                                    }
                                )

                            } catch (e: Exception) {

                                status =
                                    "UPLOAD ERROR"

                                connectionState =
                                    ConnectionState.ERROR

                                isBusy =
                                    false

                                playErrorFeedback(
                                    context
                                )
                            }
                        }
                    },

                    enabled =
                        !isBusy,

                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(6.dp)

                ) {

                    Text(
                        if (isBusy)
                            "WORKING..."
                        else
                            "UPLOAD LABEL DESIGN",

                        fontWeight =
                            FontWeight.Bold
                    )
                }

                // ---------------------------------------------------------
                // STATUS
                // ---------------------------------------------------------

                StatusCard(
                    status = status,
                    state = connectionState
                )
            }
        }
    }
}

// ========================================================================
// CONNECTION STATE
// ========================================================================

private enum class ConnectionState {

    READY,

    CONNECTED,

    ERROR
}

// ========================================================================
// STATUS CARD
// ========================================================================

@Composable
private fun StatusCard(
    status: String,
    state: ConnectionState
) {

    val backgroundColor =
        when (state) {

            ConnectionState.READY ->
                Color.Black

            ConnectionState.CONNECTED ->
                Color(0xFF16803A)

            ConnectionState.ERROR ->
                Color(0xFFD00019)
        }

    val displayText =
        when (state) {

            ConnectionState.READY ->
                if (
                    status == "READY" ||
                    status == "CONNECTING..."
                ) {
                    status
                } else {
                    status
                }

            ConnectionState.CONNECTED ->
                status

            ConnectionState.ERROR ->
                status
        }

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    backgroundColor
            ),

        shape =
            RoundedCornerShape(7.dp)

    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 14.dp,
                        vertical = 13.dp
                    ),

            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            Text(
                "●",

                color =
                    Color.White,

                fontWeight =
                    FontWeight.Bold
            )

            Column {

                Text(
                    "STATUS",

                    color =
                        Color.White,

                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,

                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    displayText,

                    color =
                        Color.White,

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

// ========================================================================
// SECTION TITLE
// ========================================================================

@Composable
private fun LabelSectionTitle(
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
    }
}

// ========================================================================
// CONNECTED SOUND
// ========================================================================

private fun playConnectedFeedback(
    context: Context
) {

    try {

        val tone =
            ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                100
            )

        /*
         * Positive acknowledgement tone.
         *
         * Approximately 1 second.
         */
        tone.startTone(
            ToneGenerator.TONE_PROP_ACK,
            1000
        )

    } catch (_: Exception) {
        // Ignore audio failure.
    }
}

// ========================================================================
// ERROR SOUND + VIBRATION
// ========================================================================

private fun playErrorFeedback(
    context: Context
) {

    try {

        val tone =
            ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                100
            )

        /*
         * Negative/error acknowledgement.
         */
        tone.startTone(
            ToneGenerator.TONE_PROP_NACK,
            700
        )

    } catch (_: Exception) {
        // Ignore audio failure.
    }

    try {

        val vibrator: Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                val manager =
                    context.getSystemService(
                        Context.VIBRATOR_MANAGER_SERVICE
                    ) as? VibratorManager

                manager?.defaultVibrator

            } else {

                @Suppress("DEPRECATION")
                context.getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as? Vibrator
            }

        vibrator?.let {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                it.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(
                            0,
                            180,
                            100,
                            180
                        ),
                        -1
                    )
                )

            } else {

                @Suppress("DEPRECATION")
                it.vibrate(
                    longArrayOf(
                        0,
                        180,
                        100,
                        180
                    ),
                    -1
                )
            }
        }

    } catch (_: Exception) {
        // Ignore vibration failure.
    }
}

// ========================================================================
// HELPERS
// ========================================================================

private fun finishActivity(
    context: Context
) {

    (context as? ComponentActivity)?.finish()
}

private fun getDisplayName(
    context: Context,
    uri: Uri
): String? {

    val projection =
        arrayOf("_display_name")

    return context.contentResolver
        .query(
            uri,
            projection,
            null,
            null,
            null
        )
        ?.use { cursor ->

            if (cursor.moveToFirst()) {

                cursor.getString(0)

            } else {

                null
            }
        }
}