package com.jotty.android.util

enum class ServerUrlScheme(val prefix: String) {
    HTTPS("https://"),
    HTTP("http://"),
    ;

    companion object {
        fun fromPrefix(prefix: String): ServerUrlScheme? =
            entries.firstOrNull { it.prefix.equals(prefix, ignoreCase = true) }
    }
}

data class ServerUrlParts(
    val scheme: ServerUrlScheme = ServerUrlScheme.HTTPS,
    val hostAndPath: String = "",
)

private val HTTPS_PREFIX = Regex("^https://", RegexOption.IGNORE_CASE)
private val HTTP_PREFIX = Regex("^http://", RegexOption.IGNORE_CASE)

/** Splits a stored server URL into scheme and host/path (without scheme). */
fun parseServerUrl(input: String): ServerUrlParts {
    val trimmed = input.trim()
    return when {
        HTTPS_PREFIX.containsMatchIn(trimmed) ->
            ServerUrlParts(
                scheme = ServerUrlScheme.HTTPS,
                hostAndPath = trimmed.replaceFirst(HTTPS_PREFIX, ""),
            )
        HTTP_PREFIX.containsMatchIn(trimmed) ->
            ServerUrlParts(
                scheme = ServerUrlScheme.HTTP,
                hostAndPath = trimmed.replaceFirst(HTTP_PREFIX, ""),
            )
        else -> ServerUrlParts(hostAndPath = trimmed)
    }
}

/**
 * When the user types into the host field, detect a leading http:// or https://,
 * move it to the scheme selector, and return the remainder.
 * [scheme] is null when the typed value did not include a scheme prefix.
 */
fun extractSchemeFromTypedHost(
    input: String,
): Pair<ServerUrlScheme?, String> {
    val trimmed = input.trimStart()
    return when {
        HTTPS_PREFIX.containsMatchIn(trimmed) ->
            ServerUrlScheme.HTTPS to trimmed.replaceFirst(HTTPS_PREFIX, "")
        HTTP_PREFIX.containsMatchIn(trimmed) ->
            ServerUrlScheme.HTTP to trimmed.replaceFirst(HTTP_PREFIX, "")
        else -> null to input
    }
}

fun combineServerUrl(
    scheme: ServerUrlScheme,
    hostAndPath: String,
): String {
    val host = hostAndPath.trim()
    return if (host.isBlank()) "" else scheme.prefix + host
}

fun browserUrlFromServerUrl(serverUrl: String): String {
    val parts = parseServerUrl(serverUrl)
    return combineServerUrl(parts.scheme, parts.hostAndPath).trim().trimEnd('/')
}
