package com.danilkinkin.buckwheat.recurring

import com.danilkinkin.buckwheat.data.entities.RecurrenceInterval
import com.danilkinkin.buckwheat.data.entities.RecurringTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

class RecurringTransactionsTest {

    @Test
    fun testRecurrenceIntervalCalculateNext() {
        val baseDate = LocalDate.of(2026, 1, 15)

        assertEquals(LocalDate.of(2026, 1, 16), RecurrenceInterval.DAILY.calculateNext(baseDate))
        assertEquals(LocalDate.of(2026, 1, 22), RecurrenceInterval.WEEKLY.calculateNext(baseDate))
        assertEquals(LocalDate.of(2026, 2, 15), RecurrenceInterval.MONTHLY.calculateNext(baseDate))
        assertEquals(LocalDate.of(2027, 1, 15), RecurrenceInterval.YEARLY.calculateNext(baseDate))
    }

    @Test
    fun testRecurrenceIntervalMonthEndClamping() {
        // January 31 plus 1 month should be February 28 (non-leap year)
        val jan31 = LocalDate.of(2026, 1, 31)
        val nextMonth = RecurrenceInterval.MONTHLY.calculateNext(jan31)
        assertEquals(LocalDate.of(2026, 2, 28), nextMonth)
    }

    @Test
    fun testCountOccurrencesBetween() {
        val startDate = LocalDate.of(2026, 6, 1)
        val endDate = LocalDate.of(2026, 6, 30)

        // Monthly transaction due on June 15: exactly 1 occurrence
        val monthlyOccurrences = RecurrenceInterval.MONTHLY.countOccurrencesBetween(
            startDate = startDate,
            endDate = endDate,
            firstOccurrence = LocalDate.of(2026, 6, 15),
        )
        assertEquals(1, monthlyOccurrences)

        // Monthly transaction due on July 5: 0 occurrences
        val futureOccurrences = RecurrenceInterval.MONTHLY.countOccurrencesBetween(
            startDate = startDate,
            endDate = endDate,
            firstOccurrence = LocalDate.of(2026, 7, 5),
        )
        assertEquals(0, futureOccurrences)

        // Weekly transaction starting June 5: occurs June 5, 12, 19, 26 (4 occurrences)
        val weeklyOccurrences = RecurrenceInterval.WEEKLY.countOccurrencesBetween(
            startDate = startDate,
            endDate = endDate,
            firstOccurrence = LocalDate.of(2026, 6, 5),
        )
        assertEquals(4, weeklyOccurrences)

        // Daily transaction starting June 28: occurs June 28, 29, 30 (3 occurrences)
        val dailyOccurrences = RecurrenceInterval.DAILY.countOccurrencesBetween(
            startDate = startDate,
            endDate = endDate,
            firstOccurrence = LocalDate.of(2026, 6, 28),
        )
        assertEquals(3, dailyOccurrences)
    }

    @Test
    fun testSafeDailyBudgetFormulaWithReservation() {
        // Scenario:
        // Total Remaining Pool = 3000
        // Period = 30 days
        // Known upcoming recurring obligation = Rent (1200) due on day 15 + Gym (300) due on day 20
        // Total Reserved = 1500
        //
        // Without reservation: Daily allowance = 3000 / 30 = 100/day.
        // Mid-cycle drop occurs on day 15 when 1200 is suddenly spent!
        //
        // WITH reservation:
        // Safe Daily Allowance = (Pool - Reserved) / Days Left = (3000 - 1500) / 30 = 1500 / 30 = 50/day.

        val totalPool = BigDecimal("3000.00")
        val daysLeft = 30
        val reservedAmount = BigDecimal("1500.00")

        val safeDailyAllowance = (totalPool - reservedAmount)
            .divide(BigDecimal(daysLeft), 2, RoundingMode.HALF_EVEN)

        assertEquals(BigDecimal("50.00"), safeDailyAllowance)

        // Now simulate day 15:
        // 14 days have passed at 50/day = 700 spent.
        // Pool is now 3000 - 700 = 2300.
        // On day 15, Rent (1200) is paid and deducted from pool.
        // New pool = 2300 - 1200 = 1100.
        // Rent's nextOccurrence is now next month, so it's no longer reserved in this period!
        // Only Gym (300) remains reserved.
        // Remaining days = 16 days (day 15 to 30).
        // If today is day 15, excluding rent which was just paid:
        val poolAfterRent = BigDecimal("1100.00")
        val remainingReserved = BigDecimal("300.00")
        val daysRemaining = 16

        val newDailyAllowance = (poolAfterRent - remainingReserved)
            .divide(BigDecimal(daysRemaining), 2, RoundingMode.HALF_EVEN)

        // Allowance stays at exactly 50.00! Zero mid-cycle drops!
        assertEquals(BigDecimal("50.00"), newDailyAllowance)
    }

    @Test
    fun testMonthlyFactorCommitmentMath() {
        val rent = RecurringTransaction(
            id = 1,
            name = "Rent",
            amount = BigDecimal("1200.00"),
            interval = RecurrenceInterval.MONTHLY,
            startDate = LocalDate.of(2026, 1, 1),
            nextOccurrence = LocalDate.of(2026, 2, 1),
            isActive = true,
        )

        val netflix = RecurringTransaction(
            id = 2,
            name = "Netflix",
            amount = BigDecimal("15.00"),
            interval = RecurrenceInterval.MONTHLY,
            startDate = LocalDate.of(2026, 1, 1),
            nextOccurrence = LocalDate.of(2026, 2, 1),
            isActive = true,
        )

        val coffeeWeekly = RecurringTransaction(
            id = 3,
            name = "Coffee subscription",
            amount = BigDecimal("20.00"),
            interval = RecurrenceInterval.WEEKLY,
            startDate = LocalDate.of(2026, 1, 1),
            nextOccurrence = LocalDate.of(2026, 2, 1),
            isActive = true,
        )

        var totalMonthly = BigDecimal.ZERO
        val list = listOf(rent, netflix, coffeeWeekly)
        for (item in list) {
            totalMonthly = totalMonthly.add(item.amount.multiply(item.interval.monthlyFactor()))
        }
        totalMonthly = totalMonthly.setScale(2, RoundingMode.HALF_UP)

        // Rent: 1200 * 1 = 1200
        // Netflix: 15 * 1 = 15
        // Coffee: 20 * (52 / 12 = 4.3333) = 86.67
        // Total ~ 1301.67
        assertEquals(BigDecimal("1301.67"), totalMonthly)
    }

    @Test
    fun testDueSoonVsUpcomingClassification() {
        val today = LocalDate.of(2026, 6, 10)
        val cutoff = today.plusDays(7) // June 17

        val billDueTomorrow = LocalDate.of(2026, 6, 11)
        val billDueIn7Days = LocalDate.of(2026, 6, 17)
        val billDueIn8Days = LocalDate.of(2026, 6, 18)
        val billOverdue = LocalDate.of(2026, 6, 8)

        // Due soon: <= cutoff
        assertTrue(!billDueTomorrow.isAfter(cutoff))
        assertTrue(!billDueIn7Days.isAfter(cutoff))
        assertTrue(!billOverdue.isAfter(cutoff))

        // Upcoming: > cutoff
        assertTrue(billDueIn8Days.isAfter(cutoff))
    }
}
