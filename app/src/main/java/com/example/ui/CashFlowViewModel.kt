package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ActualAdjustmentEntity
import com.example.data.local.entity.CashFlowItemEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.data.repository.CashFlowRepository
import com.example.domain.engine.CashFlowEvent
import com.example.domain.engine.CashFlowProjectionEngine
import com.example.domain.engine.ProjectionResult
import com.example.domain.model.PeriodType
import com.example.domain.model.TransactionType
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class AppTab(val label: String) {
    DASHBOARD("Projection"),
    TABLE("Ledger"),
    ITEMS("Recurring & Adhoc"),
    SETTINGS("Settings & Export")
}

enum class TableFilter(val label: String) {
    ALL("All"),
    EXPENSES("Expenses"),
    INCOME("Income"),
    ACTUALS("With Actuals"),
    WARNINGS("Warnings Only")
}

enum class ScenarioFilter(val label: String) {
    BOTH("Both Scenarios"),
    AVERAGE("Average Only"),
    WORST_CASE("Worst-Case Only")
}

data class CashFlowUiState(
    val isLoading: Boolean = true,
    val settings: UserSettingsEntity = UserSettingsEntity(),
    val items: List<CashFlowItemEntity> = emptyList(),
    val actuals: List<ActualAdjustmentEntity> = emptyList(),
    val projection: ProjectionResult? = null,
    val selectedTab: AppTab = AppTab.DASHBOARD,
    val selectedHorizonMonths: Int = 6,
    val tableFilter: TableFilter = TableFilter.ALL,
    val scenarioFilter: ScenarioFilter = ScenarioFilter.BOTH,
    val bannerDismissed: Boolean = false,
    val editingItem: CashFlowItemEntity? = null,
    val isItemDialogVisible: Boolean = false,
    val recordingActualEvent: CashFlowEvent? = null,
    val isActualDialogVisible: Boolean = false,
    val isStartingCashDialogVisible: Boolean = false,
    val isThresholdDialogVisible: Boolean = false,
    val userFeedbackMessage: String? = null
)

class CashFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CashFlowRepository

    private val _selectedTab = MutableStateFlow(AppTab.DASHBOARD)
    private val _selectedHorizonMonths = MutableStateFlow(6)
    private val _tableFilter = MutableStateFlow(TableFilter.ALL)
    private val _scenarioFilter = MutableStateFlow(ScenarioFilter.BOTH)
    private val _bannerDismissed = MutableStateFlow(false)
    private val _editingItem = MutableStateFlow<CashFlowItemEntity?>(null)
    private val _isItemDialogVisible = MutableStateFlow(false)
    private val _recordingActualEvent = MutableStateFlow<CashFlowEvent?>(null)
    private val _isActualDialogVisible = MutableStateFlow(false)
    private val _isStartingCashDialogVisible = MutableStateFlow(false)
    private val _isThresholdDialogVisible = MutableStateFlow(false)
    private val _userFeedbackMessage = MutableStateFlow<String?>(null)

    private var lastNotifiedBreachEpochDay: Long = -1L

    val uiState: StateFlow<CashFlowUiState>

    init {
        val db = AppDatabase.getInstance(application)
        repository = CashFlowRepository(db.cashFlowDao())

        viewModelScope.launch {
            repository.ensureInitialized()
        }

        uiState = combine(
            repository.userSettings,
            repository.allItems,
            repository.allActuals,
            _selectedTab,
            _selectedHorizonMonths,
            _tableFilter,
            _scenarioFilter,
            _bannerDismissed,
            _editingItem,
            _isItemDialogVisible,
            _recordingActualEvent,
            _isActualDialogVisible,
            _isStartingCashDialogVisible,
            _isThresholdDialogVisible,
            _userFeedbackMessage
        ) { args ->
            val settings = (args[0] as? UserSettingsEntity) ?: UserSettingsEntity()
            @Suppress("UNCHECKED_CAST")
            val items = (args[1] as? List<CashFlowItemEntity>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val actuals = (args[2] as? List<ActualAdjustmentEntity>) ?: emptyList()
            val tab = args[3] as AppTab
            val horizon = args[4] as Int
            val tFilter = args[5] as TableFilter
            val sFilter = args[6] as ScenarioFilter
            val dismissed = args[7] as Boolean
            val editItem = args[8] as? CashFlowItemEntity
            val itemDialog = args[9] as Boolean
            val recordActual = args[10] as? CashFlowEvent
            val actualDialog = args[11] as Boolean
            val startCashDialog = args[12] as Boolean
            val threshDialog = args[13] as Boolean
            val feedbackMsg = args[14] as? String

            val projection = CashFlowProjectionEngine.calculateProjection(
                settings = settings,
                items = items,
                actuals = actuals,
                horizonMonths = horizon
            )

            // Check if notification should fire according to configured lookahead and scenarios
            val breachInfo = if (settings.notifyBelowThreshold) {
                projection.getBreachWithinLookahead(
                    lookaheadDays = settings.notificationLookaheadDays,
                    checkAvg = settings.notifyAverageCase,
                    checkWorstCase = settings.notifyWorstCase
                )
            } else null

            if (breachInfo != null && breachInfo.breachDate.toEpochDay() != lastNotifiedBreachEpochDay) {
                lastNotifiedBreachEpochDay = breachInfo.breachDate.toEpochDay()
                NotificationHelper.showLowCashAlertNotification(
                    context = getApplication(),
                    threshold = settings.minimumThresholdAmount,
                    projectedAmount = breachInfo.amount,
                    breachDate = breachInfo.breachDate,
                    isWorstCase = breachInfo.isWorstCase,
                    daysAhead = breachInfo.daysAhead
                )
            }

            CashFlowUiState(
                isLoading = false,
                settings = settings,
                items = items,
                actuals = actuals,
                projection = projection,
                selectedTab = tab,
                selectedHorizonMonths = horizon,
                tableFilter = tFilter,
                scenarioFilter = sFilter,
                bannerDismissed = dismissed,
                editingItem = editItem,
                isItemDialogVisible = itemDialog,
                recordingActualEvent = recordActual,
                isActualDialogVisible = actualDialog,
                isStartingCashDialogVisible = startCashDialog,
                isThresholdDialogVisible = threshDialog,
                userFeedbackMessage = feedbackMsg
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CashFlowUiState(isLoading = true)
        )
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun selectHorizon(months: Int) {
        _selectedHorizonMonths.value = months
        viewModelScope.launch {
            repository.updateHorizon(months)
        }
    }

    fun setTableFilter(filter: TableFilter) {
        _tableFilter.value = filter
    }

    fun setScenarioFilter(filter: ScenarioFilter) {
        _scenarioFilter.value = filter
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    fun clearFeedbackMessage() {
        _userFeedbackMessage.value = null
    }

    // --- Starting Cash and Threshold Actions ---
    fun showStartingCashDialog() {
        _isStartingCashDialogVisible.value = true
    }

    fun hideStartingCashDialog() {
        _isStartingCashDialogVisible.value = false
    }

    fun updateStartingCash(amount: Double, date: LocalDate) {
        viewModelScope.launch {
            repository.updateStartingCash(amount, date.toEpochDay())
            _isStartingCashDialogVisible.value = false
            _userFeedbackMessage.value = "Starting balance updated to $${String.format(java.util.Locale.US, "%.2f", amount)}"
        }
    }

    fun showThresholdDialog() {
        _isThresholdDialogVisible.value = true
    }

    fun hideThresholdDialog() {
        _isThresholdDialogVisible.value = false
    }

    fun updateThreshold(
        threshold: Double,
        notifyAverage: Boolean,
        notifyWorstCase: Boolean,
        lookaheadDays: Int
    ) {
        viewModelScope.launch {
            repository.updateThreshold(
                threshold = threshold,
                notifyAverage = notifyAverage,
                notifyWorstCase = notifyWorstCase,
                lookaheadDays = lookaheadDays
            )
            _isThresholdDialogVisible.value = false
            _bannerDismissed.value = false // Re-enable banner if threshold modified
            _userFeedbackMessage.value = "Safety threshold & notification rules updated"
        }
    }

    // --- Item Dialog Actions ---
    fun openAddItemDialog(defaultType: TransactionType = TransactionType.EXPENSE, isPeriodic: Boolean = true) {
        val today = LocalDate.now()
        _editingItem.value = CashFlowItemEntity(
            id = 0L,
            title = "",
            type = defaultType.name,
            isPeriodic = isPeriodic,
            period = if (isPeriodic) PeriodType.MONTHLY.name else PeriodType.NONE.name,
            averageAmount = 0.0,
            maximumAmount = 0.0,
            startDateEpochDay = today.toEpochDay(),
            category = if (defaultType == TransactionType.INCOME) "Salary" else "Housing",
            isActive = true,
            notes = ""
        )
        _isItemDialogVisible.value = true
    }

    fun openEditItemDialog(item: CashFlowItemEntity) {
        _editingItem.value = item
        _isItemDialogVisible.value = true
    }

    fun hideItemDialog() {
        _isItemDialogVisible.value = false
        _editingItem.value = null
    }

    fun saveItem(item: CashFlowItemEntity) {
        viewModelScope.launch {
            repository.saveItem(item)
            _isItemDialogVisible.value = false
            _editingItem.value = null
            _userFeedbackMessage.value = "${item.title} saved successfully"
        }
    }

    fun toggleItemActive(item: CashFlowItemEntity) {
        viewModelScope.launch {
            repository.saveItem(item.copy(isActive = !item.isActive))
        }
    }

    fun deleteItem(item: CashFlowItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item)
            _userFeedbackMessage.value = "${item.title} deleted"
        }
    }

    // --- Actual Adjustments Actions ---
    fun openRecordActualDialog(event: CashFlowEvent) {
        _recordingActualEvent.value = event
        _isActualDialogVisible.value = true
    }

    fun hideRecordActualDialog() {
        _isActualDialogVisible.value = false
        _recordingActualEvent.value = null
    }

    fun saveActual(
        itemId: Long,
        occurrenceDate: LocalDate,
        actualAmount: Double,
        actualDate: LocalDate = occurrenceDate,
        notes: String = ""
    ) {
        viewModelScope.launch {
            repository.recordActual(
                itemId = itemId,
                occurrenceEpochDay = occurrenceDate.toEpochDay(),
                actualAmount = actualAmount,
                actualDateEpochDay = actualDate.toEpochDay(),
                notes = notes
            )
            _isActualDialogVisible.value = false
            _recordingActualEvent.value = null
            _userFeedbackMessage.value = "Actual amount recorded: $${String.format(java.util.Locale.US, "%.2f", actualAmount)}"
        }
    }

    fun removeActual(itemId: Long, occurrenceDate: LocalDate) {
        viewModelScope.launch {
            repository.removeActual(itemId, occurrenceDate.toEpochDay())
            _isActualDialogVisible.value = false
            _recordingActualEvent.value = null
            _userFeedbackMessage.value = "Actual amount removed (reverted to estimate)"
        }
    }

    fun setThemeMode(themeMode: String) {
        viewModelScope.launch {
            repository.updateThemeMode(themeMode)
        }
    }

    fun restoreBackupFromJson(jsonString: String) {
        viewModelScope.launch {
            try {
                val snapshot = com.example.util.DataBackupManager.parseBackupJson(jsonString)
                repository.restoreAllData(snapshot)
                _userFeedbackMessage.value = "Backup restored successfully (${snapshot.items.size} items)"
            } catch (e: Exception) {
                _userFeedbackMessage.value = "Failed to restore backup: ${e.message}"
            }
        }
    }
}
