package io.opentrack.app.diag

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Saves the diagnostics log where the owner can actually find and attach it: the public Downloads
 * collection. Returns the saved Uri, or null when the device refuses (a stated failure, never a
 * silent no-op).
 */
object DiagnosticsSaver {

    fun saveToDownloads(context: Context, source: File): Uri? {
        val name = "opentrack-diagnostics-${System.currentTimeMillis()}.txt"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: return null
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val target = File(dir, name)
                source.copyTo(target, overwrite = true)
                FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", target)
            }
        } catch (e: Exception) {
            null
        }
    }
}
