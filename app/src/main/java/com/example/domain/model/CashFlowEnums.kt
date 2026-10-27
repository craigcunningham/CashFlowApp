package com.example.domain.model

enum class TransactionType(val label: String) {
    INCOME("Income"),
    EXPENSE("Expense")
}

enum class PeriodType(val label: String, val intervalMonths: Int = 0, val intervalDays: Int = 0) {
    WEEKLY("Weekly", intervalDays = 7),
    BI_WEEKLY("Bi-weekly", intervalDays = 14),
    MONTHLY("Monthly", intervalMonths = 1),
    BI_ANNUAL("Bi-annual", intervalMonths = 6),
    ANNUAL("Annual", intervalMonths = 12),
    NONE("One-time / Ad-hoc");

    companion object {
        fun fromString(value: String?): PeriodType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
        }
    }
}
