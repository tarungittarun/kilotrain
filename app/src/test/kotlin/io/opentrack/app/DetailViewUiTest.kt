package io.opentrack.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.asset.OfflineAssetCodec
import io.opentrack.app.search.ResultsViewModel
import io.opentrack.app.source.ErailPairSource
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.NtesRunningStatusSource
import io.opentrack.app.source.SourceLocator
import io.opentrack.app.source.TransportReply
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.core.contract.Clock
import io.opentrack.core.diag.Diagnostics
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two-view UI (search view -> detail view), driven through the ViewModel so the assertions do
 * not depend on Robolectric's flaky lazy-list clicks; the live tap path itself is covered by the
 * L1/L2 engine and source tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailViewUiTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        SourceLocator.erailPairSource = null
        SourceLocator.erailSource = null
        SourceLocator.ntesStatusSource = null
    }

    private val clock = object : Clock {
        var now = 1_700_000_000_000L
        override fun nowMillis(): Long = now
    }
    private val asset: OfflineAsset by lazy {
        OfflineAssetCodec.parse(java.io.File("src/test/resources/s3_asset_sample.json").readText())
    }
    private val logged = mutableListOf<String>()
    private val diagnostics = Diagnostics({ logged.add(it) }, { true }, { clock.now })

    private fun viewModel(): ResultsViewModel {
        val store = OpenTrackStore(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(), clock,
        )
        return ResultsViewModel(
            asset = asset, store = store, clock = clock,
            erailSource = ErailTrainSource({ TransportReply(200, trainCapture) }),
            ntesStatusSource = ntesSource(),
            diagnostics = diagnostics,
        )
    }

    private val trainCapture =
        java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()

    private val pairCapture =
        java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText()

    private fun ntesSource() = NtesRunningStatusSource({ url ->
        object : java.net.HttpURLConnection(url) {
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun connect() {}
            override fun getOutputStream() = object : java.io.OutputStream() {
                override fun write(b: Int) {}
                override fun write(b: ByteArray, off: Int, len: Int) {}
            }
            override fun getResponseCode() = 200
            override fun getHeaderFields(): MutableMap<String, MutableList<String>> =
                mutableMapOf("Set-Cookie" to mutableListOf("JSESSIONID=x; Path=/"))
            override fun getInputStream() = java.io.ByteArrayInputStream(
                """
                {"TrainNo":"20652","LUPDT":"Train departed SMET at 07:10",
                 "DelayDep":"12 min","Platform":"2",
                 "STNS":[
                   {"SC":"SMET","SN":"Shivamogga Town","STA":"07:05","STD":"07:10",
                    "ETA":"07:18","ETD":"07:22","DARR":"13","DDEP":"12","PF":"2","DIST":"0","Halt":"5"},
                   {"SC":"SME","SN":"Shivamogga H","STA":"07:15","STD":"07:20",
                    "ETA":"07:15","ETD":"07:20","DARR":"0","DDEP":"0","DIST":"3","Halt":"2"},
                   {"SC":"MYS","SN":"Mysore Jn","STA":"12:00","STD":"12:00/Destination",
                    "ETA":"","ETD":"","DIST":"267","Halt":"0"}
                 ]}
                """.trimIndent().toByteArray()
            )
            override fun getErrorStream(): java.io.InputStream? = null
        }
    }, { clock.now })

    @Test
    fun theDetailViewShowsThePositionCardAndHidesTheSearchFields() {
        val viewModel = viewModel()
        viewModel.showLiveStatus("20652")
        compose.setContent { io.opentrack.app.ui.ResultsScreen(viewModel = viewModel, null) }

        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("Last reported:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // the position card, in one sentence
        compose.onNodeWithText("Last reported: SMET Shivamogga Town", substring = true).assertExists()
        compose.onNodeWithText("Next: SME Shivamogga H", substring = true).assertExists()
        // the search fields are gone
        assertTrue(
            compose.onAllNodesWithText("from station code").fetchSemanticsNodes().isEmpty()
        )
        // back returns to the search view
        compose.onNodeWithText("← search").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("from station code").assertIsDisplayed()
    }

    @Test
    fun theDetailViewShowsActualAndExpectedPerStop() {
        val viewModel = viewModel()
        viewModel.showLiveStatus("20652")
        compose.setContent { io.opentrack.app.ui.ResultsScreen(viewModel = viewModel, null) }
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("Last reported:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // a reported stop: actual + delay
        compose.onNodeWithText("actual 07:18/07:22", substring = true).assertExists()
        compose.onNodeWithText("late 13 min", substring = true).assertExists()
        // a stop with no actual: expected or not reported - never a fabricated actual
        compose.onNodeWithText("not reported", substring = true).assertExists()
        compose.onNodeWithText("Destination", substring = true).assertExists()
    }
}
