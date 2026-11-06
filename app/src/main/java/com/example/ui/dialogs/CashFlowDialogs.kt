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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.entity.CustomCategoryEntity
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
    currentNotifyAvg: Boolean,
    currentNotifyWorst: Boolean,
    currentLookaheadDays: Int,
    currentWorstCaseDays: Int = 60,
    onConfirm: (threshold: Double, notifyAvg: Boolean, notifyWorst: Boolean, lookaheadDays: Int, worstCaseDays: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var thresholdText by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentThreshold)) }
    var notifyAvg by remember { mutableStateOf(currentNotifyAvg) }
    var notifyWorst by remember { mutableStateOf(currentNotifyWorst) }
    var lookaheadDays by remember { mutableIntStateOf(currentLookaheadDays) }
    var lookaheadText by remember { mutableStateOf(currentLookaheadDays.toString()) }
    var worstCaseDays by remember { mutableIntStateOf(currentWorstCaseDays) }
    var worstCaseDaysText by remember { mutableStateOf(currentWorstCaseDays.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Safety Buffer & Alerts", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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

                Text(
                    "Notification Triggers",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Toggle 1: Average Case
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Average Case Alerts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Alert when average projection breaches buffer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Switch(
                        checked = notifyAvg,
                        onCheckedChange = { notifyAvg = it },
                        modifier = Modifier.testTag("notify_avg_switch")
                    )
                }

                // Toggle 2: Worst Case
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Worst-Case Alerts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Alert when maximum cost projection breaches buffer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Switch(
                        checked = notifyWorst,
                        onCheckedChange = { notifyWorst = it },
                        modifier = Modifier.testTag("notify_worst_switch")
                    )
                }

                // Lookahead Horizon Configuration
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Notification Lookahead Window",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Only trigger notifications if a cash breach is projected within this many days ahead.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = lookaheadText,
                        onValueChange = { input ->
                            lookaheadText = input
                            val parsed = input.toIntOrNull()
                            if (parsed != null && parsed in 1..365) {
                                lookaheadDays = parsed
                            }
                        },
                        label = { Text("Lookahead Window (Days)") },
                        suffix = { Text("days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("lookahead_days_input")
                    )

                    // Quick Chips for Lookahead
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(7, 14, 30, 60, 90).forEach { days ->
                            FilterChip(
                                selected = lookaheadDays == days,
                                onClick = {
                                    lookaheadDays = days
                                    lookaheadText = days.toString()
                                },
                                label = { Text("${days}d", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Worst-Case Projection Horizon Configuration
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Worst-Case Projection Window",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Only calculate worst-case (maximum cost) projections up to this many days into the future. Beyond this limit, worst-case calculations and graph curve are not computed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = worstCaseDaysText,
                        onValueChange = { input ->
                            worstCaseDaysText = input
                            val parsed = input.toIntOrNull()
                            if (parsed != null && parsed in 1..365) {
                                worstCaseDays = parsed
                            }
                        },
                        label = { Text("Worst-Case Window (Days)") },
                        suffix = { Text("days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("worst_case_days_input")
                    )

                    // Quick Chips for Worst-Case Window
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30, 60, 90, 120, 180).forEach { days ->
                            FilterChip(
                                selected = worstCaseDays == days,
                                onClick = {
                                    worstCaseDays = days
                                    worstCaseDaysText = days.toString()
                                },
                                label = { Text("${days}d", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull()
                    val parsedLookahead = lookaheadText.toIntOrNull() ?: lookaheadDays
                    val parsedWorstCase = worstCaseDaysText.toIntOrNull() ?: worstCaseDays
                    if (parsed == null || parsed < 0) {
                        errorMessage = "Please enter a valid positive threshold"
                    } else if (parsedLookahead < 1 || parsedLookahead > 365) {
                        errorMessage = "Lookahead must be between 1 and 365 days"
                    } else if (parsedWorstCase < 1 || parsedWorstCase > 365) {
                        errorMessage = "Worst-case window must be between 1 and 365 days"
                    } else {
                        onConfirm(parsed, notifyAvg, notifyWorst, parsedLookahead, parsedWorstCase)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditItemDialog(
    item: CashFlowItemEntity,
    availableExpenseCategories: List<String> = emptyList(),
    availableIncomeCategories: List<String> = emptyList(),
    onSave: (CashFlowItemEntity) -> Unit,
    onAddNewCategory: ((String, String) -> Unit)? = null,
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

    val activeExpenseCategories = remember(availableExpenseCategories) {
        if (availableExpenseCategories.isNotEmpty()) availableExpenseCategories
        else listOf("Housing", "Utilities", "Food", "Transportation", "Health", "Insurance", "Entertainment", "Debt", "Personal", "Other")
    }

    val activeIncomeCategories = remember(availableIncomeCategories) {
        if (availableIncomeCategories.isNotEmpty()) availableIncomeCategories
        else listOf("Salary", "Freelance", "Investment", "Bonus", "Rental", "Other")
    }

    val currentCategories = if (type == TransactionType.INCOME) activeIncomeCategories else activeExpenseCategories

    var category by remember {
        mutableStateOf(item.category.ifBlank { currentCategories.firstOrNull() ?: "Other" })
    }
    var isAddingNewCategory by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(item.notes) }
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
                            if (!activeExpenseCategories.contains(category)) {
                                category = activeExpenseCategories.firstOrNull() ?: "Housing"
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Expense", color = if (type == TransactionType.EXPENSE) ExpenseRed else MaterialTheme.colorScheme.onSurface)
                    }
                    SegmentedButton(
                        selected = type == TransactionType.INCOME,
                        onClick = {
                            type = TransactionType.INCOME
                            if (!activeIncomeCategories.contains(category)) {
                                category = activeIncomeCategories.firstOrNull() ?: "Salary"
                            }
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

                // Category Chips with FlowRow and inline New Category chip
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        if (category.isNotBlank()) {
                            Text("Selected: $category", style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary)
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        currentCategories.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                modifier = Modifier.testTag("item_category_$cat")
                            )
                        }

                        // Inline Quick Add Category Chip
                        FilterChip(
                            selected = isAddingNewCategory,
                            onClick = { isAddingNewCategory = !isAddingNewCategory },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            label = { Text("New...", fontSize = 11.sp) },
                            modifier = Modifier.testTag("add_new_category_chip")
                        )
                    }

                    if (isAddingNewCategory) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newCategoryText,
                                onValueChange = { newCategoryText = it },
                                label = { Text("New Category Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("new_category_name_input")
                            )
                            Button(
                                onClick = {
                                    val trimmed = newCategoryText.trim()
                                    if (trimmed.isNotBlank()) {
                                        onAddNewCategory?.invoke(trimmed, type.name)
                                        category = trimmed
                                        newCategoryText = ""
                                        isAddingNewCategory = false
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("confirm_new_category_button")
                            ) {
                                Text("Add")
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordActualDialog(
    event: CashFlowEvent,
    onSaveActual: (actualAmount: Double, actualDate: LocalDate, notes: String) -> Unit,
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
    var actualDate by remember {
        mutableStateOf(event.actualDate ?: event.date)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(event.notes) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = actualDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        actualDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
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
            Text("Update with Actual Amount", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Originally scheduled for: ${event.scheduledDate.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy"))}",
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

                // Actual Amount
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

                // Editable Actual Date
                OutlinedTextField(
                    value = actualDate.format(DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Actual Transaction Date") },
                    trailingIcon = {
                        IconButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.testTag("actual_date_picker_button")
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Select Actual Date")
                        }
                    },
                    supportingText = {
                        if (actualDate != event.scheduledDate) {
                            Text(
                                "Adjusted from scheduled date: ${event.scheduledDate.format(DateTimeFormatter.ofPattern("MMM dd"))}",
                                color = EmeraldPrimary
                            )
                        } else {
                            Text("Tap calendar icon to change date paid/received")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .testTag("actual_date_input")
                )

                // Notes
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
                        onSaveActual(parsed, actualDate, notes)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageCategoriesDialog(
    categories: List<CustomCategoryEntity>,
    onAddCategory: (String, String) -> Unit,
    onRenameCategory: (String, String, String) -> Unit,
    onDeleteCategory: (CustomCategoryEntity) -> Unit,
    onResetToDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var newCategoryName by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<CustomCategoryEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filteredCategories = categories.filter {
        it.type.equals(selectedType.name, ignoreCase = true)
    }.sortedWith(compareBy({ !it.isDefault }, { it.name }))

    // Sub-dialog to rename a category
    if (editingCategory != null) {
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("Rename Category", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Rename '${editingCategory!!.name}'. Any items using this category will automatically be updated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("New Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_category_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotBlank()) {
                            onRenameCategory(editingCategory!!.name, trimmed, selectedType.name)
                            editingCategory = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_category_button")
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = EmeraldPrimary
                )
                Text("Configure Categories", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Add, rename, or delete transaction categories. Expense categories help organize and project your spending accurately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Type Toggle: Expense vs Income
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = {
                            selectedType = TransactionType.EXPENSE
                            errorMessage = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(
                            "Expenses (${categories.count { it.type.equals("EXPENSE", ignoreCase = true) }})",
                            color = if (selectedType == TransactionType.EXPENSE) ExpenseRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    SegmentedButton(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = {
                            selectedType = TransactionType.INCOME
                            errorMessage = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(
                            "Income (${categories.count { it.type.equals("INCOME", ignoreCase = true) }})",
                            color = if (selectedType == TransactionType.INCOME) IncomeGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Add Category Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = {
                            newCategoryName = it
                            errorMessage = null
                        },
                        label = { Text("New ${selectedType.name.lowercase().replaceFirstChar { it.uppercase() }} Category") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add_category_text_input")
                    )

                    Button(
                        onClick = {
                            val trimmed = newCategoryName.trim()
                            if (trimmed.isBlank()) {
                                errorMessage = "Please enter a category name"
                            } else if (filteredCategories.any { it.name.equals(trimmed, ignoreCase = true) }) {
                                errorMessage = "Category '$trimmed' already exists"
                            } else {
                                onAddCategory(trimmed, selectedType.name)
                                newCategoryName = ""
                                errorMessage = null
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("submit_add_category_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                errorMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = ThresholdDanger)
                }

                Text(
                    text = "${selectedType.name.lowercase().replaceFirstChar { it.uppercase() }} Categories (${filteredCategories.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // List of Categories
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    filteredCategories.forEach { cat ->
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().testTag("manage_category_row_${cat.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = cat.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (cat.isDefault) {
                                        androidx.compose.material3.Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "Default",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingCategory = cat
                                            renameText = cat.name
                                        },
                                        modifier = Modifier.size(32.dp).testTag("edit_category_${cat.name}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Rename ${cat.name}",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Allow deletion of non-Other categories
                                    if (!cat.name.equals("Other", ignoreCase = true)) {
                                        IconButton(
                                            onClick = { onDeleteCategory(cat) },
                                            modifier = Modifier.size(32.dp).testTag("delete_category_${cat.name}")
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete ${cat.name}",
                                                modifier = Modifier.size(16.dp),
                                                tint = ThresholdDanger
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Reset to defaults
                TextButton(
                    onClick = onResetToDefaults,
                    modifier = Modifier.fillMaxWidth().testTag("reset_categories_defaults_button")
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset All Categories to Defaults", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_manage_categories_button")
            ) {
                Text("Done")
            }
        }
    )
}
