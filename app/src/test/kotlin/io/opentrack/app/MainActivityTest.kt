package io.opentrack.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.opentrack.app.source.SourceLocator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L2 screen tests (10.1): inflation + the exact copy of the state the empty honest asset
 * produces. No railway egress, no emulator (ADR 0006 keeps device-dependent proofs in their
 * own layers).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstRenderIsHonestUnverifiedNeverAnEmptyAnswer() {
        compose.onNodeWithText("offline - showing unverified timetable data.")
            .assertIsDisplayed()
    }

    /** The owner's route now resolves from the labelled fallback - with its label visible. */
    @Test
    fun searchingTheOwnerRouteShowsOneLabelledRow() {
        compose.onNodeWithText("from station code").performTextInput("SMET")
        compose.onNodeWithText("to station code").performTextInput("MYS")
        compose.onNodeWithText("search").performClick()
        compose.waitForIdle()
        // rows sit below the fold on Robolectric's small screen: existence proves the render,
        // the banner above stays the visible-assert target.
        compose.onNodeWithText("56269 · Shimoga Mysore Passenger — 10:30 · timetable")
            .assertExists()
        compose.onNodeWithText("offline - showing unverified timetable data.")
            .assertIsDisplayed()
    }

    /** Priority 1: a train number alone is a complete query (fake source: no network in tests). */
    @Test
    fun aTrainNumberAloneResolves() {
        SourceLocator.erailSource = io.opentrack.app.source.ErailTrainSource(
            { io.opentrack.app.source.TransportReply(200, liveCapture) }, enabled = true
        )
        compose.onNodeWithText("or train number").performTextInput("56269")
        compose.onNodeWithText("show train").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("56269 · SMET MYS FAST PASS — 10:20 · via erail.in")
            .assertExists()
        SourceLocator.erailSource = null
    }

    private val liveCapture = java.io.File("src/test/resources/erail_trainno_56269_capture.txt").readText()

    /**
     * 3.3/3.4: a pair the fallback does not cover is not an empty answer. SMET->NDLS is
     * measured-absent from the pinned full asset (2026-10-11), so this test holds on the
     * committed fixture AND on the CI-generated asset.
     */
    @Test
    fun anUnknownPairSaysSoInsteadOfClaimingNoTrains() {
        compose.onNodeWithText("from station code").performTextInput("SMET")
        compose.onNodeWithText("to station code").performTextInput("NDLS")
        compose.onNodeWithText("search").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("this pair is not in the offline fallback data; that is not proof that no trains run today")
            .assertExists()
        compose.onAllNodesWithText("no trains found for this route today")
            .assertCountEquals(0)
    }
}
