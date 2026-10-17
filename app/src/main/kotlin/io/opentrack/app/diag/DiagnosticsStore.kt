package io.opentrack.app.diag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.opentrack.core.diag.Diagnostics
import io.opentrack.core.diag.DiagnosticsCodec
import java.io.File

/**
 * Local, opt-in diagnostics (3.6). Nothing is written while [enabled] is false; the log lives in
 * the app's private storage and leaves the device only when the user taps export/share.
 *
 * Share paths requested by the owner:
 *  * share as FILE (a .jsonl attachment through a FileProvider - other apps get read access to
 *    that one file, nothing else), and
 *  * share as TEXT (the whole log on the clipboard for copy/paste).
 */
class DiagnosticsStore(
    context: Context,
    enabled: Boolean,
    clock: () -> Long = { System.currentTimeMillis() },
) {
    private val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
    private val logFile = File(dir, LOG_NAME)
    private val sink: (String) -> Unit = { line ->
        logFile.appendText(line + "\n")
        trim()
    }
    val diagnostics = Diagnostics(sink, { this.enabled }, clock) // the PROPERTY, not the ctor param

    var enabled: Boolean = enabled
        set(value) {
            field = value
            // flipping off must not leave a collector writing; flipping on starts a fresh session
            if (!value) logFile.writeText("")
        }

    /** Opt-in raw capture: the exact bodies, kept LOCAL, exported only when the user taps (3.5). */
    var captureRaw: Boolean = false

    fun wireSignatureAndStatus(body: String, httpStatus: Int, elapsedMs: Long): Map<String, String> =
        DiagnosticsCodec.signature(body) + mapOf(
            "http_status" to httpStatus.toString(),
            "elapsed_ms" to elapsedMs.toString(),
        )

    fun attachRaw(body: String): String? = body.takeIf { captureRaw }

    fun logText(): String =
        if (logFile.exists()) logFile.readText() else "(diagnostics disabled - nothing collected)"

    fun shareAsFile(context: Context): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", logFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "OpenTrack diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        diagnostics.export(mapOf("mode" to "file", "bytes" to logFile.length().toString()))
        return Intent.createChooser(intent, "Share diagnostics file")
    }

    /**
     * Saves the log into Downloads and returns a share intent for THAT file, so it can be attached
     * in a chat or opened from a file manager (a content URI from another app is often refused).
     */
    /** Saves the log into Downloads and returns the target Uri. No share sheet - just saves. */
    fun saveToLocal(context: Context): android.net.Uri? {
        return DiagnosticsSaver.saveToDownloads(context, logFile) ?: run {
            diagnostics.sourceFailure(mapOf("op" to "export", "failure" to "SAVE_FAILED",
                "detail" to "the downloads folder refused the copy"))
            null
        }.also { uri ->
            diagnostics.export(mapOf("mode" to "saved_file", "bytes" to logFile.length().toString(),
                "target" to uri.toString()))
        }
    }

    fun saveAndShare(context: Context): Intent? {
        val uri = saveToLocal(context) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "OpenTrack diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareAsText(context: Context): Intent {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OpenTrack diagnostics", logText()))
        diagnostics.export(mapOf("mode" to "clipboard", "bytes" to logFile.length().toString()))
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, logText())
            },
            "Share diagnostics text"
        )
    }

    fun clear() {
        logFile.writeText("")
    }

    /** The log never grows without bound (3.8: every loop has a budget). */
    private fun trim() {
        if (logFile.length() <= MAX_BYTES) return
        val text = logFile.readText()
        logFile.writeText(text.takeLast(MAX_BYTES / 2))
    }

    companion object {
        const val LOG_NAME = "opentrack-diagnostics.jsonl"
        const val MAX_BYTES = 512 * 1024
    }
}
