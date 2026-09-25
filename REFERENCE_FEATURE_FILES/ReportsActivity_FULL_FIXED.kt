package com.mahamart.essae

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahamart.essae.cloud.DailyPriceReport
import com.mahamart.essae.cloud.DailyPriceReportItem
import com.mahamart.essae.cloud.SupabaseRest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReportsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ReportsScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsScreen() {

    val context = LocalContext.current

    val api = remember {
        SupabaseRest(context)
    }

    val scope = rememberCoroutineScope()

    val today = remember {
        SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Calendar.getInstance().time)
    }

    var selectedDate by rememberSaveable {
        mutableStateOf(today)
    }

    var report by remember {
        mutableStateOf<DailyPriceReport?>(null)
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var status by rememberSaveable {
        mutableStateOf("READY")
    }

    fun loadReport() {

        busy = true
        status = "LOADING..."

        scope.launch {

            api.getProfile().fold(

                onSuccess = { profile ->

                    if (
                        !profile.active ||
                        !profile.role.equals(
                            "ADMIN",
                            ignoreCase = true
                        )
                    ) {
                        busy = false
                        status = "ADMIN ACCESS REQUIRED"
                        return@fold
                    }

                    api.getDailyPriceReport(
                        selectedDate
                    ).fold(

                        onSuccess = {
                            report = it
                            busy = false
                            status = "REPORT LOADED"
                        },

                        onFailure = {
                            busy = false
                            status =
                                "REPORT FAILED: ${
                                    it.message ?: "Unknown error"
                                }"
                        }
                    )
                },

                onFailure = {
                    busy = false
                    status =
                        "SESSION ERROR: ${
                            it.message ?: "Please login again."
                        }"
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        loadReport()
    }

    fun showDatePicker() {

        val current =
            runCatching {
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                ).parse(selectedDate)
            }.getOrNull()
                ?: Calendar.getInstance().time

        val cal =
            Calendar.getInstance().apply {
                time = current
            }

        DatePickerDialog(
            context,
            { _, year, month, day ->
                selectedDate =
                    String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        year,
                        month + 1,
                        day
                    )
                loadReport()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
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
                                containerColor = Color.White,
                                titleContentColor = scaleDarkColor(),
                                navigationIconContentColor =
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
                                "Reports",
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
                            onClick = { (context as? android.app.Activity)?.finish() }
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

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(
                            horizontal = 14.dp,
                            vertical = 10.dp
                        ),

                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                item {

                    Text(
                        "DAILY REPORT",
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
                        "Admin price updates and store status for the selected day.",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            scaleTextSecondaryColor()
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedButton(
                            onClick = {
                                showDatePicker()
                            },
                            modifier =
                                Modifier.weight(1f),
                            enabled = !busy
                        ) {
                            Text(
                                formatDisplayDate(
                                    selectedDate
                                ),
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                loadReport()
                            },
                            enabled = !busy,
                            colors =
                                mahaMartButtonColors()
                        ) {

                            if (busy) {
                                CircularProgressIndicator(
                                    modifier =
                                        Modifier.height(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("REFRESH")
                            }
                        }
                    }
                }

                report?.let { daily ->

                    item {
                        SummaryGrid(daily)
                    }

                    if (daily.items.isEmpty()) {

                        item {

                            Card(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                colors =
                                    CardDefaults
                                        .cardColors(
                                            containerColor = Color.White
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
                                    "No price update activity found for this date.",
                                    modifier =
                                        Modifier.padding(14.dp),
                                    color =
                                        scaleTextSecondaryColor()
                                )
                            }
                        }

                    } else {

                        item {

                            Text(
                                "PRICE UPDATES",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        items(
                            daily.items,
                            key = {
                                "${it.batchNumber}-${it.pluNo}-${it.storeCode}-${it.createdAt}"
                            }
                        ) { row ->
                            ReportRowCard(row)
                        }
                    }

                } ?: item {

                    if (!busy) {

                        Card(
                            modifier =
                                Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults
                                    .cardColors(
                                        containerColor = Color.White
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
                                status,
                                modifier =
                                    Modifier.padding(14.dp),
                                color =
                                    if (
                                        status.startsWith(
                                            "REPORT FAILED"
                                        )
                                    ) {
                                        scaleRedForUi()
                                    } else {
                                        scaleTextSecondaryColor()
                                    },
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryGrid(
    report: DailyPriceReport
) {

    Column(
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            SummaryCard(
                "BATCHES",
                report.batchCount.toString(),
                Modifier.weight(1f)
            )

            SummaryCard(
                "PLU UPDATES",
                report.priceItemCount.toString(),
                Modifier.weight(1f)
            )

            SummaryCard(
                "STORES",
                report.storeCount.toString(),
                Modifier.weight(1f)
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            SummaryCard(
                "PENDING",
                report.pendingCount.toString(),
                Modifier.weight(1f)
            )

            SummaryCard(
                "DOWNLOADED",
                report.downloadedCount.toString(),
                Modifier.weight(1f)
            )

            SummaryCard(
                "APPLIED",
                report.appliedCount.toString(),
                Modifier.weight(1f)
            )

            SummaryCard(
                "FAILED",
                report.failedCount.toString(),
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier
) {

    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White
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
            modifier =
                Modifier.padding(10.dp),
            verticalArrangement =
                Arrangement.spacedBy(3.dp)
        ) {

            Text(
                title,
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    scaleTextSecondaryColor(),
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                value,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReportRowCard(
    row: DailyPriceReportItem
) {

    val statusColor =
        when {
            row.status.equals(
                "APPLIED",
                ignoreCase = true
            ) -> Color(0xFF16803A)

            row.status.equals(
                "FAILED",
                ignoreCase = true
            ) -> scaleRedForUi()

            else -> Color(0xFF8A6400)
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
                scaleBorderColor()
            ),
        shape =
            RoundedCornerShape(8.dp)
    ) {

        Column(
            modifier =
                Modifier.padding(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(4.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    "PLU ${row.pluNo}  ${row.pluName}",
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    row.status,
                    color = statusColor,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Text(
                "${row.storeCode} • ${row.storeName}",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall,
                color =
                    scaleTextSecondaryColor()
            )

            Text(
                "₹${String.format(java.util.Locale.US, "%.2f", row.oldPrice)}  →  ₹${String.format(java.util.Locale.US, "%.2f", row.newPrice)}",
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "Batch ${row.batchNumber}",
                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
                color =
                    scaleTextSecondaryColor()
            )

            row.errorMessage?.let { error ->

                Text(
                    error,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        scaleRedForUi()
                )
            }
        }
    }
}

private fun formatDisplayDate(
    iso: String
): String {

    val parsed =
        runCatching {
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).parse(iso)
        }.getOrNull()
            ?: return iso

    return SimpleDateFormat(
        "dd-MM-yyyy",
        Locale.US
    ).format(parsed)
}
