package io.opentrack.core.ntes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wire format is pinned to a vector computed with an independent implementation (Python). */
class NtesCryptoTest {

    @Test
    fun base64MatchesTheAlphabet() {
        assertEquals("TWFu", String(NtesCrypto.base64("Man".toByteArray())))
        assertEquals("TWE=", String(NtesCrypto.base64("Ma".toByteArray())))
        assertEquals("TQ==", String(NtesCrypto.base64("M".toByteArray())))
    }

    @Test
    fun base64RoundTrips() {
        val data = "service=TrainRunningMob&subService=ShowFullRunJson&trainNo=16228".toByteArray()
        assertArrayEquals(data, NtesCrypto.base64Decode(String(NtesCrypto.base64(data))))
    }

    /** Pinned vector: computed with pycryptodome for this exact payload. */
    @Test
    fun requestEnvelopeMatchesThePinnedVector() {
        val payload = "service=TrainRunningMob&subService=ShowFullRunJson&trainNo=16228&startDate=2026-10-12"
        val wire = NtesCrypto.buildRequest(payload)
        assertEquals("559896E803947EAC2D88F391A2ECF264", wire.substringBefore("#"))
        assertTrue(wire.substringAfter("#").startsWith("6430384136355152636A2B7345435539465A7252"))
        assertEquals(289, wire.length)
    }

    @Test
    fun responseDecodeRoundTripsThroughTheSameLayers() {
        // the response layer is the same construction: md5#hex(base64(aes(json)))
        val json = """{"TrainNo":"16228","DelayDep":"On Time"}"""
        val wire = NtesCrypto.buildRequest(json) // identical construction to the server's encoding layer
        assertEquals(json, NtesCrypto.decodeResponse(wire))
    }

    @Test
    fun decodeOfGarbageIsNullNeverACrash() {
        assertNull(NtesCrypto.decodeResponse("not-hex-not-base64"))
        assertNull(NtesCrypto.decodeResponse(""))
        assertNull(NtesCrypto.decodeResponse("ZZZZ#{"))
    }

    private fun assertArrayEquals(a: ByteArray, b: ByteArray) {
        assertTrue("arrays differ", a.contentEquals(b))
    }
}
