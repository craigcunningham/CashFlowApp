package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.engine.CashFlowEvent
import com.example.domain.model.TransactionType
import com.example.ui.CashFlowUiState
import com.example.ui.ScenarioFilter
import com.example.ui.components.CashFlowChart
import com.example.ui.components.SummaryKpiCards
import com.example.ui.components.ThresholdAlertBanner
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.ThresholdDanger
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    uiState: CashFlowUiState,
    onSelectHorizon: (Int) -> Unit,
    onSelectScenarioFilter: (ScenarioFilter) -> Unit,
    onDismissBanner: () -> Unit,
    onViewInLedger: () -> Unit,
    onEditStartingCash: () -> Unit,
    onAdjustThreshold: () -> Unit,
    onRecordActual: (CashFlowEvent) -> Unit,
    onAddItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val projection = uiState.projection

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("dashboard_screen_scroll"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Horizon selector pills: 1M, 3M, 6M, 1Y
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Forecast Horizon:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            1 to "1M",
                            3 to "3M",
                            6 to "6M",
                            12 to "1Y"
                        ).forEach { (months, label) ->
                            FilterChip(
                                selected = uiState.selectedHorizonMonths == months,
                                onClick = { onSelectHorizon(months) },
                                label = { Text(label, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.testTag("horizon_chip_$label")
                            )
                        }
                    }
                }
            }

            // Threshold warning banner if triggered
            if (projection != null) {
                item {
                    ThresholdAlertBanner(
                        projection = projection,
                        isDismissed = uiState.bannerDismissed,
                        onDismiss = onDismissBanner,
                        onViewInLedger = onViewInLedger,
                        onAdjustThreshold = onAdjustThreshold
                    )
                }

                // Interactive Chart
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Cash Flow Curve",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Average vs Worst-Case Scenario",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Scenario toggle
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = uiState.scenarioFilter == ScenarioFilter.BOTH,
                                        onClick = { onSelectScenarioFilter(ScenarioFilter.BOTH) },
                                        label = { Text("Both", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = uiState.scenarioFilter == ScenarioFilter.AVERAGE,
                                        onClick = { onSelectScenarioFilter(ScenarioFilter.AVERAGE) },
                                        label = { Text("Avg", fontSize = 10.sp) }
                                    )
                                    FilterChip(
                                        selected = uiState.scenarioFilter == ScenarioFilter.WORST_CASE,
                                        onClick = { onSelectScenarioFilter(ScenarioFilter.WORST_CASE) },
                                        label = { Text("Max", fontSize = 10.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            CashFlowChart(
                                dailyPoints = projection.dailyPoints,
                                minimumThreshold = projection.minimumThreshold,
                                scenarioFilter = uiState.scenarioFilter
                            )
                        }
                    }
                }

                // Summary KPI Cards
                item {
                    SummaryKpiCards(
                        projection = projection,
                        onEditStartingCash = onEditStartingCash
                    )
                }

                // Upcoming schedule section
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Next Scheduled Transactions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onViewInLedger) {
                            Text("View All (${projection.allEvents.size})", fontSize = 12.sp)
                        }
                    }
                }

                val upcomingEvents = projection.allEvents.take(6)
                if (upcomingEvents.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No upcoming transactions. Tap + to add periodic income or expense.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(upcomingEvents, key = { it.id }) { event ->
                        DashboardEventRow(
                            event = event,
                            minimumThreshold = projection.minimumThreshold,
                            onRecordActual = { onRecordActual(event) }
                        )
                    }
                }
            }
        }

        // Floating Action Button to add income/expense
        FloatingActionButton(
            onClick = onAddItem,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("dashboard_add_item_fab"),
            containerColor = EmeraldPrimary,
            contentColor = androidx.compose.ui.graphics.Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Cash Flow Item")
        }
    }
}

@Composable
fun DashboardEventRow(
    event: CashFlowEvent,
    minimumThreshold: Double,
    onRecordActual: () -> Unit
) {
    val isIncome = event.type == TransactionType.INCOME
    val isBelowThreshold = event.runningAvgBalance < minimumThreshold || event.runningMaxBalance < minimumThreshold

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable { onRecordActual() }
            .testTag("dashboard_event_${event.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBelowThreshold) ThresholdDanger.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circle category/type icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isIncome) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isIncome) "+" else "−",
                        color = if (isIncome) IncomeGreen else ExpenseRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
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

                    val dateText = if (event.isDateRescheduled) {
                        "Paid/Recv: ${event.date.format(DateTimeFormatter.ofPattern("MMM dd"))} (was ${event.scheduledDate.format(DateTimeFormatter.ofPattern("MMM dd"))}) • ${if (event.isPeriodic) event.period.label else "Ad-hoc"} • ${event.category}"
                    } else {
                        "${event.date.format(DateTimeFormatter.ofPattern("MMM dd"))} • ${if (event.isPeriodic) event.period.label else "Ad-hoc"} • ${event.category}"
                    }

                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, if (isIncome) "+$%,.2f" else "−$%,.2f", event.effectiveAvgAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) IncomeGreen else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Bal: ${String.format(Locale.US, "$%,.0f", event.runningAvgBalance)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isBelowThreshold) ThresholdDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isBelowThreshold) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
