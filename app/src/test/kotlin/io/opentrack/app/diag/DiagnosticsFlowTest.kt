package io.opentrack.app.diag

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.opentrack.app.MainActivity
import io.opentrack.app.source.ErailPairSource
import io.opentrack.app.source.SourceLocator
import io.opentrack.app.source.TransportReply
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The owner's flow, end to end through the real UI: enable diagnostics, run the operations a
 * passenger runs, share. No network (the source is faked), the same code paths a phone uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        SourceLocator.erailPairSource = null
        SourceLocator.erailSource = null
    }

    private val pairCapture =
        java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText()

    private fun enableDiagnostics() {
        compose.onNodeWithText("diagnostics").assertIsDisplayed()
        // the switch is the second Switch in the bar; toggle it by clicking its row
        compose.onAllNodesWithText("off")[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun theOwnerFlow_enable_operate_share() {
        // arrange: a live pair answer on the fake transport
        SourceLocator.erailPairSource = ErailPairSource(
            { TransportReply(200, pairCapture) }, enabled = true
        )
        enableDiagnostics()

        // 1) operate: a station-pair search, exactly as a passenger would
        compose.onNodeWithText("from station code").performTextInput("tlgt")
        compose.onNodeWithText("to station code").performTextInput("mys")
        compose.onNodeWithText("search").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("16228 · TLGP MYS EXP — 23:00 · via erail.in")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 2) the log file now holds the operation AND the frontend result
        val log = java.io.File(
            compose.activity.filesDir,
            "diagnostics/${DiagnosticsStore.LOG_NAME}",
        ).readText()
        assertTrue(log.contains("PAIR_SEARCH"))
        assertTrue(log.contains("SCREEN_STATE"))
        assertTrue(log.contains("16228@23:00[via erail.in]"))
        assertTrue(log.contains("ui_state\":\"LIVE"))
    }
}
