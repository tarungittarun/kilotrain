package in.opentrack.app.search

import androidx.test.core.app.ApplicationProvider
import in.opentrack.app.asset.OfflineAsset
import in.opentrack.app.asset.OfflineAssetCodec
import in.opentrack.app.store.OpenTrackStore
import in.opentrack.core.contract.Clock
import in.opentrack.core.contract.Provenance
import in.opentrack.core.contract.ProvenanceKind
import in.opentrack.core.contract.TrainIdentity
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

    private fun identity(number: String, from: String, to: String) = TrainIdentity(
        number, "name-$number", from, to,
        Provenance(ProvenanceKind.VIA_SOURCE, "erail.in", clock.now),
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = OpenTrackStore(context, clock)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun emptyAdmissionFreeAssetIsNotAnEmptyAnswer() {
        val engine = ResultsEngine(OfflineAssetCodec.emptyAsset(), store, clock)
        val outcome = engine.lookup("SMET", "NDLS")
        assertEquals(ResultsEngine.UiStateName.OFFLINE_UNVERIFIED, outcome.state)
        assertTrue(outcome.rows.isEmpty())
        assertEquals("no admitted source has published data yet; this is not an empty answer", outcome.note)
    }

    @Test
    fun assetRowsAreLabelledAndProvenanced() {
        val asset = OfflineAsset(
            schemaVersion = OfflineAssetCodec.SUPPORTED_SCHEMA,
            trains = listOf(identity("16228", "SMET", "NDLS")),
            schemaCompatible = true,
            degradeMessage = null,
        )
        val outcome = ResultsEngine(asset, store, clock).lookup("SMET", "NDLS")
        assertEquals(ResultsEngine.UiStateName.OFFLINE_UNVERIFIED, outcome.state)
        assertEquals(1, outcome.rows.size)
        assertEquals("via erail.in", outcome.rows.first().provenanceLabel)
        assertNull(outcome.rows.first().timeDue) // no minutes invented (3.4)
    }

    @Test
    fun schemaMismatchDegradesWithAMessageProtocolChanged() {
        val asset = OfflineAsset(99, emptyList(), false, "older format")
        val outcome = ResultsEngine(asset, store, clock).lookup("SMET", "NDLS")
        assertEquals(ResultsEngine.UiStateName.PROTOCOL_CHANGED, outcome.state)
        assertEquals("older format", outcome.note)
    }

    @Test
    fun freshCacheRendersCachedState() {
        store.putCache("SMET", "NDLS", """{"rows":[{"number":"16228","name":"n","from":"SMET","to":"NDLS"}]}""", clock.now)
        val outcome = ResultsEngine(OfflineAssetCodec.emptyAsset(), store, clock).lookup("SMET", "NDLS")
        assertEquals(ResultsEngine.UiStateName.CACHED, outcome.state)
        assertEquals(1, outcome.rows.size)
    }

    @Test
    fun expiredCacheIsAMissNotALie() {
        store.putCache("SMET", "NDLS", """{"rows":[{"number":"16228","name":"n","from":"SMET","to":"NDLS"}]}""", clock.now)
        clock.now += ResultsEngine.CACHE_TTL_MS + 1_000
        val outcome = ResultsEngine(OfflineAssetCodec.emptyAsset(), store, clock).lookup("SMET", "NDLS")
        assertEquals(ResultsEngine.UiStateName.OFFLINE_UNVERIFIED, outcome.state)
    }

    @Test
    fun everySourceIsOffUntilACaptureExists() {
        assertTrue(ResultsEngine.ENABLED_SOURCES.values.none { it })
    }
}
