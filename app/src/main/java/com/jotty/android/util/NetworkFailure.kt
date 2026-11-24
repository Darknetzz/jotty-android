package com.jotty.android.util

import kotlinx.coroutines.CancellationException
import java.io.IOException

private const val MAX_CAUSE_DEPTH = 8

/**
 * True when the request never got an answer from the server (DNS failure, timeout, refused
 * connection, TLS failure, …), as opposed to the server answering with an error.
 *
 * Retrofit's `HttpException` is not an [IOException], so 4xx/5xx responses return false.
 * Offline repositories use this to queue the change locally instead of failing the save.
 */
fun Throwable.isServerUnreachable(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (current is CancellationException) return false
        if (current is IOException) return true
        val cause = current.cause
        current = if (cause === current) null else cause
        depth++
    }
    return false
}
