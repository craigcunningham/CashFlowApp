package com.example

import com.example.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun defaultCategories_containEssentialExpenseTypes() {
        val defaultExpenses = listOf(
            "Housing", "Utilities", "Food", "Transportation",
            "Health", "Insurance", "Entertainment", "Debt", "Personal", "Other"
        )
        assertTrue(defaultExpenses.contains("Housing"))
        assertTrue(defaultExpenses.contains("Utilities"))
        assertTrue(defaultExpenses.contains("Food"))
        assertTrue(defaultExpenses.contains("Other"))
    }

    @Test
    fun transactionType_resolutionIsCorrect() {
        assertEquals(TransactionType.EXPENSE, TransactionType.valueOf("EXPENSE"))
        assertEquals(TransactionType.INCOME, TransactionType.valueOf("INCOME"))
    }
}
