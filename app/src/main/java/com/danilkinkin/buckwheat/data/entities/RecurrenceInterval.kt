package com.danilkinkin.buckwheat.data.entities

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class RecurrenceInterval {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    /**
     * Advances the date by one interval.
     */
    fun calculateNext(fromDate: LocalDate): LocalDate {
        return when (this) {
            DAILY -> fromDate.plusDays(1)
            WEEKLY -> fromDate.plusWeeks(1)
            MONTHLY -> fromDate.plusMonths(1)
            YEARLY -> fromDate.plusYears(1)
        }
    }

    /**
     * Calculates the number of times this recurring item occurs between
     * [startDate] and [endDate] (inclusive), given its [firstOccurrence].
     * If [firstOccurrence] is after [endDate], returns 0.
     */
    fun countOccurrencesBetween(
        startDate: LocalDate,
        endDate: LocalDate,
        firstOccurrence: LocalDate,
    ): Int {
        if (firstOccurrence.isAfter(endDate)) return 0

        var current = firstOccurrence
        var count = 0

        // Advance until within target window or past it
        while (!current.isAfter(endDate)) {
            if (!current.isBefore(startDate)) {
                count++
            }
            current = calculateNext(current)
        }

        return count
    }

    /**
     * Returns an approximate monthly factor to compute monthly equivalent cost.
     * Monthly = 1.0, Weekly = ~4.333, Daily = ~30.416, Yearly = ~0.0833
     */
    fun monthlyFactor(): BigDecimal {
        return when (this) {
            DAILY -> BigDecimal("30.4167")
            WEEKLY -> BigDecimal("52").divide(BigDecimal("12"), 4, RoundingMode.HALF_UP)
            MONTHLY -> BigDecimal.ONE
            YEARLY -> BigDecimal.ONE.divide(BigDecimal("12"), 4, RoundingMode.HALF_UP)
        }
    }
}
