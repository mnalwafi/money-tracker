package com.danilkinkin.buckwheat.data

import com.danilkinkin.buckwheat.data.entities.ParsedExpense
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingExpenseRepository @Inject constructor() {
    private val _pendingExpenses = MutableStateFlow<List<ParsedExpense>>(emptyList())
    val pendingExpenses: StateFlow<List<ParsedExpense>> = _pendingExpenses.asStateFlow()

    fun addPendingExpense(expense: ParsedExpense) {
        _pendingExpenses.update { current ->
            // Avoid duplicate additions if the same rawText or ID was recently captured
            val isDuplicate = current.any {
                it.amount == expense.amount &&
                        it.merchant.equals(expense.merchant, ignoreCase = true) &&
                        Math.abs(it.date.time - expense.date.time) < 10_000 // within 10 seconds
            }
            if (isDuplicate) current else listOf(expense) + current
        }
    }

    fun removePendingExpense(id: String) {
        _pendingExpenses.update { current ->
            current.filterNot { it.id == id }
        }
    }

    fun getPendingExpenseById(id: String): ParsedExpense? {
        return _pendingExpenses.value.find { it.id == id }
    }

    fun clearAll() {
        _pendingExpenses.value = emptyList()
    }
}
