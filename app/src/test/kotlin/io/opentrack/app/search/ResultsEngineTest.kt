package io.opentrack.app.search

import androidx.test.core.app.ApplicationProvider
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.asset.OfflineAssetCodec
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.TransportReply
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.core.contract.Clock
import org.junit.After
import org.junit.Assert.assertEquals
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

    /** ADR 0009 (owner 2026-10-12): years-old fallback rows are NEVER a pair answer. */
    @Test
    fun aPairQueryIsNeverAnsweredFromThe2016Fallback() {
        val outcome = ResultsEngine(asset, store, clock).lookup("TLGP", "MYS")
        assertEquals(ResultsEngine.UiStateName.COULD_NOT_LOOK, outcome.state)
        assertTrue(outcome.rows.isEmpty())
        assertTrue(outcome.note!!.startsWith("could not look up trains between TLGP and MYS"))
        assertTrue(outcome.note!!.contains("will not show years-old timetable data"))
    }

    @Test
    fun aPairAbsentFromTheFallbackIsAlsoCouldNotLookNotEmptyAnswer() {
        val outcome = ResultsEngine(asset, store, clock).lookup("NDLS", "BCT")
        assertEquals(ResultsEngine.UiStateName.COULD_NOT_LOOK, outcome.state)
        assertTrue(outcome.rows.isEmpty())
    }

    /** The live train-number path is the one that answers with current data. */
    @Test
    fun liveNumberSearchLabelsViaTheSourceWithTheFeedStamp() {
        val capture = java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()
        val source = ErailTrainSource({ TransportReply(200, capture) }, enabled = true)
        clock.now = 1_800_000_000_000L
        val outcome = ResultsEngine(asset, store, clock, source).lookupByNumber("56269")
        assertEquals(ResultsEngine.UiStateName.LIVE, outcome.state)
        assertEquals("erail.in", outcome.bannerSource)
        assertEquals("via erail.in", outcome.rows.single().provenanceLabel)
        assertEquals("10:20", outcome.rows.single().timeDue)
        assertTrue(outcome.note!!.startsWith("as of 2026-10-06"))
    }

    /** When the live source cannot answer, the failure is named and NOTHING old is shown. */
    @Test
    fun liveFailureNamesItselfAndShowsNoOldData() {
        val source = ErailTrainSource(
            { TransportReply(200, "~~~~~Please try again after some time.") }, enabled = true
        )
        val outcome = ResultsEngine(asset, store, clock, source).lookupByNumber("56269")
        assertEquals(ResultsEngine.UiStateName.COULD_NOT_LOOK, outcome.state)
        assertTrue(outcome.rows.isEmpty())
        assertTrue(outcome.note!!.startsWith("could not reach erail.in for train 56269 (SHAPE_UNKNOWN"))
        assertTrue(outcome.note!!.contains("No old data is shown in its place"))
    }

    /** 10.3: station codes keep all four letters through the path. */
    @Test
    fun fourLetterCodesSurviveTheLookup() {
        assertEquals("TLGP", ResultsEngine.codePreserved(" tlgp "))
        assertEquals("MYS", ResultsEngine.codePreserved("mys"))
    }

    /** Flags follow captures: the number endpoint has one (ADR 0008); pair and NTES do not. */
    @Test
    fun flagsFollowCaptures() {
        assertTrue(io.opentrack.app.source.FeatureFlags.ERAIL_TRAIN_NUMBER)   // capture 2026-10-11
        assertTrue(!io.opentrack.app.source.FeatureFlags.ERAIL_PAIR)         // throttle-only, no capture
        assertTrue(!io.opentrack.app.source.FeatureFlags.NTES)               // no envelope capture
    }
}
