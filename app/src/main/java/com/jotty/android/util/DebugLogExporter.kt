package com.jotty.android.util

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.jotty.android.BuildConfig
import com.jotty.android.R
import com.jotty.android.data.preferences.JottyInstance
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogExporter {
    sealed class WriteResult {
        data class Ok(val file: File) : WriteResult()

        data class Failed(val message: String) : WriteResult()
    }

    sealed class ShareResult {
        data object Started : ShareResult()

        data class Failed(val message: String) : ShareResult()
    }

    sealed class SaveResult {
        data class Saved(val displayName: String) : SaveResult()

        /** API 28 and below — caller should launch [ActivityResultContracts.CreateDocument]. */
        data class NeedsPicker(val file: File, val suggestedName: String) : SaveResult()

        data class Failed(val message: String) : SaveResult()
    }

    fun writeReport(
        context: Context,
        instance: JottyInstance?,
    ): WriteResult {
        val logs = AppLog.snapshot()
        if (logs.isBlank()) {
            return WriteResult.Failed(context.getString(R.string.export_debug_logs_empty))
        }

        val dir = File(context.cacheDir, "debug-logs").apply { mkdirs() }
        val timestamp =
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "jotty-debug-$timestamp.txt")

        return try {
            val header = buildHeader(instance)
            file.writeText(header + "\n\n--- Log ---\n\n" + logs)
            WriteResult.Ok(file)
        } catch (e: Exception) {
            AppLog.e("DebugLogExporter", "Write failed", e)
            val fallback = context.getString(R.string.export_debug_logs_failed)
            WriteResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: fallback)
        }
    }

    /**
     * Writes the report into the public Downloads folder (Android 10+). Older releases need
     * [NeedsPicker] and a document-create intent from the UI.
     */
    fun saveToDownloads(
        context: Context,
        file: File,
    ): SaveResult {
        val displayName = file.name
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return SaveResult.NeedsPicker(file, displayName)
        }
        return try {
            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            val resolver = context.contentResolver
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return SaveResult.Failed(context.getString(R.string.export_debug_logs_save_failed))
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return SaveResult.Failed(context.getString(R.string.export_debug_logs_save_failed))
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            SaveResult.Saved(displayName)
        } catch (e: Exception) {
            AppLog.e("DebugLogExporter", "Save to Downloads failed", e)
            SaveResult.Failed(context.getString(R.string.export_debug_logs_save_failed))
        }
    }

    fun copyToUri(
        context: Context,
        file: File,
        destination: android.net.Uri,
    ): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(destination)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("no stream")
        }.onFailure { e ->
            AppLog.e("DebugLogExporter", "Copy to uri failed", e)
        }.isSuccess

    fun shareReport(
        context: Context,
        file: File,
    ): ShareResult {
        return try {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    // Generic file share — avoids “Can't save text” from link-only save targets.
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Jotty Android debug logs")
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val chooserTitle = context.getString(R.string.export_debug_logs)
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            ShareResult.Started
        } catch (e: Exception) {
            AppLog.e("DebugLogExporter", "Share failed", e)
            val fallback = context.getString(R.string.export_debug_logs_failed)
            ShareResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: fallback)
        }
    }

    private fun buildHeader(instance: JottyInstance?): String =
        buildString {
            appendLine("Jotty Android debug log")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            if (instance != null) {
                appendLine("Instance: ${instance.name}")
                appendLine("Server: ${instance.serverUrl}")
            } else {
                appendLine("Instance: (none connected)")
            }
        }
}
