package io.opentrack.app.search

import androidx.test.core.app.ApplicationProvider
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.asset.OfflineAssetCodec
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.core.contract.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultsEngineTest {

    private lateinit var store: OpenTrackStore
    private val clock = object : Clock {
        var now = 1_700_000_000_000L
        override fun nowMillis(): Long = now
    }

    private val asset: OfflineAsset by lazy {
        OfflineAssetCodec.parse(
            java.io.File("src/test/resources/s3_asset_sample.json").readText()
        )
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = OpenTrackStore(context, clock)
    }

    @After
    fun tearDown() = store.close()

    @Test
    fun theOwnerRouteReturnsItsLabelledRow() {
        val outcome = ResultsEngine(asset, store, clock).lookup("SMET", "MYS")
        assertEquals(ResultsEngine.UiStateName.OFFLINE_UNVERIFIED, outcome.state)
        assertEquals(1, outcome.rows.size)
        val row = outcome.rows.first()
        assertEquals("56269", row.number)
        assertEquals("Shimoga Mysore Passenger", row.name)
        assertEquals("10:30", row.timeDue)
        assertEquals("timetable", row.provenanceLabel) // 3.2: never looks live
        assertTrue(outcome.note!!.contains("2016")) // 10.8: vintage stated with the rows
    }

    /** 3.3/3.4: an incomplete fallback must never produce an empty answer. */
    @Test
    fun aPairMissingFromTheFallbackIsNeverAnEmptyAnswer() {
        val outcome = ResultsEngine(asset, store, clock).lookup("NDLS", "BCT")
        assertEquals(ResultsEngine.UiStateName.OFFLINE_UNVERIFIED, outcome.state)
        assertTrue(outcome.rows.isEmpty())
        assertEquals("this pair is not in the offline fallback data; that is not proof that no trains run today",
            outcome.note)
    }

    @Test
    fun aSecondPairAlsoResolves() {
        val outcome = ResultsEngine(asset, store, clock).lookup("JBP", "NDLS")
        assertEquals(1, outcome.rows.size)
        assertEquals("12192", outcome.rows.first().number)
    }

    @Test
    fun aRepeatSearchServesTheCacheWithItsOwnLabel() {
        ResultsEngine(asset, store, clock).lookup("SMET", "MYS")
        val second = ResultsEngine(asset, store, clock).lookup("SMET", "MYS")
        assertEquals(ResultsEngine.UiStateName.CACHED, second.state)
        assertEquals("56269", second.rows.first().number)
        assertEquals("cached under a minute", second.rows.first().provenanceLabel)
    }

    @Test
    fun schemaMismatchDegradesWithAMessageProtocolChanged() {
        val broken = OfflineAsset(99, emptyList(), false, "older format")
        val outcome = ResultsEngine(broken, store, clock).lookup("SMET", "MYS")
        assertEquals(ResultsEngine.UiStateName.PROTOCOL_CHANGED, outcome.state)
        assertEquals("older format", outcome.note)
    }

    @Test
    fun emptyAssetIsNotAnEmptyAnswerEither() {
        val outcome = ResultsEngine(OfflineAssetCodec.emptyAsset(), store, clock).lookup("SMET", "MYS")
        assertEquals(ResultsEngine.UiStateName.OFFLINE_UNVERIFIED, outcome.state)
        assertEquals("this pair is not in the offline fallback data; that is not proof that no trains run today",
            outcome.note)
    }

    /** 10.3: station codes keep all four letters through the path. */
    @Test
    fun fourLetterCodesSurviveTheLookup() {
        assertEquals("SMET", ResultsEngine.codePreserved(" smet "))
        assertEquals("MYS", ResultsEngine.codePreserved("mys"))
    }

    @Test
    fun theRetiredPrefixTrainIsNotInTheFallback() {
        val numbers = asset.trains.map { it.number }
        assertTrue("06228 (retired prefix) must never ship", numbers.none { it == "06228" })
        assertNull(asset.trains.firstOrNull { it.number.startsWith("0") }?.number)
    }

    @Test
    fun everySourceIsOffUntilACaptureExists() {
        assertTrue(ResultsEngine.ENABLED_SOURCES.values.none { it })
    }
}
