package com.example

import com.example.data.local.entity.ActualAdjustmentEntity
import com.example.data.local.entity.CashFlowItemEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.domain.engine.CashFlowProjectionEngine
import com.example.domain.model.PeriodType
import com.example.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CashFlowProjectionTest {

    @Test
    fun testCashFlowProjectionsWithAverageAndMaximum() {
        val startDate = LocalDate.of(2026, 11, 1)
        val settings = UserSettingsEntity(
            startingCashAmount = 2000.0,
            startingDateEpochDay = startDate.toEpochDay(),
            minimumThresholdAmount = 1000.0,
            projectionHorizonMonths = 1
        )

        val items = listOf(
            // Salary bi-weekly: $1,500 avg and max
            CashFlowItemEntity(
                id = 1,
                title = "Salary",
                type = TransactionType.INCOME.name,
                isPeriodic = true,
                period = PeriodType.BI_WEEKLY.name,
                averageAmount = 1500.0,
                maximumAmount = 1500.0,
                startDateEpochDay = startDate.plusDays(5).toEpochDay(),
                category = "Salary"
            ),
            // Electric utility monthly: $100 avg, $250 max
            CashFlowItemEntity(
                id = 2,
                title = "Electric",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.MONTHLY.name,
                averageAmount = 100.0,
                maximumAmount = 250.0,
                startDateEpochDay = startDate.plusDays(10).toEpochDay(),
                category = "Utilities"
            )
        )

        val result = CashFlowProjectionEngine.calculateProjection(
            settings = settings,
            items = items,
            actuals = emptyList(),
            horizonMonths = 1
        )

        // Starting balance 2000
        // Events in 1 month:
        // - Salary at day +5 (+1500), day +19 (+1500) = +3000
        // - Electric at day +10 (-100 avg, -250 max)
        // Final avg = 2000 + 3000 - 100 = 4900
        // Final max = 2000 + 3000 - 250 = 4750
        assertEquals(4900.0, result.finalAvgBalance, 0.01)
        assertEquals(4750.0, result.finalMaxBalance, 0.01)
        assertFalse(result.isThresholdBreachedAvg)
        assertFalse(result.isThresholdBreachedMax)
    }

    @Test
    fun testActualOverridesEstimate() {
        val startDate = LocalDate.of(2026, 11, 1)
        val settings = UserSettingsEntity(
            startingCashAmount = 1000.0,
            startingDateEpochDay = startDate.toEpochDay(),
            minimumThresholdAmount = 500.0,
            projectionHorizonMonths = 1
        )

        val rentDate = startDate.plusDays(3)
        val items = listOf(
            CashFlowItemEntity(
                id = 10,
                title = "Rent",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.MONTHLY.name,
                averageAmount = 800.0,
                maximumAmount = 900.0,
                startDateEpochDay = rentDate.toEpochDay(),
                category = "Housing"
            )
        )

        // Record an actual of $750 instead of estimated $800 avg / $900 max
        val actuals = listOf(
            ActualAdjustmentEntity(
                id = 1,
                periodicItemId = 10,
                occurrenceDateEpochDay = rentDate.toEpochDay(),
                actualAmount = 750.0
            )
        )

        val result = CashFlowProjectionEngine.calculateProjection(
            settings = settings,
            items = items,
            actuals = actuals,
            horizonMonths = 1
        )

        val rentEvent = result.allEvents.first { it.itemId == 10L }
        assertTrue(rentEvent.isActual)
        assertEquals(750.0, rentEvent.effectiveAvgAmount, 0.01)
        assertEquals(750.0, rentEvent.effectiveMaxAmount, 0.01)
        assertEquals(250.0, result.finalAvgBalance, 0.01) // 1000 - 750 = 250
        assertEquals(250.0, result.finalMaxBalance, 0.01)
    }

    @Test
    fun testThresholdBreachDetection() {
        val startDate = LocalDate.of(2026, 11, 1)
        val breachDay = startDate.plusDays(4)
        val settings = UserSettingsEntity(
            startingCashAmount = 1500.0,
            startingDateEpochDay = startDate.toEpochDay(),
            minimumThresholdAmount = 1000.0, // Safety threshold is $1,000
            projectionHorizonMonths = 1
        )

        val items = listOf(
            // Large ad-hoc car repair expense of $800
            CashFlowItemEntity(
                id = 99,
                title = "Car Repair",
                type = TransactionType.EXPENSE.name,
                isPeriodic = false,
                period = PeriodType.NONE.name,
                averageAmount = 800.0,
                maximumAmount = 1100.0,
                startDateEpochDay = breachDay.toEpochDay(),
                category = "Transportation"
            )
        )

        val result = CashFlowProjectionEngine.calculateProjection(
            settings = settings,
            items = items,
            actuals = emptyList(),
            horizonMonths = 1
        )

        // 1500 - 800 = 700 (below 1000!)
        assertTrue(result.isThresholdBreachedAvg)
        assertEquals(breachDay, result.thresholdBreachDateAvg)
        assertEquals(700.0, result.lowestAvgBalance, 0.01)
        assertEquals(400.0, result.lowestMaxBalance, 0.01) // 1500 - 1100 = 400
    }

    @Test
    fun testThemeModeParsing() {
        assertEquals(com.example.ui.theme.ThemeMode.LIGHT, com.example.ui.theme.ThemeMode.fromString("LIGHT"))
        assertEquals(com.example.ui.theme.ThemeMode.DARK, com.example.ui.theme.ThemeMode.fromString("DARK"))
        assertEquals(com.example.ui.theme.ThemeMode.SYSTEM, com.example.ui.theme.ThemeMode.fromString("SYSTEM"))
        assertEquals(com.example.ui.theme.ThemeMode.SYSTEM, com.example.ui.theme.ThemeMode.fromString("UNKNOWN"))
    }

    @Test
    fun testBackupAndRestoreSerialization() {
        val settings = UserSettingsEntity(
            startingCashAmount = 3500.0,
            startingDateEpochDay = LocalDate.of(2026, 10, 27).toEpochDay(),
            minimumThresholdAmount = 800.0,
            themeMode = "DARK"
        )
        val items = listOf(
            CashFlowItemEntity(
                id = 5,
                title = "Internet Bill",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.MONTHLY.name,
                averageAmount = 75.0,
                maximumAmount = 85.0,
                startDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
                category = "Utilities"
            )
        )
        val actuals = listOf(
            ActualAdjustmentEntity(
                id = 1,
                periodicItemId = 5,
                occurrenceDateEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
                actualAmount = 79.99
            )
        )

        val json = com.example.util.DataBackupManager.createBackupJson(settings, items, actuals)
        val parsed = com.example.util.DataBackupManager.parseBackupJson(json)

        assertEquals(3500.0, parsed.settings.startingCashAmount, 0.01)
        assertEquals("DARK", parsed.settings.themeMode)
        assertEquals(1, parsed.items.size)
        assertEquals("Internet Bill", parsed.items[0].title)
        assertEquals(75.0, parsed.items[0].averageAmount, 0.01)
        assertEquals(1, parsed.actuals.size)
        assertEquals(79.99, parsed.actuals[0].actualAmount, 0.01)
    }
}
