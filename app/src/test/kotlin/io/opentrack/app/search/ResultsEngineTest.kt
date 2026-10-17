package io.opentrack.app.search

import androidx.test.core.app.ApplicationProvider
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.asset.OfflineAssetCodec
import io.opentrack.app.source.ErailPairSource
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.PairLookupResult
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

    /**
     * ADR 0010 (owner 2026-10-12: "do it from your side"): the pair query runs on the passenger's
     * device, so the sandbox/CI throttle does not apply to it. Here the transport is faked with
     * the CAPTURED payload, proving the parse+label path end to end.
     */
    @Test
    fun aPairQueryAnswersLiveFromTheCapturedPairResponse() {
        val capture = java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText()
        val pairSource = ErailPairSource({ TransportReply(200, capture) }, enabled = true)
        val outcome = ResultsEngine(asset, store, clock, null, pairSource).lookup("SMET", "MYS")
        assertEquals(ResultsEngine.UiStateName.LIVE, outcome.state)
        assertEquals(4, outcome.rows.size)
        val row = outcome.rows.first()
        assertEquals("16228", row.number)
        assertEquals("TLGP MYS EXP", row.name)
        assertEquals("23:00", row.timeDue)
        assertEquals("via erail.in", row.provenanceLabel) // 3.2: never looks like a timetable
    }

    /** 12.3: a throttle body is a stated failure with a signature - never a parse, never stale. */
    @Test
    fun aThrottledPairResponseIsAStatedFailureWithASignature() {
        val pairSource = ErailPairSource(
            { TransportReply(200, "~~~~~Please try again after some time.") }, enabled = true
        )
        val outcome = ResultsEngine(asset, store, clock, null, pairSource).lookup("SMET", "MYS")
        assertEquals(ResultsEngine.UiStateName.COULD_NOT_LOOK, outcome.state)
        assertTrue(outcome.rows.isEmpty())
        assertTrue(outcome.note!!.contains("SHAPE_UNKNOWN"))
        assertTrue(outcome.note!!.contains("signature="))
        assertTrue(outcome.note!!.contains("No old data is shown in its place"))
    }

    /** ADR 0009: with the source disabled, the answer is still not years-old rows. */
    @Test
    fun aPairQueryIsNeverAnsweredFromThe2016Fallback() {
        val outcome = ResultsEngine(asset, store, clock).lookup("TLGP", "MYS")
        assertEquals(ResultsEngine.UiStateName.COULD_NOT_LOOK, outcome.state)
        assertTrue(outcome.rows.isEmpty())
        assertTrue(outcome.note!!.startsWith("could not look up trains between TLGP and MYS"))
        assertTrue(outcome.note!!.contains("No old data is shown in its place"))
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

    /**
     * Flags follow captures: the erail train-number endpoint has one (ADR 0008), the erail pair
     * endpoint is guarded by the measured live call (ADR 0011), and NTES carries the official app's
     * documented public envelope plus documented status fields (ADR 0012).
     */
    @Test
    fun flagsFollowCaptures() {
        assertTrue(io.opentrack.app.source.FeatureFlags.ERAIL_TRAIN_NUMBER)  // capture 2026-10-11
        assertTrue(io.opentrack.app.source.FeatureFlags.ERAIL_PAIR)          // guarded runtime query (ADR 0010)
        assertTrue(io.opentrack.app.source.FeatureFlags.NTES)                 // documented public envelope, guarded (ADR 0012)
    }
}
