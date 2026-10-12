package io.opentrack.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.opentrack.app.source.ErailPairSource
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.SourceLocator
import io.opentrack.app.source.TransportReply
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L2 screen tests (10.1): inflation, the exact copy of every state the search path can produce,
 * and no network in tests (the source is faked through SourceLocator). Device-dependent proofs
 * live in their own layers (ADR 0006).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        SourceLocator.erailSource = null
        SourceLocator.erailPairSource = null
    }

    @Test
    fun firstRenderIsHonestAndNeverClaimsAnAnswer() {
        compose.onNodeWithText("offline - showing unverified timetable data.")
            .assertIsDisplayed()
    }

    /** The pair query runs on the device; here its transport is faked to the throttle body. */
    @Test
    fun aPairSearchSaysItCouldNotLookInsteadOfShowingOldRows() {
        SourceLocator.erailPairSource = ErailPairSource(
            { TransportReply(200, "~~~~~Please try again after some time.") }, enabled = true
        )
        compose.onNodeWithText("from station code").performTextInput("tlgt")
        compose.onNodeWithText("to station code").performTextInput("mys")
        compose.onNodeWithText("search").performClick()
        // the engine runs on Dispatchers.Default, outside the compose clock: wait for the state
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("could not look this up. no old data is shown in its place.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("could not look this up. no old data is shown in its place.")
            .assertIsDisplayed()
        // and nothing old is on screen to contradict it
        compose.onAllNodesWithText("16205 · Talaguppa Mysore Intercity Express — 14:45 · timetable")
            .assertCountEquals(0)
        compose.onAllNodesWithText("56269 · Shimoga Mysore Passenger — 10:30 · timetable")
            .assertCountEquals(0)
    }

    /** Priority 1+2: a train number answers live from erail.in with the feed's own stamp. */
    @Test
    fun aTrainNumberAnswersLive() {
        SourceLocator.erailSource = ErailTrainSource(
            { TransportReply(200, liveCapture) }, enabled = true
        )
        compose.onNodeWithText("or train number").performTextInput("56269")
        compose.onNodeWithText("show train").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("16228 · TLGP MYS EXP — 23:00 · via erail.in")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("16228 · TLGP MYS EXP — 23:00 · via erail.in")
            .assertExists()
    }

    /** When the live source cannot answer, the failure is named and no old row appears. */
    @Test
    fun aLiveFailureNamesItselfAndShowsNoOldRow() {
        SourceLocator.erailSource = ErailTrainSource(
            { TransportReply(200, "~~~~~Please try again after some time.") }, enabled = true
        )
        compose.onNodeWithText("or train number").performTextInput("56269")
        compose.onNodeWithText("show train").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("could not look this up. no old data is shown in its place.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("56269 · Shimoga Mysore Passenger — 10:30 · timetable")
            .assertCountEquals(0)
    }

    private val liveCapture = java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()
    private val pairCapture =
        java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText()
}
