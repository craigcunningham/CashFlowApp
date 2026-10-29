package com.example.domain.engine

import com.example.data.local.entity.ActualAdjustmentEntity
import com.example.data.local.entity.CashFlowItemEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.domain.model.PeriodType
import com.example.domain.model.TransactionType
import java.time.LocalDate

data class CashFlowEvent(
    val id: String,
    val itemId: Long,
    val title: String,
    val type: TransactionType,
    val category: String,
    val isPeriodic: Boolean,
    val period: PeriodType,
    val date: LocalDate, // effective date applied (actual date if edited, otherwise scheduled)
    val scheduledDate: LocalDate = date, // original scheduled occurrence date
    val avgAmount: Double,
    val maxAmount: Double,
    val isActual: Boolean,
    val actualAmount: Double?,
    val actualDate: LocalDate? = null,
    val effectiveAvgAmount: Double,
    val effectiveMaxAmount: Double,
    val notes: String = "",
    // Running balance after this transaction
    var runningAvgBalance: Double = 0.0,
    var runningMaxBalance: Double = 0.0
) {
    val isDateRescheduled: Boolean
        get() = isActual && actualDate != null && actualDate != scheduledDate
}

data class DailyProjection(
    val date: LocalDate,
    val endingAvgBalance: Double,
    val endingMaxBalance: Double,
    val dayIncomeAvg: Double,
    val dayExpenseAvg: Double,
    val dayExpenseMax: Double,
    val events: List<CashFlowEvent>,
    val isBelowThresholdAvg: Boolean,
    val isBelowThresholdMax: Boolean
)

data class LookaheadBreachInfo(
    val breachDate: LocalDate,
    val amount: Double,
    val isWorstCase: Boolean,
    val daysAhead: Long
)

data class ProjectionResult(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startingBalance: Double,
    val minimumThreshold: Double,
    val dailyPoints: List<DailyProjection>,
    val allEvents: List<CashFlowEvent>,
    val finalAvgBalance: Double,
    val finalMaxBalance: Double,
    val lowestAvgBalance: Double,
    val lowestAvgDate: LocalDate?,
    val lowestMaxBalance: Double,
    val lowestMaxDate: LocalDate?,
    val isThresholdBreachedAvg: Boolean,
    val thresholdBreachDateAvg: LocalDate?,
    val isThresholdBreachedMax: Boolean,
    val thresholdBreachDateMax: LocalDate?,
    val totalIncomeAvg: Double,
    val totalExpenseAvg: Double,
    val totalExpenseMax: Double
) {
    fun getBreachWithinLookahead(
        lookaheadDays: Int,
        checkAvg: Boolean,
        checkWorstCase: Boolean,
        fromDate: LocalDate = LocalDate.now()
    ): LookaheadBreachInfo? {
        val lookaheadCutoff = fromDate.plusDays(lookaheadDays.toLong())

        val avgBreach = if (checkAvg && thresholdBreachDateAvg != null &&
            !thresholdBreachDateAvg.isBefore(fromDate) &&
            !thresholdBreachDateAvg.isAfter(lookaheadCutoff)
        ) {
            thresholdBreachDateAvg
        } else null

        val worstBreach = if (checkWorstCase && thresholdBreachDateMax != null &&
            !thresholdBreachDateMax.isBefore(fromDate) &&
            !thresholdBreachDateMax.isAfter(lookaheadCutoff)
        ) {
            thresholdBreachDateMax
        } else null

        return when {
            avgBreach != null && worstBreach != null -> {
                if (avgBreach.isBefore(worstBreach) || avgBreach == worstBreach) {
                    LookaheadBreachInfo(
                        breachDate = avgBreach,
                        amount = lowestAvgBalance,
                        isWorstCase = false,
                        daysAhead = java.time.temporal.ChronoUnit.DAYS.between(fromDate, avgBreach)
                    )
                } else {
                    LookaheadBreachInfo(
                        breachDate = worstBreach,
                        amount = lowestMaxBalance,
                        isWorstCase = true,
                        daysAhead = java.time.temporal.ChronoUnit.DAYS.between(fromDate, worstBreach)
                    )
                }
            }
            avgBreach != null -> {
                LookaheadBreachInfo(
                    breachDate = avgBreach,
                    amount = lowestAvgBalance,
                    isWorstCase = false,
                    daysAhead = java.time.temporal.ChronoUnit.DAYS.between(fromDate, avgBreach)
                )
            }
            worstBreach != null -> {
                LookaheadBreachInfo(
                    breachDate = worstBreach,
                    amount = lowestMaxBalance,
                    isWorstCase = true,
                    daysAhead = java.time.temporal.ChronoUnit.DAYS.between(fromDate, worstBreach)
                )
            }
            else -> null
        }
    }
}

object CashFlowProjectionEngine {

    fun calculateProjection(
        settings: UserSettingsEntity,
        items: List<CashFlowItemEntity>,
        actuals: List<ActualAdjustmentEntity>,
        horizonMonths: Int = settings.projectionHorizonMonths
    ): ProjectionResult {
        val startDate = LocalDate.ofEpochDay(settings.startingDateEpochDay)
        val endDate = startDate.plusMonths(horizonMonths.toLong())
        val startBalance = settings.startingCashAmount
        val threshold = settings.minimumThresholdAmount

        // Map actual adjustments by (itemId to occurrenceEpochDay)
        val actualMap = actuals.associateBy { it.periodicItemId to it.occurrenceDateEpochDay }

        val activeItems = items.filter { it.isActive }
        val generatedEvents = mutableListOf<CashFlowEvent>()

        for (item in activeItems) {
            val itemStartDate = LocalDate.ofEpochDay(item.startDateEpochDay)
            val itemEndDate = item.endDateEpochDay?.let { LocalDate.ofEpochDay(it) }
            val itemType = if (item.type.equals("INCOME", ignoreCase = true)) TransactionType.INCOME else TransactionType.EXPENSE
            val period = PeriodType.fromString(item.period)

            if (!item.isPeriodic || period == PeriodType.NONE) {
                // Ad-hoc item: happens only on itemStartDate
                if (!itemStartDate.isBefore(startDate) && !itemStartDate.isAfter(endDate)) {
                    val actualAdj = actualMap[item.id to itemStartDate.toEpochDay()]
                    val isActual = actualAdj != null
                    val actualAmt = actualAdj?.actualAmount
                    val actualDate = if (isActual && actualAdj != null) {
                        LocalDate.ofEpochDay(actualAdj.effectiveActualDateEpochDay)
                    } else null
                    val effectiveDate = actualDate ?: itemStartDate

                    val effAvg = actualAmt ?: item.averageAmount
                    val effMax = actualAmt ?: item.maximumAmount

                    generatedEvents.add(
                        CashFlowEvent(
                            id = "adhoc_${item.id}_${itemStartDate.toEpochDay()}",
                            itemId = item.id,
                            title = item.title,
                            type = itemType,
                            category = item.category,
                            isPeriodic = false,
                            period = PeriodType.NONE,
                            date = effectiveDate,
                            scheduledDate = itemStartDate,
                            avgAmount = item.averageAmount,
                            maxAmount = item.maximumAmount,
                            isActual = isActual,
                            actualAmount = actualAmt,
                            actualDate = actualDate,
                            effectiveAvgAmount = effAvg,
                            effectiveMaxAmount = effMax,
                            notes = actualAdj?.notes ?: item.notes
                        )
                    )
                }
            } else {
                // Periodic item: generate occurrences
                var currentOccurrence = itemStartDate
                var step = 0

                // Advance forward if starting before projection start
                while (currentOccurrence.isBefore(startDate) && (itemEndDate == null || !currentOccurrence.isAfter(itemEndDate))) {
                    step++
                    currentOccurrence = advanceOccurrence(itemStartDate, period, step)
                }

                // Collect all occurrences within window
                while (!currentOccurrence.isAfter(endDate)) {
                    if (itemEndDate != null && currentOccurrence.isAfter(itemEndDate)) {
                        break
                    }

                    if (!currentOccurrence.isBefore(startDate)) {
                        val epochDay = currentOccurrence.toEpochDay()
                        val actualAdj = actualMap[item.id to epochDay]
                        val isActual = actualAdj != null
                        val actualAmt = actualAdj?.actualAmount
                        val actualDate = if (isActual && actualAdj != null) {
                            LocalDate.ofEpochDay(actualAdj.effectiveActualDateEpochDay)
                        } else null
                        val effectiveDate = actualDate ?: currentOccurrence

                        val effAvg = actualAmt ?: item.averageAmount
                        val effMax = actualAmt ?: item.maximumAmount

                        generatedEvents.add(
                            CashFlowEvent(
                                id = "periodic_${item.id}_$epochDay",
                                itemId = item.id,
                                title = item.title,
                                type = itemType,
                                category = item.category,
                                isPeriodic = true,
                                period = period,
                                date = effectiveDate,
                                scheduledDate = currentOccurrence,
                                avgAmount = item.averageAmount,
                                maxAmount = item.maximumAmount,
                                isActual = isActual,
                                actualAmount = actualAmt,
                                actualDate = actualDate,
                                effectiveAvgAmount = effAvg,
                                effectiveMaxAmount = effMax,
                                notes = actualAdj?.notes ?: item.notes
                            )
                        )
                    }

                    step++
                    currentOccurrence = advanceOccurrence(itemStartDate, period, step)
                }
            }
        }

        // Sort events chronologically by effective date, with income processed before expenses on the same day
        generatedEvents.sortWith(
            compareBy<CashFlowEvent> { it.date }
                .thenBy { if (it.type == TransactionType.INCOME) 0 else 1 }
                .thenBy { it.title }
        )

        // Calculate running balances per event
        var runningAvg = startBalance
        var runningMax = startBalance

        var totalIncomeAvg = 0.0
        var totalExpenseAvg = 0.0
        var totalExpenseMax = 0.0

        for (event in generatedEvents) {
            when (event.type) {
                TransactionType.INCOME -> {
                    runningAvg += event.effectiveAvgAmount
                    runningMax += event.effectiveAvgAmount
                    totalIncomeAvg += event.effectiveAvgAmount
                }
                TransactionType.EXPENSE -> {
                    runningAvg -= event.effectiveAvgAmount
                    runningMax -= event.effectiveMaxAmount
                    totalExpenseAvg += event.effectiveAvgAmount
                    totalExpenseMax += event.effectiveMaxAmount
                }
            }
            event.runningAvgBalance = runningAvg
            event.runningMaxBalance = runningMax
        }

        // Group events by date for daily projection curve
        val eventsByDate = generatedEvents.groupBy { it.date }

        val dailyPoints = mutableListOf<DailyProjection>()
        var dayDate = startDate
        var currentDailyAvg = startBalance
        var currentDailyMax = startBalance

        var lowestAvg = startBalance
        var lowestAvgDate: LocalDate? = startDate
        var lowestMax = startBalance
        var lowestMaxDate: LocalDate? = startDate

        var breachDateAvg: LocalDate? = if (startBalance < threshold) startDate else null
        var breachDateMax: LocalDate? = if (startBalance < threshold) startDate else null

        while (!dayDate.isAfter(endDate)) {
            val dayEvents = eventsByDate[dayDate] ?: emptyList()
            var dayIncome = 0.0
            var dayExpAvg = 0.0
            var dayExpMax = 0.0

            for (e in dayEvents) {
                if (e.type == TransactionType.INCOME) {
                    dayIncome += e.effectiveAvgAmount
                } else {
                    dayExpAvg += e.effectiveAvgAmount
                    dayExpMax += e.effectiveMaxAmount
                }
            }

            currentDailyAvg = currentDailyAvg + dayIncome - dayExpAvg
            currentDailyMax = currentDailyMax + dayIncome - dayExpMax

            if (currentDailyAvg < lowestAvg) {
                lowestAvg = currentDailyAvg
                lowestAvgDate = dayDate
            }
            if (currentDailyMax < lowestMax) {
                lowestMax = currentDailyMax
                lowestMaxDate = dayDate
            }

            if (breachDateAvg == null && currentDailyAvg < threshold) {
                breachDateAvg = dayDate
            }
            if (breachDateMax == null && currentDailyMax < threshold) {
                breachDateMax = dayDate
            }

            dailyPoints.add(
                DailyProjection(
                    date = dayDate,
                    endingAvgBalance = currentDailyAvg,
                    endingMaxBalance = currentDailyMax,
                    dayIncomeAvg = dayIncome,
                    dayExpenseAvg = dayExpAvg,
                    dayExpenseMax = dayExpMax,
                    events = dayEvents,
                    isBelowThresholdAvg = currentDailyAvg < threshold,
                    isBelowThresholdMax = currentDailyMax < threshold
                )
            )

            dayDate = dayDate.plusDays(1)
        }

        return ProjectionResult(
            startDate = startDate,
            endDate = endDate,
            startingBalance = startBalance,
            minimumThreshold = threshold,
            dailyPoints = dailyPoints,
            allEvents = generatedEvents,
            finalAvgBalance = currentDailyAvg,
            finalMaxBalance = currentDailyMax,
            lowestAvgBalance = lowestAvg,
            lowestAvgDate = lowestAvgDate,
            lowestMaxBalance = lowestMax,
            lowestMaxDate = lowestMaxDate,
            isThresholdBreachedAvg = breachDateAvg != null,
            thresholdBreachDateAvg = breachDateAvg,
            isThresholdBreachedMax = breachDateMax != null,
            thresholdBreachDateMax = breachDateMax,
            totalIncomeAvg = totalIncomeAvg,
            totalExpenseAvg = totalExpenseAvg,
            totalExpenseMax = totalExpenseMax
        )
    }

    private fun advanceOccurrence(start: LocalDate, period: PeriodType, step: Int): LocalDate {
        return when (period) {
            PeriodType.WEEKLY -> start.plusWeeks(step.toLong())
            PeriodType.BI_WEEKLY -> start.plusWeeks((step * 2).toLong())
            PeriodType.MONTHLY -> start.plusMonths(step.toLong())
            PeriodType.BI_ANNUAL -> start.plusMonths((step * 6).toLong())
            PeriodType.ANNUAL -> start.plusYears(step.toLong())
            PeriodType.NONE -> start
        }
    }
}
