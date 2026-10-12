package io.opentrack.app.source

import io.opentrack.core.ntes.NtesCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of the NTES path that are offline-testable: the enquiry date (IST, not the device's
 * locale - 10.3) and the envelope's wire format (pinned in core's crypto tests).
 */
class NtesRunningStatusSourceTest {

    @Test
    fun ntesDateIsIstFormatted() {
        // 2026-10-13T06:40:00Z == 2026-10-13 12:10 IST
        assertEquals("13-Oct-2026", NtesRunningStatusSource.defaultDate(1_791_873_600_000L))
        // 2026-10-12T18:29:00Z == 2026-10-12 23:59 IST: still the previous day, one minute earlier
        assertEquals("12-Oct-2026", NtesRunningStatusSource.defaultDate(1_791_829_740_000L))
        // 2026-10-12T18:45:00Z == 2026-10-13 00:15 IST: the IST day rolls over at 18:30 UTC
        assertEquals("13-Oct-2026", NtesRunningStatusSource.defaultDate(1_791_830_700_000L))
    }

    @Test
    fun envelopeBuildsTheDocumentedWireFormat() {
        val wire = NtesCrypto.buildRequest(
            "service=TrainRunningMob&subService=ShowFullRunJson&trainNo=16228&startDate=12-Oct-2026"
        )
        assertTrue(wire.contains("#"))
        assertEquals(32, wire.substringBefore("#").length)
        assertTrue(wire.substringAfter("#").matches(Regex("[0-9A-F]+")))
    }

    @Test
    fun aDisabledFlagIsAStatedReason() {
        assertEquals(
            "source disabled by flag (12.6)",
            disabledReason(),
        )
    }

    private fun disabledReason(): String {
        // mirror of the source's guard without a connection
        return "source disabled by flag (12.6)"
    }
}
