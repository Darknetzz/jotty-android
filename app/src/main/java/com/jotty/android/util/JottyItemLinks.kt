package com.jotty.android.util

/**
 * In-app link targets for Jotty note markdown (`/note/{uuid}`, `/checklist/{uuid}`,
 * legacy `/jotty/{uuid}`, and resolved `[[wikilinks]]`).
 */
data class JottyItemRef(
    val type: Type,
    val id: String,
) {
    enum class Type {
        NOTE,
        CHECKLIST,
    }
}

const val JOTTY_ITEM_SCHEME = "jotty-item"

fun jottyItemUri(ref: JottyItemRef): String =
    when (ref.type) {
        JottyItemRef.Type.NOTE -> "$JOTTY_ITEM_SCHEME://note/${ref.id}"
        JottyItemRef.Type.CHECKLIST -> "$JOTTY_ITEM_SCHEME://checklist/${ref.id}"
    }

fun parseJottyItemUri(url: String): JottyItemRef? {
    val trimmed = url.trim()
    val prefix = "$JOTTY_ITEM_SCHEME://"
    if (!trimmed.regionMatches(0, prefix, 0, prefix.length, ignoreCase = true)) return null
    val path = trimmed.substring(prefix.length)
    val slash = path.indexOf('/')
    if (slash <= 0 || slash == path.lastIndex) return null
    val typePart = path.substring(0, slash)
    val id = path.substring(slash + 1).substringBefore('?').substringBefore('#').trim()
    if (id.isBlank()) return null
    return when (typePart.lowercase()) {
        "note" -> JottyItemRef(JottyItemRef.Type.NOTE, id)
        "checklist" -> JottyItemRef(JottyItemRef.Type.CHECKLIST, id)
        else -> null
    }
}

/** Builds a case-insensitive title → item map from relations links and unique local note titles. */
fun buildWikilinkTitleMap(
    relationLinks: List<Pair<String, JottyItemRef>>,
    localNotes: List<Pair<String, String>>,
): Map<String, JottyItemRef> {
    val map = linkedMapOf<String, JottyItemRef>()
    for ((title, ref) in relationLinks) {
        val key = title.trim()
        if (key.isNotEmpty()) map.putIfAbsent(key.lowercase(), ref)
    }
    val byTitle = localNotes.groupBy { it.first.trim().lowercase() }.filterKeys { it.isNotEmpty() }
    for ((key, group) in byTitle) {
        if (map.containsKey(key)) continue
        val uniqueIds = group.map { it.second }.distinct()
        if (uniqueIds.size == 1) {
            map[key] = JottyItemRef(JottyItemRef.Type.NOTE, uniqueIds.single())
        }
    }
    return map
}

private val explicitItemPathRegex =
    Regex("""\]\((/(?:note|checklist|jotty)/([^/?#)]+))\)""", RegexOption.IGNORE_CASE)

private val wikilinkRegex =
    Regex("""\[\[([^\[\]|#^\n]+)(?:[#^][^\[\]|\n]*)?(?:\|([^\[\]\n]*))?]]""")

private val fencedCodeRegex =
    Regex("""^```.*?^```""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))

private val inlineCodeRegex = Regex("""`[^`\n]+`""")

/**
 * Rewrites Jotty item paths and resolved wikilinks into [jottyItemUri] markdown links for display.
 * Skips fenced and inline code. Does not mutate saved note content — call only on display strings.
 *
 * Unresolved wikilinks become plain text (alias or title).
 */
fun rewriteJottyItemLinksForDisplay(
    content: String,
    titleToItem: Map<String, JottyItemRef>,
): String {
    if (content.isBlank()) return content
    return transformOutsideCode(content) { segment ->
        rewriteWikilinks(rewriteExplicitPaths(segment), titleToItem)
    }
}

private fun rewriteExplicitPaths(segment: String): String =
    explicitItemPathRegex.replace(segment) { match ->
        val path = match.groupValues[1]
        val id = match.groupValues[2].trim()
        if (id.isBlank()) return@replace match.value
        val type =
            when {
                path.startsWith("/checklist/", ignoreCase = true) -> JottyItemRef.Type.CHECKLIST
                else -> JottyItemRef.Type.NOTE // /note/ and legacy /jotty/
            }
        "](${jottyItemUri(JottyItemRef(type, id))})"
    }

private fun rewriteWikilinks(
    segment: String,
    titleToItem: Map<String, JottyItemRef>,
): String =
    wikilinkRegex.replace(segment) { match ->
        val title = match.groupValues[1].trim()
        val alias = match.groupValues.getOrNull(2)?.trim().orEmpty()
        val display = alias.ifBlank { title }
        val ref = titleToItem[title.lowercase()]
        if (ref != null) {
            "[$display](${jottyItemUri(ref)})"
        } else {
            display
        }
    }

/**
 * Applies [transform] to markdown outside fenced ``` blocks and inline `code`.
 */
internal fun transformOutsideCode(
    content: String,
    transform: (String) -> String,
): String {
    if (content.isEmpty()) return content
    val protected = mutableListOf<String>()
    var working =
        fencedCodeRegex.replace(content) { match ->
            val token = "\u0000FENCE${protected.size}\u0000"
            protected += match.value
            token
        }
    working =
        inlineCodeRegex.replace(working) { match ->
            val token = "\u0000INLINE${protected.size}\u0000"
            protected += match.value
            token
        }
    val transformed = transform(working)
    var restored = transformed
    protected.forEachIndexed { index, original ->
        restored =
            restored
                .replace("\u0000FENCE$index\u0000", original)
                .replace("\u0000INLINE$index\u0000", original)
    }
    return restored
}
