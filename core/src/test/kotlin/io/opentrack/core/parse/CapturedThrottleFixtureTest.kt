package io.opentrack.core.parse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Graduation (7.2): the probe's throttle capture graduated into a test. This is the exact body
 * measured on 2026-10-11 from erail.in (fixture: poc/fixtures/s2_erail/documented_base_path.txt).
 */
class CapturedThrottleFixtureTest {

    @Test
    fun capturedThrottleBodyIsRefusedByTheShapeGuard() {
        val body = java.io.File("src/test/resources/throttle_s2.txt").readText()
        val sig = ShapeSignature(
            delimiterCounts = mapOf('~' to body.count { it == '~' }),
            hasFeedStamp = body.contains(Regex("""\d{4}-\d{2}-\d{2}-\d{1,2}-\d{1,2}-\d{1,2}""")),
            lineCount = body.lines().count { it.isNotBlank() },
        )
        assertFalse("the captured throttle body must never look like a trains payload",
            sig.looksLikeTrainsPayload())
    }

    @Test
    fun capturedThrottleBodyMatchesTheRecordedSignature() {
        val body = java.io.File("src/test/resources/throttle_s2.txt").readText()
        assertTrue(body.contains("Please try again after some time."))
    }
}
