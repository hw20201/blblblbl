package com.github.mihomo.android.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficCounterAccountingTest {
    @Test fun monotonicCountersOnlyAddTheObservedDifference() {
        assertEquals(CounterAccounting(delta = 25, last = 125, pendingReset = -1), accountCounter(100, -1, 125))
    }

    @Test fun aSingleLowerSampleDoesNotSubtractOrLoseThePriorBaseline() {
        assertEquals(CounterAccounting(delta = 0, last = 1000, pendingReset = 12), accountCounter(1000, -1, 12))
        assertEquals(CounterAccounting(delta = 5, last = 1005, pendingReset = -1), accountCounter(1000, 12, 1005))
        assertEquals(CounterAccounting(delta = 5, last = 1010, pendingReset = -1), accountCounter(1005, -1, 1010))
    }

    @Test fun confirmedCoreResetCountsNewSessionBytesOnceAndThenContinues() {
        val firstLow = accountCounter(900, -1, 20)
        assertEquals(CounterAccounting(0, 900, 20), firstLow)
        val confirmed = accountCounter(firstLow.last, firstLow.pendingReset, 27)
        assertEquals(CounterAccounting(27, 27, -1), confirmed)
        assertEquals(CounterAccounting(5, 32, -1), accountCounter(confirmed.last, confirmed.pendingReset, 32))
    }

    @Test fun weekStartsMondayAndMonthStartsOnCalendarBoundary() {
        assertEquals(
            LocalDate.of(2026, 10, 26) to LocalDate.of(2026, 11, 1),
            trafficPeriodStarts(LocalDate.of(2026, 11, 1))
        )
        assertEquals(
            LocalDate.of(2026, 11, 2) to LocalDate.of(2026, 11, 1),
            trafficPeriodStarts(LocalDate.of(2026, 11, 3))
        )
    }
}
