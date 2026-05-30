package com.jotty.android.util

import android.content.Context
import android.content.Intent
import android.os.Build
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
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Jotty Android debug logs")
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
