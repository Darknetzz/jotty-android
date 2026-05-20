package com.jotty.android.data.encryption

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

/**
 * Best-effort helpers for passphrase material. JVM/Android may retain copies of strings and
 * char arrays in memory beyond our control; callers should still clear sensitive buffers when done.
 */
internal fun CharArray.copyTrimmedOrNull(): CharArray? {
    var start = 0
    var end = size
    while (start < end && this[start].isWhitespace()) start++
    while (start < end && this[end - 1].isWhitespace()) end--
    if (start >= end) return null
    return copyOfRange(start, end)
}

internal fun CharArray.clearPassphrase() {
    fill('\u0000')
}

/** UTF-8 bytes for Argon2 / AES payloads without going through [String] when possible. */
internal fun utf8Encode(chars: CharArray): ByteArray {
    val bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
    val n = bb.remaining()
    val out = ByteArray(n)
    bb.get(out)
    return out
}
