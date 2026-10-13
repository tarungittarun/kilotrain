package io.opentrack.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The expected-time arithmetic is pure and wrap-safe (10.3); it never invents an actual. */
class ExpectedTimingTest {

    @Test
    fun shiftWrapsPastMidnight() {
        assertEquals("00:10", shiftMinutes("23:59", 11))
        assertEquals("00:08", shiftMinutes("23:58", 10))
        assertEquals("17:45", shiftMinutes("17:35", 10))
    }

    @Test
    fun theTrainPositionIsTheLastReportedStopWhenNoCurrentIsPublished() {
        val stops = listOf(
            ResultsEngine.StopStatus("A", "a", "10:00", "10:05", "10:05", "10:07", 5, 2, false, false, null),
            ResultsEngine.StopStatus("B", "b", "10:30", "10:35", null, null, null, null, false, false, "10:45"),
            ResultsEngine.StopStatus("C", "c", "11:00", "11:05", null, null, null, null, false, false, "11:15"),
        )
        val out = withPositionAndExpectations(stops, "10 min")
        assertTrue(out[0].isTrainHere) // the last stop with a reported actual
        assertEquals("10:45", out[1].expectedDeparture) // expected, not actual
        assertTrue(out[1].actualDeparture == null)
        assertTrue(!out[1].isTrainHere)
    }

    @Test
    fun onTimeProducesZeroDelayAndNoExpectedShift() {
        val stops = listOf(
            ResultsEngine.StopStatus("A", "a", "10:00", "10:05", null, null, null, null, false, false, null),
        )
        val out = withPositionAndExpectations(stops, "On Time")
        assertEquals("10:05", out.single().expectedDeparture)
    }

    private fun assertTrue(v: Boolean) = org.junit.Assert.assertTrue(v)
}
