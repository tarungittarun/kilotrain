package io.opentrack.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * The SEARCH view, exactly as the owner opens it. The detail view's rendering is covered by
 * DetailViewUiTest (deterministic - Robolectric does not reliably click lazy-list rows).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        SourceLocator.erailPairSource = null
    }

    private val pairCapture =
        java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText()

    @Test
    fun theSearchViewRendersFirst() {
        compose.onNodeWithText("from station code").assertIsDisplayed()
        compose.onNodeWithText("or train number").assertIsDisplayed()
        compose.onAllNodesWithText("← search").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun aPairSearchRendersResultCards() {
        SourceLocator.erailPairSource = ErailPairSource(
            { TransportReply(200, pairCapture) }, enabled = true
        )
        compose.onNodeWithText("from station code").performTextInput("smet")
        compose.onNodeWithText("to station code").performTextInput("mys")
        compose.onNodeWithText("search").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("16228 · TLGP MYS EXP — 23:00 · via erail.in")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("16228 · TLGP MYS EXP — 23:00 · via erail.in").assertExists()
        // the row offers the two entry points into the detail view
        compose.onAllNodesWithText("schedule").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        compose.onAllNodesWithText("live status").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }
}
