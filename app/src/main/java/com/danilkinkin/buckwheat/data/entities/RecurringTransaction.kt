package com.danilkinkin.buckwheat.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "categoryTag")
    val categoryTag: String? = null,

    @ColumnInfo(name = "interval")
    val interval: RecurrenceInterval,

    @ColumnInfo(name = "startDate")
    val startDate: LocalDate,

    @ColumnInfo(name = "nextOccurrence")
    val nextOccurrence: LocalDate,

    @ColumnInfo(name = "isActive", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "autoDeduct", defaultValue = "0")
    val autoDeduct: Boolean = false,
)
