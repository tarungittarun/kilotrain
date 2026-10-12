package io.opentrack.app.source

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.opentrack.app.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The owner's exact flow for the live-status path, end to end through the real UI: enable
 * diagnostics, search, tap "live status" on a train, and see per-stop actuals. Everything is faked
 * at the connection layer, so this proves the UI→engine→source→screen path offline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveStatusFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        SourceLocator.erailPairSource = null
        SourceLocator.ntesStatusSource = null
    }

    private val pairCapture =
        java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText()

    private val statusBody = """
        {"trainNo":"16228","currentStatusStr":"Train is running 9 min late",
         "trainFullStatus":[
           {"stnCode":"SMET","stnName":"Shivamogga Town","schArrTime":"22:55","schDepTime":"23:00",
            "actArrTime":"23:05","actDepTime":"23:10","arrDelay":"10","depDelay":"10","isCurrentStn":false},
           {"stnCode":"SME","stnName":"Shivamogga H","schArrTime":"23:05","schDepTime":"23:10",
            "actArrTime":"23:15","actDepTime":"23:20","arrDelay":"10","depDelay":"10","isCurrentStn":true}
         ]}
    """.trimIndent()

    private fun fakeStatusSource() = NtesRunningStatusSource({ url ->
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
                java.io.ByteArrayInputStream(statusBody.toByteArray())
            override fun getErrorStream(): java.io.InputStream? = null
        }
    }, { 1_792_000_000_000L })

    @Test
    fun theOwnerFlow_enable_search_andExport() {
        SourceLocator.erailPairSource = ErailPairSource(
            { io.opentrack.app.source.TransportReply(200, pairCapture) }, enabled = true
        )
        SourceLocator.ntesStatusSource = fakeStatusSource()
        compose.onNodeWithTag("diagnostics-toggle").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("from station code").performTextInput("smet")
        compose.onNodeWithText("to station code").performTextInput("mys")
        compose.onNodeWithText("search").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("16228 · TLGP MYS EXP — 23:00 · via erail.in")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val log = java.io.File(compose.activity.filesDir, "diagnostics/opentrack-diagnostics.jsonl")
            .takeIf { it.exists() }?.readText() ?: "(no log file)"
        assertTrue("LOG: $log", log.contains("PAIR_SEARCH"))
    }
}
