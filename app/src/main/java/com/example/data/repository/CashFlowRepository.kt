package com.example.data.repository

import com.example.data.local.dao.CashFlowDao
import com.example.data.local.entity.ActualAdjustmentEntity
import com.example.data.local.entity.CashFlowItemEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.domain.model.PeriodType
import com.example.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate

class CashFlowRepository(private val dao: CashFlowDao) {

    val allItems: Flow<List<CashFlowItemEntity>> = dao.getAllItems()
    val allActuals: Flow<List<ActualAdjustmentEntity>> = dao.getAllActuals()
    val userSettings: Flow<UserSettingsEntity?> = dao.getUserSettings()

    suspend fun ensureInitialized() {
        val existingSettings = dao.getUserSettingsDirect()
        if (existingSettings == null) {
            val today = LocalDate.now()
            val initialSettings = UserSettingsEntity(
                id = 1,
                startingCashAmount = 4500.0,
                startingDateEpochDay = today.toEpochDay(),
                minimumThresholdAmount = 1000.0,
                currencySymbol = "$",
                notifyBelowThreshold = true,
                projectionHorizonMonths = 6
            )
            dao.insertOrUpdateSettings(initialSettings)

            // Seed clean realistic starter items so the user immediately gets a preview
            val starterItems = listOf(
                CashFlowItemEntity(
                    title = "Salary Paycheck",
                    type = TransactionType.INCOME.name,
                    isPeriodic = true,
                    period = PeriodType.BI_WEEKLY.name,
                    averageAmount = 2400.0,
                    maximumAmount = 2400.0,
                    startDateEpochDay = today.plusDays(2).toEpochDay(),
                    category = "Salary",
                    notes = "Direct deposit"
                ),
                CashFlowItemEntity(
                    title = "Apartment Rent",
                    type = TransactionType.EXPENSE.name,
                    isPeriodic = true,
                    period = PeriodType.MONTHLY.name,
                    averageAmount = 1450.0,
                    maximumAmount = 1450.0,
                    startDateEpochDay = today.withDayOfMonth(1).let {
                        if (it.isBefore(today)) it.plusMonths(1) else it
                    }.toEpochDay(),
                    category = "Housing",
                    notes = "Fixed lease payment"
                ),
                CashFlowItemEntity(
                    title = "Electric & Utilities",
                    type = TransactionType.EXPENSE.name,
                    isPeriodic = true,
                    period = PeriodType.MONTHLY.name,
                    averageAmount = 120.0,
                    maximumAmount = 190.0, // Variable!
                    startDateEpochDay = today.plusDays(10).toEpochDay(),
                    category = "Utilities",
                    notes = "Summer/winter AC/heating fluctuates"
                ),
                CashFlowItemEntity(
                    title = "Groceries & Household",
                    type = TransactionType.EXPENSE.name,
                    isPeriodic = true,
                    period = PeriodType.WEEKLY.name,
                    averageAmount = 150.0,
                    maximumAmount = 220.0,
                    startDateEpochDay = today.plusDays(4).toEpochDay(),
                    category = "Food",
                    notes = "Weekly grocery store runs"
                ),
                CashFlowItemEntity(
                    title = "Car Insurance",
                    type = TransactionType.EXPENSE.name,
                    isPeriodic = true,
                    period = PeriodType.BI_ANNUAL.name,
                    averageAmount = 600.0,
                    maximumAmount = 650.0,
                    startDateEpochDay = today.plusMonths(2).toEpochDay(),
                    category = "Transportation",
                    notes = "6-month premium"
                ),
                CashFlowItemEntity(
                    title = "High-Yield Savings Interest",
                    type = TransactionType.INCOME.name,
                    isPeriodic = true,
                    period = PeriodType.MONTHLY.name,
                    averageAmount = 45.0,
                    maximumAmount = 45.0,
                    startDateEpochDay = today.plusDays(15).toEpochDay(),
                    category = "Investment",
                    notes = "Monthly yield"
                )
            )
            dao.insertItems(starterItems)
        }
    }

    suspend fun saveItem(item: CashFlowItemEntity): Long {
        return if (item.id == 0L) {
            dao.insertItem(item)
        } else {
            dao.updateItem(item)
            item.id
        }
    }

    suspend fun deleteItem(item: CashFlowItemEntity) {
        dao.deleteActualsForItem(item.id)
        dao.deleteItem(item)
    }

    suspend fun deleteItemById(id: Long) {
        dao.deleteActualsForItem(id)
        dao.deleteItemById(id)
    }

    suspend fun recordActual(
        itemId: Long,
        occurrenceEpochDay: Long,
        actualAmount: Double,
        notes: String = ""
    ) {
        val entity = ActualAdjustmentEntity(
            periodicItemId = itemId,
            occurrenceDateEpochDay = occurrenceEpochDay,
            actualAmount = actualAmount,
            notes = notes
        )
        dao.insertOrUpdateActual(entity)
    }

    suspend fun removeActual(itemId: Long, occurrenceEpochDay: Long) {
        dao.deleteActualForOccurrence(itemId, occurrenceEpochDay)
    }

    suspend fun updateSettings(settings: UserSettingsEntity) {
        dao.insertOrUpdateSettings(settings)
    }

    suspend fun updateStartingCash(amount: Double, dateEpochDay: Long) {
        val current = dao.getUserSettingsDirect() ?: UserSettingsEntity()
        dao.insertOrUpdateSettings(
            current.copy(
                startingCashAmount = amount,
                startingDateEpochDay = dateEpochDay
            )
        )
    }

    suspend fun updateThreshold(threshold: Double, notify: Boolean) {
        val current = dao.getUserSettingsDirect() ?: UserSettingsEntity()
        dao.insertOrUpdateSettings(
            current.copy(
                minimumThresholdAmount = threshold,
                notifyBelowThreshold = notify
            )
        )
    }

    suspend fun updateHorizon(months: Int) {
        val current = dao.getUserSettingsDirect() ?: UserSettingsEntity()
        dao.insertOrUpdateSettings(current.copy(projectionHorizonMonths = months))
    }

    suspend fun updateThemeMode(themeMode: String) {
        val current = dao.getUserSettingsDirect() ?: UserSettingsEntity()
        dao.insertOrUpdateSettings(current.copy(themeMode = themeMode))
    }

    suspend fun restoreAllData(snapshot: com.example.util.BackupSnapshot) {
        dao.clearAllItems()
        dao.clearAllActuals()
        dao.insertOrUpdateSettings(snapshot.settings)
        dao.insertItems(snapshot.items)
        for (actual in snapshot.actuals) {
            dao.insertOrUpdateActual(actual)
        }
    }
}
