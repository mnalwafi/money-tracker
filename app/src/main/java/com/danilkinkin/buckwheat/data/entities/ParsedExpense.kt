package com.danilkinkin.buckwheat.data.entities

import java.math.BigDecimal
import java.util.Date
import java.util.UUID

enum class TransactionCaptureType {
    EXPENSE,
    INCOME
}

data class ParsedExpense(
    val id: String = UUID.randomUUID().toString(),
    val type: TransactionCaptureType = TransactionCaptureType.EXPENSE,
    val amount: BigDecimal,
    val merchant: String,
    val date: Date = Date(),
    val packageName: String = "",
    val rawText: String = "",
    val currencySymbol: String? = null,
    val confidence: Float = 1.0f,
    val source: String = "hybrid",
)
