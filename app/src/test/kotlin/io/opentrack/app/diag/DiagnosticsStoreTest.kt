package io.opentrack.app.diag

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 3.6: opt-in means OFF writes nothing; ON records what the wire and the screen said. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsStoreTest {

    private lateinit var store: DiagnosticsStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = DiagnosticsStore(context, enabled = false) { 1_700_000_000_000L }
    }

    @Test
    fun disabledStoreCollectsNothing() {
        store.diagnostics.pairSearch(mapOf("from" to "SMET", "to" to "MYS"))
        store.diagnostics.screenState(mapOf("ui_state" to "LIVE"))
        assertTrue(store.logText().startsWith("(diagnostics disabled"))
        val file = java.io.File(
            ApplicationProvider.getApplicationContext<android.content.Context>().filesDir,
            "diagnostics/${DiagnosticsStore.LOG_NAME}",
        )
        assertFalse(file.exists() && file.length() > 0)
    }

    @Test
    fun enabledStoreRecordsWireStructureAndTheFrontendRows() {
        store.enabled = true
        val signature = store.wireSignatureAndStatus(
            body = java.io.File("src/test/resources/erail_pair_smet_mys_capture.txt").readText(),
            httpStatus = 200,
            elapsedMs = 63,
        )
        store.diagnostics.pairSearch(mapOf("from" to "SMET", "to" to "MYS") + signature)
        store.diagnostics.screenState(
            mapOf(
                "op" to "pair",
                "ui_state" to "LIVE",
                "rows" to "16228@23:00[via erail.in]",
                "count" to "4",
            )
        )
        val text = store.logText()
        assertTrue(text.contains("PAIR_SEARCH"))
        assertTrue(text.contains("SCREEN_STATE"))
        assertTrue(text.contains("http_status\":\"200"))
        assertTrue(text.contains("16228@23:00[via erail.in]"))
        // structure only: the payload's train names never enter the log
        assertFalse(text.contains("TLGP MYS EXP"))
    }

    @Test
    fun rawCaptureIsAttachedOnlyWhenOptedIn() {
        store.enabled = true
        store.captureRaw = false
        store.diagnostics.pairSearch(mapOf("x" to "1"), rawBody = store.attachRaw("SECRET-BODY"))
        assertFalse(store.logText().contains("SECRET-BODY"))
        store.captureRaw = true
        store.diagnostics.pairSearch(mapOf("x" to "2"), rawBody = store.attachRaw("SECRET-BODY2"))
        assertTrue(store.logText().contains("SECRET-BODY2"))
    }

    @Test
    fun shareAsFileBuildsAGrantedUriToTheLogOnly() {
        store.enabled = true
        store.diagnostics.export(mapOf("mode" to "manual"))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val chooser = store.shareAsFile(context)
        val intent = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertEquals(android.content.Intent.ACTION_SEND, intent?.action)
        assertTrue(intent!!.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
        assertTrue(uri.toString().contains("diagnostics"))
        assertTrue(store.logText().contains("\"mode\":\"file\""))
    }

    @Test
    fun shareAsTextPutsTheLogOnTheClipboard() {
        store.enabled = true
        store.diagnostics.screenState(mapOf("ui_state" to "COULD_NOT_LOOK"))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store.shareAsText(context)
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        assertTrue((clip?.getItemAt(0)?.text ?: "").contains("COULD_NOT_LOOK"))
    }

    @Test
    fun clearingEmptiesTheLog() {
        store.enabled = true
        store.diagnostics.export(mapOf("mode" to "x"))
        store.clear()
        assertTrue(store.logText().trim().isEmpty())
    }
}
