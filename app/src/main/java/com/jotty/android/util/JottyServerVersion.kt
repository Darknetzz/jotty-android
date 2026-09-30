package com.jotty.android.util

/**
 * Parses Jotty [api/health] `version` strings (e.g. `1.28.0`, `v1.27.1-beta`) into major.minor
 * for compatibility checks. Returns null when the string cannot be parsed.
 */
data class JottyServerVersion(
    val major: Int,
    val minor: Int,
) : Comparable<JottyServerVersion> {
    override fun compareTo(other: JottyServerVersion): Int =
        compareValuesBy(this, other, JottyServerVersion::major, JottyServerVersion::minor)

    companion object {
        /** Highest Jotty server major.minor this app release has been checked against. */
        val COMPAT_CEILING = JottyServerVersion(1, 28)

        private val VERSION_PREFIX =
            Regex("""^v?(\d+)\.(\d+)(?:\.|\D|$)""", RegexOption.IGNORE_CASE)

        fun parse(raw: String?): JottyServerVersion? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val match = VERSION_PREFIX.find(text) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            return JottyServerVersion(major, minor)
        }

        /** True when [raw] is a known version newer than [COMPAT_CEILING]. */
        fun isNewerThanCompatCeiling(raw: String?): Boolean {
            val parsed = parse(raw) ?: return false
            return parsed > COMPAT_CEILING
        }
    }
}
