package com.example.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CashFlowItemEntity
import com.example.domain.engine.CashFlowEvent
import com.example.domain.model.PeriodType
import com.example.domain.model.TransactionType
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.ThresholdDanger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartingCashDialog(
    currentAmount: Double,
    currentDate: LocalDate,
    onConfirm: (amount: Double, date: LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentAmount)) }
    var selectedDate by remember { mutableStateOf(currentDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Starting Cash Balance", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Enter your current starting cash or liquid balance and effective starting date for projections.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Starting Balance ($)") },
                    prefix = { Text("$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("starting_cash_input")
                )

                OutlinedTextField(
                    value = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("As of Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountText.toDoubleOrNull()
                    if (parsed == null) {
                        errorMessage = "Please enter a valid numeric amount"
                    } else {
                        onConfirm(parsed, selectedDate)
                    }
                },
                modifier = Modifier.testTag("save_starting_cash_button")
            ) {
                Text("Save Balance")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SafetyThresholdDialog(
    currentThreshold: Double,
    currentNotify: Boolean,
    onConfirm: (threshold: Double, notify: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var thresholdText by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentThreshold)) }
    var notify by remember { mutableStateOf(currentNotify) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Safety Buffer & Alerts", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Set the minimum cash amount you wish to maintain. The app will warn you if projected cash flow dips below this safety floor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = {
                        thresholdText = it
                        errorMessage = null
                    },
                    label = { Text("Minimum Cash Safety Threshold ($)") },
                    prefix = { Text("$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("threshold_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = EmeraldPrimary
                        )
                        Column {
                            Text("Threshold Notifications", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Alert when projection breaches buffer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Switch(
                        checked = notify,
                        onCheckedChange = { notify = it },
                        modifier = Modifier.testTag("notify_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull()
                    if (parsed == null || parsed < 0) {
                        errorMessage = "Please enter a valid positive threshold"
                    } else {
                        onConfirm(parsed, notify)
                    }
                },
                modifier = Modifier.testTag("save_threshold_button")
            ) {
                Text("Save Threshold")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemDialog(
    item: CashFlowItemEntity,
    onSave: (CashFlowItemEntity) -> Unit,
    onDelete: ((CashFlowItemEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isNew = item.id == 0L
    var title by remember { mutableStateOf(item.title) }
    var type by remember { mutableStateOf(TransactionType.valueOf(item.type.uppercase())) }
    var isPeriodic by remember { mutableStateOf(item.isPeriodic) }
    var period by remember { mutableStateOf(PeriodType.fromString(item.period).let { if (it == PeriodType.NONE) PeriodType.MONTHLY else it }) }
    var avgAmountText by remember { mutableStateOf(if (item.averageAmount > 0) String.format(Locale.US, "%.2f", item.averageAmount) else "") }
    var maxAmountText by remember { mutableStateOf(if (item.maximumAmount > 0) String.format(Locale.US, "%.2f", item.maximumAmount) else "") }
    var selectedDate by remember { mutableStateOf(LocalDate.ofEpochDay(item.startDateEpochDay)) }
    var category by remember { mutableStateOf(item.category.ifBlank { if (type == TransactionType.INCOME) "Salary" else "Housing" }) }
    var notes by remember { mutableStateOf(item.notes) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = if (type == TransactionType.INCOME) {
        listOf("Salary", "Freelance", "Investment", "Bonus", "Rental", "Other")
    } else {
        listOf("Housing", "Utilities", "Food", "Transportation", "Health", "Insurance", "Entertainment", "Debt", "Other")
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNew) "Add Cash Flow Item" else "Edit ${item.title}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!isNew && onDelete != null) {
                    IconButton(
                        onClick = { onDelete(item) },
                        modifier = Modifier.size(32.dp).testTag("delete_item_dialog_button")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Item",
                            tint = ThresholdDanger
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Type selector: Expense vs Income
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == TransactionType.EXPENSE,
                        onClick = {
                            type = TransactionType.EXPENSE
                            if (!categories.contains(category)) category = "Housing"
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Expense", color = if (type == TransactionType.EXPENSE) ExpenseRed else MaterialTheme.colorScheme.onSurface)
                    }
                    SegmentedButton(
                        selected = type == TransactionType.INCOME,
                        onClick = {
                            type = TransactionType.INCOME
                            if (!categories.contains(category)) category = "Salary"
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Income", color = if (type == TransactionType.INCOME) IncomeGreen else MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Cadence selector: Recurring vs Adhoc
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = isPeriodic,
                        onClick = { isPeriodic = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Recurring")
                    }
                    SegmentedButton(
                        selected = !isPeriodic,
                        onClick = { isPeriodic = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Ad-hoc / One-time")
                    }
                }

                // Description
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    label = { Text("Description *") },
                    placeholder = { Text("e.g. Apartment Rent, Electric Bill") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("item_title_input")
                )

                // Period if recurring
                if (isPeriodic) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recurrence Period", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                PeriodType.WEEKLY,
                                PeriodType.BI_WEEKLY,
                                PeriodType.MONTHLY
                            ).forEach { p ->
                                FilterChip(
                                    selected = period == p,
                                    onClick = { period = p },
                                    label = { Text(p.label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                PeriodType.BI_ANNUAL,
                                PeriodType.ANNUAL
                            ).forEach { p ->
                                FilterChip(
                                    selected = period == p,
                                    onClick = { period = p },
                                    label = { Text(p.label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Cost Inputs: Average and Maximum
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = avgAmountText,
                        onValueChange = {
                            avgAmountText = it
                            errorMessage = null
                            // If max is blank, auto-populate with same
                            if (maxAmountText.isBlank()) maxAmountText = it
                        },
                        label = { Text("Average Amount *") },
                        prefix = { Text("$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("item_avg_amount_input")
                    )

                    OutlinedTextField(
                        value = maxAmountText,
                        onValueChange = {
                            maxAmountText = it
                            errorMessage = null
                        },
                        label = { Text("Maximum Amount *") },
                        prefix = { Text("$ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("item_max_amount_input")
                    )
                }

                Text(
                    text = "Tip: Maximum amount is used to project your worst-case cash flow scenario.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Date
                OutlinedTextField(
                    value = selectedDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isPeriodic) "Anchor / Start Date" else "Transaction Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )

                // Category Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.take(3).forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.drop(3).take(3).forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = ThresholdDanger
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Please enter a description"
                        return@Button
                    }
                    val avgParsed = avgAmountText.toDoubleOrNull()
                    val maxParsed = maxAmountText.toDoubleOrNull()

                    if (avgParsed == null || avgParsed < 0) {
                        errorMessage = "Please enter a valid average amount"
                        return@Button
                    }
                    val finalMax = maxParsed ?: avgParsed

                    val updatedItem = item.copy(
                        title = title.trim(),
                        type = type.name,
                        isPeriodic = isPeriodic,
                        period = if (isPeriodic) period.name else PeriodType.NONE.name,
                        averageAmount = avgParsed,
                        maximumAmount = finalMax,
                        startDateEpochDay = selectedDate.toEpochDay(),
                        category = category,
                        notes = notes.trim()
                    )
                    onSave(updatedItem)
                },
                modifier = Modifier.testTag("save_item_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RecordActualDialog(
    event: CashFlowEvent,
    onSaveActual: (actualAmount: Double, notes: String) -> Unit,
    onRemoveActual: () -> Unit,
    onDismiss: () -> Unit
) {
    var actualAmountText by remember {
        mutableStateOf(
            if (event.isActual && event.actualAmount != null) {
                String.format(Locale.US, "%.2f", event.actualAmount)
            } else {
                String.format(Locale.US, "%.2f", event.avgAmount)
            }
        )
    }
    var notes by remember { mutableStateOf(event.notes) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Update with Actual Amount", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Scheduled for: ${event.date.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Estimated Avg: $${String.format(Locale.US, "%.2f", event.avgAmount)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Estimated Max: $${String.format(Locale.US, "%.2f", event.maxAmount)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = actualAmountText,
                    onValueChange = {
                        actualAmountText = it
                        errorMessage = null
                    },
                    label = { Text("Actual Amount Incurred ($)") },
                    prefix = { Text("$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("actual_amount_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Actual Notes (e.g. invoice #, receipt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (event.isActual) {
                    TextButton(
                        onClick = onRemoveActual,
                        colors = ButtonDefaults.textButtonColors(contentColor = ThresholdDanger),
                        modifier = Modifier.align(Alignment.End).testTag("revert_to_estimate_button")
                    ) {
                        Text("Revert to Estimate")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = actualAmountText.toDoubleOrNull()
                    if (parsed == null || parsed < 0) {
                        errorMessage = "Please enter a valid amount"
                    } else {
                        onSaveActual(parsed, notes)
                    }
                },
                modifier = Modifier.testTag("save_actual_button")
            ) {
                Text("Save Actual")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
