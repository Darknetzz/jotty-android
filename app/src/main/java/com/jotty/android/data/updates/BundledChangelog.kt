package com.jotty.android.data.updates

import android.content.Context

/**
 * Parses [CHANGELOG.md] bundled in assets (copied from the repo root at build time).
 */
class BundledChangelog private constructor(
    private val sections: Map<String, String>,
    private val devSectionKeys: List<String>,
) {
    fun section(key: String): String? = sections[key]

    /** First `[x.y.z-dev]` section in file order (rolling dev-latest notes). */
    fun firstDevSectionBody(): String? = devSectionKeys.firstOrNull()?.let { sections[it] }

    companion object {
        private const val ASSET_NAME = "CHANGELOG.md"
        private val SECTION_HEADER = Regex("""(?m)^## \[([^\]]+)\].*$""")

        fun load(context: Context): BundledChangelog? =
            runCatching {
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            }.getOrNull()?.let { parse(it) }

        internal fun parse(markdown: String): BundledChangelog {
            val matches = SECTION_HEADER.findAll(markdown).toList()
            if (matches.isEmpty()) {
                return BundledChangelog(emptyMap(), emptyList())
            }
            val sections = linkedMapOf<String, String>()
            val devKeys = mutableListOf<String>()
            matches.forEachIndexed { index, match ->
                val key = match.groupValues[1].trim()
                val bodyStart = match.range.last + 1
                val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: markdown.length
                val body = markdown.substring(bodyStart, bodyEnd).trim()
                if (body.isNotEmpty()) {
                    sections[key] = body
                }
                if (key.endsWith("-dev", ignoreCase = false)) {
                    devKeys.add(key)
                }
            }
            return BundledChangelog(sections, devKeys)
        }

        /** Section key for the installed build (e.g. `1.3.6-dev` or `1.3.6`). */
        fun sectionKeyForInstalled(versionName: String): String {
            val trimmed = versionName.trim()
            val devPlus = trimmed.indexOf("-dev+")
            if (devPlus > 0) {
                return trimmed.substring(0, devPlus) + "-dev"
            }
            return UpdateChecker.baseVersionNameWithoutDevSuffix(trimmed)
        }

        /** Section key for a stable remote version, or `null` to use the rolling dev section. */
        fun sectionKeyForRemote(
            remoteVersionName: String,
            channel: UpdateChannel,
        ): String? =
            when (channel) {
                UpdateChannel.Dev -> null
                UpdateChannel.Stable -> remoteVersionName.trim().removePrefix("v")
            }

        fun resolveMarkdown(
            changelog: BundledChangelog?,
            sectionKey: String?,
            useDevRollingSection: Boolean,
            fallbackMarkdown: String?,
        ): String? {
            val bundled =
                when {
                    useDevRollingSection -> changelog?.firstDevSectionBody()
                    sectionKey != null -> changelog?.section(sectionKey)
                    else -> null
                }
            return bundled?.takeIf { it.isNotBlank() }
                ?: fallbackMarkdown?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
