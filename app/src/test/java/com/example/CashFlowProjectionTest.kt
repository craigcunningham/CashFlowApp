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

    @Test
    fun testActualAdjustmentWithCustomRescheduledDate() {
        val startDate = LocalDate.of(2026, 11, 1)
        val scheduledDate = startDate.plusDays(4) // Nov 5
        val rescheduledActualDate = startDate.plusDays(9) // Nov 10

        val settings = UserSettingsEntity(
            startingCashAmount = 2000.0,
            startingDateEpochDay = startDate.toEpochDay(),
            minimumThresholdAmount = 500.0,
            projectionHorizonMonths = 1
        )

        val items = listOf(
            CashFlowItemEntity(
                id = 20,
                title = "Gym Membership",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.MONTHLY.name,
                averageAmount = 60.0,
                maximumAmount = 60.0,
                startDateEpochDay = scheduledDate.toEpochDay(),
                category = "Health"
            )
        )

        val actuals = listOf(
            ActualAdjustmentEntity(
                id = 1,
                periodicItemId = 20,
                occurrenceDateEpochDay = scheduledDate.toEpochDay(),
                actualAmount = 65.0,
                actualDateEpochDay = rescheduledActualDate.toEpochDay(),
                notes = "Delayed processing by bank"
            )
        )

        val result = CashFlowProjectionEngine.calculateProjection(
            settings = settings,
            items = items,
            actuals = actuals,
            horizonMonths = 1
        )

        val event = result.allEvents.first { it.itemId == 20L }
        assertTrue(event.isActual)
        assertTrue(event.isDateRescheduled)
        assertEquals(scheduledDate, event.scheduledDate)
        assertEquals(rescheduledActualDate, event.date)
        assertEquals(65.0, event.effectiveAvgAmount, 0.01)

        // Check daily balance on Nov 5: still 2000 because payment was shifted to Nov 10!
        val day5Point = result.dailyPoints.first { it.date == scheduledDate }
        assertEquals(2000.0, day5Point.endingAvgBalance, 0.01)

        // Check daily balance on Nov 10: 2000 - 65 = 1935
        val day10Point = result.dailyPoints.first { it.date == rescheduledActualDate }
        assertEquals(1935.0, day10Point.endingAvgBalance, 0.01)
    }

    @Test
    fun testNotificationLookaheadBreachFiltering() {
        val today = LocalDate.of(2026, 11, 1)
        val breachDay = today.plusDays(40) // 40 days ahead

        val settings = UserSettingsEntity(
            startingCashAmount = 1000.0,
            startingDateEpochDay = today.toEpochDay(),
            minimumThresholdAmount = 500.0,
            notifyBelowThreshold = true,
            notifyAverageCase = true,
            notifyWorstCase = true,
            notificationLookaheadDays = 30
        )

        val items = listOf(
            CashFlowItemEntity(
                id = 30,
                title = "Insurance Premium",
                type = TransactionType.EXPENSE.name,
                isPeriodic = false,
                period = PeriodType.NONE.name,
                averageAmount = 700.0, // 1000 - 700 = 300, below 500 threshold
                maximumAmount = 800.0,
                startDateEpochDay = breachDay.toEpochDay(),
                category = "Insurance"
            )
        )

        val result = CashFlowProjectionEngine.calculateProjection(
            settings = settings,
            items = items,
            actuals = emptyList(),
            horizonMonths = 2
        )

        assertTrue(result.isThresholdBreachedAvg)

        // 1. With 30-day lookahead: breach is 40 days away, so should return null (no alert)
        val breach30Days = result.getBreachWithinLookahead(
            lookaheadDays = 30,
            checkAvg = true,
            checkWorstCase = true,
            fromDate = today
        )
        org.junit.Assert.assertNull(breach30Days)

        // 2. With 60-day lookahead: breach is 40 days away, so should alert!
        val breach60Days = result.getBreachWithinLookahead(
            lookaheadDays = 60,
            checkAvg = true,
            checkWorstCase = true,
            fromDate = today
        )
        assertNotNull(breach60Days)
        assertEquals(breachDay, breach60Days?.breachDate)
        assertEquals(40L, breach60Days?.daysAhead)

        // 3. If Average Case alerts are turned OFF, but breach is only average:
        val breachAvgDisabled = result.getBreachWithinLookahead(
            lookaheadDays = 60,
            checkAvg = false,
            checkWorstCase = false,
            fromDate = today
        )
        org.junit.Assert.assertNull(breachAvgDisabled)
    }

    @Test
    fun testStarterItemsProjection() {
        val today = LocalDate.of(2026, 11, 1)
        val settings = UserSettingsEntity(
            id = 1,
            startingCashAmount = 4500.0,
            startingDateEpochDay = today.toEpochDay(),
            minimumThresholdAmount = 1000.0,
            currencySymbol = "$",
            notifyBelowThreshold = true,
            projectionHorizonMonths = 6
        )
        val starterItems = listOf(
            CashFlowItemEntity(
                title = "Salary Paycheck",
                type = TransactionType.INCOME.name,
                isPeriodic = true,
                period = PeriodType.BI_WEEKLY.name,
                averageAmount = 2400.0,
                maximumAmount = 2400.0,
                startDateEpochDay = today.plusDays(2).toEpochDay(),
                category = "Salary"
            ),
            CashFlowItemEntity(
                title = "Apartment Rent",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.MONTHLY.name,
                averageAmount = 1450.0,
                maximumAmount = 1450.0,
                startDateEpochDay = today.toEpochDay(),
                category = "Housing"
            ),
            CashFlowItemEntity(
                title = "Electric & Utilities",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.MONTHLY.name,
                averageAmount = 120.0,
                maximumAmount = 190.0,
                startDateEpochDay = today.plusDays(10).toEpochDay(),
                category = "Utilities"
            ),
            CashFlowItemEntity(
                title = "Groceries & Household",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.WEEKLY.name,
                averageAmount = 150.0,
                maximumAmount = 220.0,
                startDateEpochDay = today.plusDays(4).toEpochDay(),
                category = "Food"
            ),
            CashFlowItemEntity(
                title = "Car Insurance",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.BI_ANNUAL.name,
                averageAmount = 600.0,
                maximumAmount = 650.0,
                startDateEpochDay = today.plusMonths(2).toEpochDay(),
                category = "Transportation"
            ),
            CashFlowItemEntity(
                title = "High-Yield Savings Interest",
                type = TransactionType.INCOME.name,
                isPeriodic = true,
                period = PeriodType.MONTHLY.name,
                averageAmount = 45.0,
                maximumAmount = 45.0,
                startDateEpochDay = today.plusDays(15).toEpochDay(),
                category = "Investment"
            )
        )
        val result = CashFlowProjectionEngine.calculateProjection(
            settings = settings,
            items = starterItems,
            actuals = emptyList(),
            horizonMonths = 6
        )
        assertFalse(result.isThresholdBreachedAvg)
        assertFalse(result.isThresholdBreachedMax)
        val breachInfo = result.getBreachWithinLookahead(30, checkAvg = true, checkWorstCase = true, fromDate = today)
        org.junit.Assert.assertNull(breachInfo)
    }

    @Test
    fun testPastBreachDoesNotTriggerUpcomingAlert() {
        val today = LocalDate.of(2026, 11, 1)
        val startDate = today.minusDays(20)
        val settings = UserSettingsEntity(
            id = 1,
            startingCashAmount = 800.0, // Below 1000 threshold in the past!
            startingDateEpochDay = startDate.toEpochDay(),
            minimumThresholdAmount = 1000.0,
            projectionHorizonMonths = 2
        )
        // Income occurred 10 days ago bringing balance to 4000
        val items = listOf(
            CashFlowItemEntity(
                title = "Past Salary",
                type = TransactionType.INCOME.name,
                isPeriodic = false,
                period = PeriodType.NONE.name,
                averageAmount = 3200.0,
                maximumAmount = 3200.0,
                startDateEpochDay = startDate.plusDays(10).toEpochDay(),
                category = "Salary"
            )
        )
        val result = CashFlowProjectionEngine.calculateProjection(
            settings = settings,
            items = items,
            actuals = emptyList(),
            horizonMonths = 2
        )

        // The breach within upcoming 30 days from today must be null because today's balance is 4000
        val upcomingBreach = result.getBreachWithinLookahead(
            lookaheadDays = 30,
            checkAvg = true,
            checkWorstCase = true,
            fromDate = today
        )
        org.junit.Assert.assertNull(upcomingBreach)
    }

    @Test
    fun testWorstCaseCalculationBoundedByConfigurableDays() {
        val today = LocalDate.now()
        val settings = UserSettingsEntity(
            startingCashAmount = 3000.0,
            startingDateEpochDay = today.toEpochDay(),
            minimumThresholdAmount = 1000.0,
            projectionHorizonMonths = 6,
            worstCaseProjectionDays = 60
        )

        // Expense every 10 days: avg $100, max $800.
        // Over 6 months (180 days), there are 18 occurrences.
        // In worst-case without limit: 3000 - (18 * 800) = -11,400 (deep breach).
        // With 60-day limit: only occurrences within next 60 days (6 occurrences) count towards worst case.
        val items = listOf(
            CashFlowItemEntity(
                id = 1,
                title = "Variable Expense",
                type = TransactionType.EXPENSE.name,
                isPeriodic = true,
                period = PeriodType.WEEKLY.name,
                averageAmount = 100.0,
                maximumAmount = 500.0,
                startDateEpochDay = today.plusDays(7).toEpochDay(),
                category = "Miscellaneous"
            )
        )

        val result = CashFlowProjectionEngine.calculateProjection(
            settings = settings,
            items = items,
            actuals = emptyList(),
            horizonMonths = 6
        )

        assertEquals(60, result.worstCaseProjectionDays)
        val cutoffDate = today.plusDays(60)
        assertEquals(cutoffDate, result.worstCaseCutoffDate)

        // Verify active points vs inactive points
        val activePoints = result.dailyPoints.filter { it.isWorstCaseActive }
        val inactivePoints = result.dailyPoints.filter { !it.isWorstCaseActive }

        assertTrue("Should have active points within 60 days", activePoints.isNotEmpty())
        assertTrue("Should have inactive points after 60 days in a 6-month horizon", inactivePoints.isNotEmpty())

        activePoints.forEach { point ->
            assertFalse(point.date.isAfter(cutoffDate))
            assertTrue(point.isWorstCaseActive)
        }

        inactivePoints.forEach { point ->
            assertTrue(point.date.isAfter(cutoffDate))
            assertFalse(point.isWorstCaseActive)
            assertFalse("Inactive point must not flag worst-case threshold breach", point.isBelowThresholdMax)
        }
    }
}
