package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.CashFlowEvent
import com.example.domain.engine.ProjectionResult
import com.example.domain.model.TransactionType
import com.example.ui.CashFlowUiState
import com.example.ui.TableFilter
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.ThresholdDanger
import com.example.ui.theme.WorstCaseAmber
import com.example.util.CsvExporter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TableScreen(
    uiState: CashFlowUiState,
    onSetTableFilter: (TableFilter) -> Unit,
    onRecordActual: (CashFlowEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val projection = uiState.projection ?: return

    val filteredEvents = projection.allEvents.filter { event ->
        when (uiState.tableFilter) {
            TableFilter.ALL -> true
            TableFilter.EXPENSES -> event.type == TransactionType.EXPENSE
            TableFilter.INCOME -> event.type == TransactionType.INCOME
            TableFilter.ACTUALS -> event.isActual
            TableFilter.WARNINGS -> event.runningAvgBalance < projection.minimumThreshold || (event.isWorstCaseActive && event.runningMaxBalance < projection.minimumThreshold)
        }
    }

    val today = remember { LocalDate.now() }

    // Find the index of the first item for the current date (or first item on/after today)
    val todayTargetIndex = remember(filteredEvents, today) {
        val exactIndex = filteredEvents.indexOfFirst { it.date == today }
        if (exactIndex != -1) {
            exactIndex
        } else {
            val nextIndex = filteredEvents.indexOfFirst { it.date.isAfter(today) }
            if (nextIndex != -1) nextIndex else (if (filteredEvents.isNotEmpty()) 0 else -1)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val initialScrollIndex = remember(todayTargetIndex) {
        if (todayTargetIndex in filteredEvents.indices) todayTargetIndex else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)

    // Automatically scroll to the first item for the current date when entering the ledger or changing filter
    LaunchedEffect(uiState.tableFilter, todayTargetIndex) {
        if (todayTargetIndex in filteredEvents.indices) {
            listState.scrollToItem(todayTargetIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("table_screen_container")
    ) {
        // Controls header: filter chips & export CSV button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TableFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.tableFilter == filter,
                        onClick = { onSetTableFilter(filter) },
                        label = { Text(filter.label, fontSize = 11.sp) },
                        modifier = Modifier.testTag("table_filter_${filter.name}")
                    )
                }
            }

            if (todayTargetIndex in filteredEvents.indices) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(todayTargetIndex)
                        }
                    },
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .testTag("scroll_to_today_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = "Scroll to today",
                        modifier = Modifier.size(16.dp),
                        tint = EmeraldPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Today", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                }
            }

            OutlinedButton(
                onClick = {
                    val file = CsvExporter.exportScheduleToCsv(context, projection)
                    CsvExporter.shareCsvFile(context, file, "Export Cash Flow Schedule")
                },
                modifier = Modifier
                    .padding(start = 4.dp)
                    .testTag("export_csv_table_button"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Summary bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing ${filteredEvents.size} of ${projection.allEvents.size} transactions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Safety: $${String.format(Locale.US, "%,.0f", projection.minimumThreshold)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ThresholdDanger
                )
            }
        }

        if (filteredEvents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No transactions match the selected filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("table_events_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredEvents, key = { it.id }) { event ->
                    TableLedgerCard(
                        event = event,
                        minimumThreshold = projection.minimumThreshold,
                        onRecordActual = { onRecordActual(event) }
                    )
                }
            }
        }
    }
}

@Composable
fun TableLedgerCard(
    event: CashFlowEvent,
    minimumThreshold: Double,
    onRecordActual: () -> Unit
) {
    val isToday = event.date == LocalDate.now()
    val isIncome = event.type == TransactionType.INCOME
    val isAvgBelow = event.runningAvgBalance < minimumThreshold
    val isMaxBelow = event.isWorstCaseActive && (event.runningMaxBalance < minimumThreshold)
    val isWarning = isAvgBelow || isMaxBelow

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRecordActual() }
            .testTag("ledger_row_${event.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isWarning -> ThresholdDanger.copy(alpha = 0.08f)
                isToday -> EmeraldPrimary.copy(alpha = 0.06f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = when {
            isWarning -> androidx.compose.foundation.BorderStroke(1.dp, ThresholdDanger.copy(alpha = 0.4f))
            isToday -> androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldPrimary.copy(alpha = 0.5f))
            else -> null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Date and Status Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = event.date.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isToday) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = EmeraldPrimary
                    ) {
                        Text(
                            text = "TODAY",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (event.isPeriodic) event.period.label else "Ad-hoc",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                if (event.isActual) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = EmeraldPrimary.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = if (event.isDateRescheduled) "ACTUAL (RESCHEDULED)" else "ACTUAL",
                            color = EmeraldPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Row 2: Title & Category on left, Transaction Amount on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = event.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (event.isDateRescheduled) {
                            Text(
                                text = "• Orig: ${event.scheduledDate.format(DateTimeFormatter.ofPattern("MMM dd"))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = String.format(Locale.US, if (isIncome) "+$%,.2f" else "−$%,.2f", event.effectiveAvgAmount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isIncome) IncomeGreen else ExpenseRed
                    )
                    if (event.isActual && event.effectiveAvgAmount != event.avgAmount) {
                        Text(
                            text = "Est: ${String.format(Locale.US, if (isIncome) "+$%,.2f" else "−$%,.2f", event.avgAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Row 3: Running Balances & Actual Update Action
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("Running Avg", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = String.format(Locale.US, "$%,.2f", event.runningAvgBalance),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAvgBelow) ThresholdDanger else EmeraldPrimary
                            )
                        }

                        Column {
                            Text("Running Worst-Case", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (event.isWorstCaseActive) {
                                    String.format(Locale.US, "$%,.2f", event.runningMaxBalance)
                                } else {
                                    "—"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (event.isWorstCaseActive) {
                                    if (isMaxBelow) ThresholdDanger else WorstCaseAmber
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }

                    // Button to record / update actual
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .clickable { onRecordActual() }
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (event.isActual) Icons.Default.CheckCircle else Icons.Default.Edit,
                                contentDescription = null,
                                tint = if (event.isActual) EmeraldPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (event.isActual) "Edit Actual" else "Enter Actual",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (event.isActual) EmeraldPrimary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
