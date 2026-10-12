package io.opentrack.core.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun disabledCollectorWritesNothing() {
        var lines = 0
        val diag = Diagnostics({ lines++ }, enabled = false, now = { 1L })
        diag.pairSearch(mapOf("from" to "SMET"))
        diag.deviceInfo(mapOf("model" to "x"))
        diag.record(DiagKind.SCREEN_STATE, mapOf("state" to "LIVE"))
        assertEquals(0, lines)
    }

    @Test
    fun enabledCollectorWritesOneLinePerEvent() {
        val lines = mutableListOf<String>()
        val diag = Diagnostics({ lines.add(it) }, enabled = true, now = { 42L })
        diag.pairSearch(mapOf("from" to "SMET", "to" to "MYS"))
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("\"ts\":42"))
        assertTrue(lines.single().contains("\"kind\":\"PAIR_SEARCH\""))
        assertTrue(lines.single().contains("\"from\":\"SMET\""))
        assertTrue(lines.single().contains("\"to\":\"MYS\""))
    }

    @Test
    fun structuralSignatureCountsWithoutValues() {
        val sig = DiagnosticsCodec.signature("~~~~~Please try again after some time.")
        assertEquals("38", sig["bytes"])
        assertEquals("5", sig["tildes"])
        assertEquals("0", sig["carets"])
        assertEquals("false", sig["has_feed_stamp"])
        assertEquals("true", sig["throttle_marker"])
    }

    @Test
    fun rawBodyIsAttachedOnlyWhenProvided() {
        val withoutRaw = DiagnosticsCodec.encode(
            DiagnosticEvent(1, DiagKind.PAIR_SEARCH, mapOf("from" to "SMET"))
        )
        assertFalse(withoutRaw.contains("raw"))
        val withRaw = DiagnosticsCodec.encode(
            DiagnosticEvent(1, DiagKind.PAIR_SEARCH, mapOf("from" to "SMET"), rawBody = "body")
        )
        assertTrue(withRaw.contains("\"raw\":\"body\""))
    }

    @Test
    fun escapingKeepsOneEventPerLine() {
        val line = DiagnosticsCodec.encode(
            DiagnosticEvent(1, DiagKind.SCREEN_STATE, mapOf("copy" to "line1\nline2 \"quoted\""))
        )
        assertFalse(line.contains("\n"))
        assertTrue(line.contains("\\n"))
        assertTrue(line.contains("\\\""))
    }
}
