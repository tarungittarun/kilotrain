package io.opentrack.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 10.2: the EXACT copy of every state is asserted, not reviewed. */
class StateCopyTest {

    @Test
    fun liveCopy() {
        assertEquals("live data 4 min old",
            StateCopy.banner(ScreenState.LIVE, source = "NTES", age = "4 min"))
    }

    @Test
    fun liveStaleCopy() {
        assertEquals("live data 27 min old (may be behind)",
            StateCopy.banner(ScreenState.LIVE_STALE, source = "NTES", age = "27 min"))
    }

    @Test
    fun cachedCopy() {
        assertEquals("cached 2 h",
            StateCopy.banner(ScreenState.CACHED, source = "erail.in", age = "2 h"))
    }

    @Test
    fun offlineUnverifiedCopy() {
        assertEquals("offline - showing unverified timetable data. No source answered.",
            StateCopy.banner(ScreenState.OFFLINE_UNVERIFIED, source = null, age = null))
    }

    @Test
    fun answeredEmptyCopy() {
        assertEquals("no trains found for this route today",
            StateCopy.banner(ScreenState.ANSWERED_EMPTY, source = null, age = null))
    }

    /** 3.3: "could not look" and "answered empty" are different words for different truths. */
    @Test
    fun couldNotLookNamesTheSourceAndIsNotAnEmptyAnswer() {
        val could = StateCopy.banner(ScreenState.COULD_NOT_LOOK, source = "erail.in", age = null)
        val empty = StateCopy.banner(ScreenState.ANSWERED_EMPTY, source = null, age = null)
        assertEquals("could not reach erail.in. showing what is already on screen.", could)
        assertEquals(false, could == empty)
    }

    @Test
    fun partialNamesWhatWasLookedUp() {
        assertEquals("showing what erail.in answered; the rest could not be looked up.",
            StateCopy.banner(ScreenState.PARTIAL, source = "erail.in", age = null))
    }

    /** 12.3: protocol change is said in words, with no silent fallback. */
    @Test
    fun protocolChangedCopy() {
        assertEquals("NTES's feed may have changed. Nothing to show until it is re-checked.",
            StateCopy.banner(ScreenState.PROTOCOL_CHANGED, source = "NTES", age = null))
    }

    /** 3.2: every row line ends with its rendered provenance label. */
    @Test
    fun rowLineCarriesTheLabel() {
        val line = StateCopy.row("16228", "SMET-NDLS Exp", "17:45", "via erail.in")
        assertEquals("16228 · SMET-NDLS Exp — 17:45 · via erail.in", line)
        assertEquals(true, line.endsWith("via erail.in"))
    }
}
