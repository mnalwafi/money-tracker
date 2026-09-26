package com.danilkinkin.buckwheat.data.entities

import java.math.BigDecimal
import java.util.Date
import java.util.UUID

data class ParsedExpense(
    val id: String = UUID.randomUUID().toString(),
    val amount: BigDecimal,
    val merchant: String,
    val date: Date = Date(),
    val packageName: String = "",
    val rawText: String = "",
    val currencySymbol: String? = null,
)
