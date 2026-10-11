package io.opentrack.core.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTypesTest {

    private val prov = Provenance(ProvenanceKind.VIA_SOURCE, "erail.in", 1L)

    @Test
    fun emptyListingAndFailureAreDifferentTypes() {
        val empty: PairLookup = PairLookup.Answered(
            PairListing.empty("SMET", "NDLS", prov)
        )
        val failed: PairLookup = PairLookup.CouldNotLook(
            PairListing.failed("SMET", "NDLS", SourceFailure.TIMEOUT, "no answer in 8 s")
        )
        assertTrue(empty is PairLookup.Answered)
        assertTrue(failed is PairLookup.CouldNotLook)
        // an empty answer and a could-not-look never share a type (3.3)
        assertFalse(empty.javaClass == failed.javaClass)
    }

    @Test
    fun emptyListingCarriesNoRowsAndAFailureCarriesNoneEither() {
        val empty = PairListing.empty("SMET", "NDLS", prov)
        assertEquals(0, empty.rows.size)
        val failure = PairListing.failed("SMET", "NDLS", SourceFailure.OFFLINE, "airplane mode")
        assertEquals(SourceFailure.OFFLINE, failure.failure)
    }

    /** 10.3: same number, two names - identity is what disagrees, not what gets cleaned. */
    @Test
    fun sameNumberDifferentNameDisagrees() {
        val a = TrainIdentity("16228", "SMET NDLS Exp", "SMET", "NDLS", prov)
        val b = TrainIdentity("16228", "Sengottai New Delhi Exp", "SMET", "NDLS", prov)
        assertTrue(a.disagreesWith(b))
    }

    /** 10.3: 16221 keyed in the reverse direction must CONFLICT, not silently vanish/merge. */
    @Test
    fun reverseKeyedTrainConflicts() {
        val forward = TrainIdentity("16221", "X", "SMET", "NDLS", prov)
        val reverseKeyed = TrainIdentity("16221", "X", "NDLS", "SMET", prov)
        assertTrue(forward.conflictsWith(reverseKeyed))
        assertFalse(forward.disagreesWith(reverseKeyed)) // conflict is about the route
    }

    /** 10.3: 4-letter station codes are never truncated by the contract. */
    @Test
    fun fourLetterStationCodesAreNotTruncated() {
        val row = TrainIdentity("16228", "SMET-NDLS Exp", "SMET", "NDLS", prov)
        assertEquals("SMET", row.fromStation)
        assertEquals("NDLS", row.toStation)
    }

    /** 6.2: a provenance is a REQUIRED constructor argument - blank source fails loudly. */
    @Test(expected = IllegalArgumentException::class)
    fun provenanceWithoutSourceIsRejected() {
        Provenance(ProvenanceKind.LIVE, "   ", 1L)
    }
}
