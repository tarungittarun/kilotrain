package io.opentrack.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The expected-time arithmetic is pure and wrap-safe (10.3); it never invents an actual. */
class ExpectedTimingTest {

    @Test
    fun shiftWrapsPastMidnight() {
        assertEquals("00:10", shiftMinutes("23:59", 11))
        assertEquals("00:08", shiftMinutes("23:58", 10))
        assertEquals("17:45", shiftMinutes("17:35", 10))
    }

    /** The owner's comparison test: the train is BETWEEN stations, never "here" at one it left. */
    @Test
    fun theTrainIsShownBetweenStationsNotAtTheLastReportedOne() {
        val stops = listOf(
            stop("A", "10:00", "10:05", actual = "10:07", distance = "0"),
            stop("B", "10:30", "10:35", actual = null, distance = "18"),
            stop("C", "11:00", "11:05", actual = null, distance = "40"),
        )
        val out = withPositionAndExpectations(stops, "10 min")
        assertTrue(out[0].isDepartedHere) // the train has LEFT A
        assertTrue(!out.any { it.isTrainHere }) // never "here" at a stop it has left
        assertTrue(out[1].isNextStation)
        assertEquals("18", out[1].kmToNext) // ~18 km to the next stop
        assertEquals("10:45", out[1].expectedDeparture) // expected, not actual
        assertNull(out[1].actualDeparture)
    }

    @Test
    fun withNoReportedActualsThereIsNoPositionMarker() {
        val stops = listOf(
            stop("A", "10:00", "10:05", actual = null, distance = "0"),
            stop("B", "10:30", "10:35", actual = null, distance = "18"),
        )
        val out = withPositionAndExpectations(stops, "10 min")
        assertTrue(out.none { it.isDepartedHere })
        assertTrue(out.none { it.isNextStation })
        assertEquals("10:15", out[0].expectedDeparture)
    }

    private fun stop(
        code: String, schedArr: String, schedDep: String,
        actual: String?, distance: String,
    ) = ResultsEngine.StopStatus(
        code = code, name = code, scheduledArrival = schedArr, scheduledDeparture = schedDep,
        actualArrival = actual, actualDeparture = actual,
        arrivalDelayMinutes = null, departureDelayMinutes = null,
        isCurrentStation = false, isTrainHere = false, expectedDeparture = null,
        distanceKm = distance, haltMinutes = null,
        isDepartedHere = false, isNextStation = false, kmToNext = null,
    )
}
