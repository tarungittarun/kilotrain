package io.opentrack.core.parse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeSignatureTest {

    /** The measured throttle body must never enter the parsing path (LEARNINGS 1). */
    @Test
    fun throttleShapeIsNotATrainsPayload() {
        assertFalse(ShapeSignature.THROTTLE_SHAPE.looksLikeTrainsPayload())
    }

    @Test
    fun realPayloadShapePasses() {
        val shape = ShapeSignature(
            delimiterCounts = mapOf('~' to 40),
            hasFeedStamp = true,
            lineCount = 12,
        )
        assertTrue(shape.looksLikeTrainsPayload())
    }

    /** A stamp-less multi-row body is still refused: the stamp is the honest "as of". */
    @Test
    fun multiRowWithoutFeedStampIsRefused() {
        val shape = ShapeSignature(delimiterCounts = mapOf('~' to 9), hasFeedStamp = false, lineCount = 5)
        assertFalse(shape.looksLikeTrainsPayload())
    }
}
