package com.jotty.android.util

import android.util.Log

/**
 * Tagged logging for filtering by feature (e.g. "notes", "encryption", "checklists").
 * Use log level and tag when debugging.
 */
object AppLog {
    private const val PREFIX = "Jotty"

    fun d(tag: String, message: String) = Log.d("$PREFIX/$tag", message)
    fun i(tag: String, message: String) = Log.i("$PREFIX/$tag", message)
    fun w(tag: String, message: String) = Log.w("$PREFIX/$tag", message)
    fun e(tag: String, message: String) = Log.e("$PREFIX/$tag", message)
    fun e(tag: String, message: String, t: Throwable) = Log.e("$PREFIX/$tag", message, t)
}
