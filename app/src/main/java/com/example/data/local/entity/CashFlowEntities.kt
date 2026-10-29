package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "cash_flow_items",
    indices = [Index(value = ["type"]), Index(value = ["isActive"])]
)
data class CashFlowItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val type: String, // "INCOME" or "EXPENSE"
    val isPeriodic: Boolean, // true = recurring, false = adhoc
    val period: String, // "WEEKLY", "BI_WEEKLY", "MONTHLY", "BI_ANNUAL", "ANNUAL", "NONE"
    val averageAmount: Double,
    val maximumAmount: Double,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val category: String,
    val isActive: Boolean = true,
    val notes: String = ""
)

@Entity(
    tableName = "actual_adjustments",
    indices = [
        Index(value = ["periodicItemId", "occurrenceDateEpochDay"], unique = true)
    ]
)
data class ActualAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val periodicItemId: Long,
    val occurrenceDateEpochDay: Long, // scheduled occurrence date
    val actualAmount: Double,
    val actualDateEpochDay: Long = occurrenceDateEpochDay, // date actually paid/received
    val notes: String = "",
    val recordedAtEpochMillis: Long = System.currentTimeMillis()
) {
    val effectiveActualDateEpochDay: Long
        get() = if (actualDateEpochDay > 0) actualDateEpochDay else occurrenceDateEpochDay
}

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val startingCashAmount: Double = 5000.0,
    val startingDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val minimumThresholdAmount: Double = 1000.0,
    val currencySymbol: String = "$",
    val notifyBelowThreshold: Boolean = true,
    val notifyAverageCase: Boolean = true,
    val notifyWorstCase: Boolean = true,
    val notificationLookaheadDays: Int = 30,
    val projectionHorizonMonths: Int = 6,
    val themeMode: String = "SYSTEM" // "SYSTEM", "LIGHT", "DARK"
)
