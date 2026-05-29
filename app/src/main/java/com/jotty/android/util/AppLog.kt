package com.jotty.android.util

import android.util.Log
import com.jotty.android.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Tagged logging for filtering by feature (e.g. "notes", "encryption", "checklists").
 * All levels are captured in an in-memory ring buffer for export via [DebugLogExporter].
 * [d] also mirrors to logcat in debug builds.
 */
object AppLog {
    private const val PREFIX = "Jotty"
    private const val MAX_BUFFER_LINES = 1000

    private val buffer = ConcurrentLinkedDeque<String>()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var crashHandlerInstalled = false

    /** Records uncaught exceptions into the export buffer (call once from [android.app.Application.onCreate]). */
    fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("crash", "Uncaught on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun snapshot(): String = buffer.joinToString(separator = "\n")

    fun clear() {
        buffer.clear()
    }

    fun d(
        tag: String,
        message: String,
    ) {
        append("D", tag, message)
        if (BuildConfig.DEBUG) {
            Log.d("$PREFIX/$tag", message)
        }
    }

    fun i(
        tag: String,
        message: String,
    ) {
        append("I", tag, message)
        Log.i("$PREFIX/$tag", message)
    }

    fun w(
        tag: String,
        message: String,
    ) {
        append("W", tag, message)
        Log.w("$PREFIX/$tag", message)
    }

    fun e(
        tag: String,
        message: String,
    ) {
        append("E", tag, message)
        Log.e("$PREFIX/$tag", message)
    }

    fun e(
        tag: String,
        message: String,
        t: Throwable,
    ) {
        append("E", tag, "$message\n${Log.getStackTraceString(t)}")
        Log.e("$PREFIX/$tag", message, t)
    }

    private fun append(
        level: String,
        tag: String,
        message: String,
    ) {
        val line = "${timestampFormat.format(Date())} $level/$PREFIX/$tag: $message"
        buffer.addLast(line)
        while (buffer.size > MAX_BUFFER_LINES) {
            buffer.pollFirst()
        }
    }
}
