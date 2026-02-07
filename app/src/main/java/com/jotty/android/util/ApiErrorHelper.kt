package com.jotty.android.util

import android.content.Context
import com.jotty.android.R
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps exceptions to short, user-friendly messages suitable for UI (Snackbar, error state).
 * Uses string resources for i18n consistency.
 */
object ApiErrorHelper {

    fun userMessage(context: Context, t: Throwable): String =
        context.getString(errorMessageResId(t))

    /** Visible for testing: returns the string resource ID for the given throwable. */
    internal fun errorMessageResId(t: Throwable): Int = when (t) {
        is UnknownHostException -> R.string.no_internet_connection
        is SocketTimeoutException -> R.string.connection_timed_out
        is IOException -> R.string.network_error
        is HttpException -> when (t.code()) {
            in 500..599 -> R.string.server_error
            else -> R.string.request_failed
        }
        else -> R.string.unknown_error
    }
}
