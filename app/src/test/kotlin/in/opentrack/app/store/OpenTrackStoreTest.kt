package in.opentrack.app.store

import androidx.test.core.app.ApplicationProvider
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
class OpenTrackStoreTest {

    private lateinit var store: OpenTrackStore
    private val clock = object : Clock {
        var now = 1_700_000_000_000L
        override fun nowMillis(): Long = now
    }

    @Before
    fun setUp() {
        store = OpenTrackStore(ApplicationProvider.getApplicationContext(), clock)
    }

    @After
    fun tearDown() = store.close()

    @Test
    fun freshCacheIsReturned() {
        store.putCache("SMET", "NDLS", "{}", clock.now)
        val row = store.freshCache("SMET", "NDLS", ttlMillis = 60_000)
        assertEquals("{}", row?.payload)
    }

    @Test
    fun staleCacheIsNotReturned() {
        store.putCache("SMET", "NDLS", "{}", clock.now - 120_000)
        assertNull(store.freshCache("SMET", "NDLS", ttlMillis = 60_000))
    }

    @Test
    fun zeroStampedCacheIsTheLeastTrustedState() {
        store.putCache("SMET", "NDLS", "{}", 0)
        assertNull(store.freshCache("SMET", "NDLS", ttlMillis = 60_000))
    }

    /** 6.4: an override CORRECTS a row instead of merely contradicting it. */
    @Test
    fun overrideCorrectsIdentity() {
        val cold = TrainIdentity(
            "16228", "SMET-NDLS EXP (unverified)", "SMET", "NDLS",
            Provenance(ProvenanceKind.TIMETABLE, "offline asset", 0),
        )
        store.putOverride(
            OpenTrackStore.Override("16228", "SMET NDLS SF EXP", "SMET", "NDLS", clock.now, "NTES")
        )
        val corrected = store.identityFor("16228", cold)
        assertEquals("SMET NDLS SF EXP", corrected.name)
        assertEquals("NTES", corrected.provenance.source)
        assertEquals(ProvenanceKind.LIVE, corrected.provenance.kind)
    }

    @Test
    fun noOverrideKeepsTheColdRow() {
        val cold = TrainIdentity(
            "16228", "cold", "SMET", "NDLS",
            Provenance(ProvenanceKind.TIMETABLE, "offline asset", 0),
        )
        assertEquals(cold, store.identityFor("16228", cold))
    }

    /** 6.4: an unwritten migration clears the cache and says so - never stale-as-fresh. */
    @Test
    fun upgradeClearsCacheRatherThanShowingStaleAsFresh() {
        store.putCache("SMET", "NDLS", "{}", clock.now)
        store.onUpgrade(store.writableDatabase, 1, 2)
        assertNull(store.freshCache("SMET", "NDLS", ttlMillis = 60_000))
        // and the database is still usable after the clear
        store.putCache("SMET", "NDLS", "{\"rows\":[]}", clock.now)
        assertEquals("{\"rows\":[]}", store.freshCache("SMET", "NDLS", 60_000)?.payload)
    }

    @Test
    fun schemaVersionIsPinned() {
        assertEquals(1, OpenTrackStore.DATABASE_VERSION)
    }
}
