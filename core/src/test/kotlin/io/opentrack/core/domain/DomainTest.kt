package io.opentrack.core.domain

import io.opentrack.core.contract.Provenance
import io.opentrack.core.contract.ProvenanceKind
import io.opentrack.core.contract.TrainIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunDaysTest {

    @Test
    fun epochDayZeroIsThursday() {
        assertEquals(4, RunDays.dayOfWeek(0)) // 1970-01-01 was a Thursday
    }

    @Test
    fun knownDatesMapCorrectly() {
        // 2026-10-11 is a Sunday (epoch day 20737)
        assertEquals(0, RunDays.dayOfWeek(20737))
        assertTrue(RunDays.runsOn(RunDays.SUNDAY, 20737))
        assertFalse(RunDays.runsOn(RunDays.MONDAY, 20737))
    }

    @Test
    fun allDaysRunsEverywhere() {
        for (day in 20735L..20740L) {
            assertTrue(RunDays.runsOn(RunDays.ALL_DAYS, day))
        }
    }
}

class SearchWindowTest {

    @Test
    fun windowSpansTwoDaysBackOneDayForward() {
        val range = SearchWindow.range(20705)
        assertEquals(20704L..20706L, range)
        assertEquals(3, range.count())
    }
}

/** 6.5/10.8: ONE comparator must never serve both decisions. */
class OrderingTest {

    private fun row(number: String, minutesUntil: Int) = DepartureRow(
        identity = TrainIdentity(number, "name-$number", "SMET", "NDLS",
            Provenance(ProvenanceKind.VIA_SOURCE, "erail.in", 1L)),
        minutesUntilDeparture = minutesUntil,
    )

    /** The named symptom from 10.6. */
    @Test
    fun aTrainLeavingInNineMinutesOutranksOneThatLeftFortyOneMinutesAgo() {
        val rows = listOf(row("A", -41), row("B", 9))
        val ordered = rows.sortedWith(RowDisplayOrder.comparator)
        assertEquals(listOf("B", "A"), ordered.map { it.identity.number })
    }

    @Test
    fun stillCatchableComeBeforeAlreadyGone() {
        val rows = listOf(row("GONE", -200), row("CATCH", 1))
        val ordered = rows.sortedWith(RowDisplayOrder.comparator)
        assertEquals(listOf("CATCH", "GONE"), ordered.map { it.identity.number })
    }

    @Test
    fun alreadyGoneAreOrderedMostRecentFirst() {
        val rows = listOf(row("OLD", -300), row("RECENT", -5))
        val ordered = rows.sortedWith(RowDisplayOrder.comparator)
        assertEquals(listOf("RECENT", "OLD"), ordered.map { it.identity.number })
    }

    @Test
    fun proximityChoosesTheRunClosestToNowNotTheDisplayOrder() {
        val runs = listOf(
            TrainRun(row("A", -120).identity, 20705, 100),
            TrainRun(row("B", 30).identity, 20705, 1300),
        )
        val chosen = ProximityRunChoice.choose(runs, referenceEpochDay = 20705, referenceMinutes = 1300)
        assertEquals("B", chosen?.identity?.number) // the near one, though -120 displays higher
    }
}
