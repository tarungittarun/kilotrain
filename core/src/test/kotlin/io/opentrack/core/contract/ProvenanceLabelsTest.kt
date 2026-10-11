package io.opentrack.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3.2: the four labels are the ENTIRE vocabulary, rendered by ONE function. */
class ProvenanceLabelsTest {

    @Test
    fun timetableRendersTimetable() {
        val p = Provenance(ProvenanceKind.TIMETABLE, "offline asset", 1_700_000_000_000)
        assertEquals("timetable", p.label(now = 1_700_000_000_000 + 90L * 24 * 3600 * 1000))
    }

    @Test
    fun viaSourceRendersViaSource() {
        val p = Provenance(ProvenanceKind.VIA_SOURCE, "erail.in", 1_700_000_000_000)
        assertEquals("via erail.in", p.label(1_700_000_000_000))
    }

    @Test
    fun cachedRendersCachedWithAge() {
        val now = 1_700_000_000_000
        val p = Provenance(ProvenanceKind.CACHED, "erail.in", now - 3 * 60 * 1000)
        assertEquals("cached 3 min", p.label(now))
    }

    @Test
    fun liveRendersLiveDataAgeOld() {
        val now = 1_700_000_000_000
        val p = Provenance(ProvenanceKind.LIVE, "NTES", now - 2 * 60 * 60 * 1000)
        assertEquals("live data 2 h old", p.label(now))
    }

    /** 6.2: fetchedAtMillis <= 0 is the least trusted state, never "now". */
    @Test
    fun fetchedAtZeroNeverClaimsFreshness() {
        val p = Provenance(ProvenanceKind.LIVE, "NTES", 0)
        assertTrue(p.isLeastTrusted)
        assertEquals("timetable", p.label(now = 1_700_000_000_000))
    }

    @Test
    fun negativeFetchedAtIsAlsoLeastTrusted() {
        val p = Provenance(ProvenanceKind.CACHED, "erail.in", -5)
        assertTrue(p.isLeastTrusted)
        assertEquals("timetable", p.label(now = 1_700_000_000_000))
    }

    /** A clock skewed behind the feed still shows an age, never a negative one. */
    @Test
    fun clockSkewNeverRendersNegativeAge() {
        val p = Provenance(ProvenanceKind.LIVE, "NTES", 1_700_000_000_000)
        assertEquals("live data under a minute old", p.label(now = 1_700_000_000_000 - 30_000))
    }
}
