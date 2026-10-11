package in.opentrack.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import in.opentrack.app.store.OpenTrackStore
import in.opentrack.core.contract.Provenance
import in.opentrack.core.contract.ProvenanceKind
import in.opentrack.core.contract.SystemClock
import in.opentrack.core.contract.TrainIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L3 (10.1): the override-store flow on a real device/emulator, no egress.
 * L4 network proof is DEFERRED, not faked: no source has a captured payload (ADR 0002), so
 * fetch->parse->store->UI is claimed by nobody. QA_MATRIX row 9 says so.
 */
@RunWith(AndroidJUnit4::class)
class StoreOverrideFlowTest {

    private lateinit var store: OpenTrackStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(OpenTrackStore.DATABASE_NAME)
        store = OpenTrackStore(context, SystemClock)
    }

    @After
    fun tearDown() = store.close()

    @Test
    fun anOpenedTrainCorrectsTheColdStartCopy() {
        val cold = TrainIdentity(
            "16228", "old unverified name", "SMET", "NDLS",
            Provenance(ProvenanceKind.TIMETABLE, "offline asset", 0),
        )
        store.putOverride(
            OpenTrackStore.Override("16228", "railway's current name", "SMET", "NDLS",
                SystemClock.nowMillis(), "NTES")
        )
        val corrected = store.identityFor("16228", cold)
        assertEquals("railway's current name", corrected.name)
        assertEquals("NTES", corrected.provenance.source)
    }
}
