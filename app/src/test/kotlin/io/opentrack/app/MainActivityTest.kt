package io.opentrack.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.opentrack.app.source.ErailPairSource
import io.opentrack.app.source.NtesRunningStatusSource
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
 * L2 screen tests for the two-view UI (search view -> detail view), the exact flow the owner runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        SourceLocator.erailPairSource = null
        SourceLocator.erailSource = null
        SourceLocator.ntesStatusSource = null
    }

    private val pairCapture =
        java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText()

    private fun showFirstTrain() {
        compose.onNodeWithText("from station code").performTextInput("smet")
        compose.onNodeWithText("to station code").performTextInput("mys")
        compose.onNodeWithText("search").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("16228 · TLGP MYS EXP — 23:00 · via erail.in")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theSearchViewRendersFirst() {
        compose.onNodeWithText("from station code").assertIsDisplayed()
        compose.onNodeWithText("or train number").assertIsDisplayed()
        // the search view is what opens; the detail view has a back button instead
        compose.onAllNodesWithText("← search").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    /** The owner's ask: selecting a train hides the search and shows the detail view. */
    @Test
    fun selectingATrainOpensTheDetailViewAndHidesTheSearch() {
        showFirstTrain()
        compose.onAllNodesWithText("live status")[0].performClick()
        // the search fields are gone; the detail header is present
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("← search").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("from station code").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("← search").assertIsDisplayed()
    }

    @Test
    fun backFromTheDetailViewReturnsToTheSearch() {
        showFirstTrain()
        compose.onAllNodesWithText("live status")[0].performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("← search").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("← search").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("from station code").assertIsDisplayed()
    }

    @Test
    fun thePositionCardNamesTheLastReportedAndNextStop() {
        SourceLocator.erailPairSource = ErailPairSource(
            { TransportReply(200, pairCapture) }, enabled = true
        )
        SourceLocator.ntesStatusSource = NtesRunningStatusSource({ url ->
            object : java.net.HttpURLConnection(url) {
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun connect() {}
                override fun getOutputStream() = object : java.io.OutputStream() {
                    override fun write(b: Int) {}
                    override fun write(b: ByteArray, off: Int, len: Int) {}
                }
                override fun getResponseCode() = 200
                override fun getInputStream() = java.io.ByteArrayInputStream(
                    """
                    {"TrainNo":"16228","LUPDT":"Train departed SMET at 22:58",
                     "STNS":[
                       {"SC":"SMET","SN":"Shivamogga Town","STA":"22:55","STD":"23:00",
                        "ETA":"23:05","ETD":"23:10","DARR":"10","DDEP":"10","DIST":"0","Halt":"5"},
                       {"SC":"SME","SN":"Shivamogga H","STA":"23:05","STD":"23:10",
                        "ETA":"23:05","ETD":"23:10","DARR":"0","DDEP":"0","DIST":"3","Halt":"2"}
                     ]}
                    """.trimIndent().toByteArray()
                )
                override fun getErrorStream(): java.io.InputStream? = null
            }
        }, { 1_700_000_000_000L })

        showFirstTrain()
        compose.onAllNodesWithText("live status")[0].performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("Last reported:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // the card states the position in one sentence (the reference app's model)
        compose.onNodeWithText("Last reported: SMET Shivamogga Town", substring = true).assertExists()
        compose.onNodeWithText("Next: SME Shivamogga H", substring = true).assertExists()
    }

    @Test
    fun aFailedLiveStatusNamesItself() {
        SourceLocator.erailPairSource = ErailPairSource(
            { TransportReply(200, pairCapture) }, enabled = true
        )
        SourceLocator.ntesStatusSource = NtesRunningStatusSource({ url ->
            object : java.net.HttpURLConnection(url) {
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun connect() {}
                override fun getOutputStream() = object : java.io.OutputStream() {
                    override fun write(b: Int) {}
                    override fun write(b: ByteArray, off: Int, len: Int) {}
                }
                override fun getResponseCode() = 200
                override fun getInputStream() =
                    java.io.ByteArrayInputStream("~~~~~nope".toByteArray())
                override fun getErrorStream(): java.io.InputStream? = null
            }
        }, { 1L })
        showFirstTrain()
        compose.onAllNodesWithText("live status")[0].performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("could not get the live status", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("could not get the live status", substring = true).assertExists()
    }
}
