package io.opentrack.app.asset

import io.opentrack.core.contract.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineAssetCodecTest {

    private val sample = java.io.File("src/test/resources/s3_asset_sample.json").readText()

    @Test
    fun parsesSchemaTwoTrains() {
        val asset = OfflineAssetCodec.parse(sample)
        assertTrue(asset.schemaCompatible)
        assertEquals(2, asset.schemaVersion)
        assertEquals(2, asset.trains.size)
        val row = asset.lookupByPair("SMET", "MYS").single()
        assertEquals("56269", row.number)
        assertEquals("10:30", row.departure)
        assertEquals("16:55", row.arrival)
    }

    @Test
    fun identityProvenanceIsTimetableAndLeastTrusted() {
        val row = OfflineAssetCodec.parse(sample).lookupByPair("SMET", "MYS").single()
        val provenance = row.toIdentity().provenance
        assertEquals("timetable", provenance.label(1_700_000_000_000))
        assertTrue(provenance.isLeastTrusted)
    }

    @Test
    fun schemaMismatchDegradesWithoutCrashing() {
        val asset = OfflineAssetCodec.parse(sample.replace("\"schema\": 2", "\"schema\": 1"))
        assertFalse(asset.schemaCompatible)
        assertTrue(asset.trains.isEmpty())
        assertTrue(asset.degradeMessage!!.contains("older format"))
    }

    @Test
    fun corruptAssetDegradesWithoutCrashing() {
        val asset = OfflineAssetCodec.parse("this is not json")
        assertFalse(asset.schemaCompatible)
        assertNull(asset.trains.firstOrNull())
    }

    @Test
    fun lookupByPairNeverMixesDirections() {
        val asset = OfflineAssetCodec.parse(sample)
        assertEquals(1, asset.lookupByPair("SMET", "MYS").size)
        assertTrue(asset.lookupByPair("MYS", "SMET").isEmpty())
    }
}
