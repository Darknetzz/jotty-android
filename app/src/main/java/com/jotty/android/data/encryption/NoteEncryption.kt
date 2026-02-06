package com.jotty.android.data.encryption

/**
 * Result of parsing note content. Jotty encrypted notes use YAML frontmatter
 * (see howto/ENCRYPTION.md): `encrypted: true`, `encryptionMethod: xchacha` or `pgp`.
 */
sealed class ParsedNoteContent {
    data class Plain(val content: String) : ParsedNoteContent()
    data class Encrypted(
        val frontmatter: NoteFrontmatter,
        val encryptionMethod: String,
        val encryptedBody: String,
    ) : ParsedNoteContent()
}

data class NoteFrontmatter(
    val uuid: String? = null,
    val title: String? = null,
    val encrypted: Boolean = false,
    val encryptionMethod: String? = null,
)

/**
 * Parses note content and detects if it is encrypted (Jotty format: YAML frontmatter + body).
 */
object NoteEncryption {

    private const val FRONTMATTER_START = "---"
    private const val FRONTMATTER_END = "---"
    private val ENCRYPTED_REGEX = Regex("""encrypted:\s*true""", RegexOption.IGNORE_CASE)
    private val METHOD_REGEX = Regex("""encryptionMethod:\s*(\S+)""")
    private val TITLE_REGEX = Regex("""title:\s*(.+)""")
    private val UUID_REGEX = Regex("""uuid:\s*(\S+)""")

    fun parse(content: String): ParsedNoteContent {
        val trimmed = content.trim()
        if (!trimmed.startsWith(FRONTMATTER_START)) {
            return ParsedNoteContent.Plain(content)
        }
        val endOfFront = trimmed.indexOf(FRONTMATTER_END, FRONTMATTER_START.length)
        if (endOfFront == -1) return ParsedNoteContent.Plain(content)
        val frontmatterStr = trimmed.substring(FRONTMATTER_START.length, endOfFront).trim()
        val body = trimmed.substring(endOfFront + FRONTMATTER_END.length).trimStart()
        if (!ENCRYPTED_REGEX.containsMatchIn(frontmatterStr)) {
            return ParsedNoteContent.Plain(content)
        }
        val method = METHOD_REGEX.find(frontmatterStr)?.groupValues?.get(1)?.trim() ?: "xchacha"
        val title = TITLE_REGEX.find(frontmatterStr)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
        val uuid = UUID_REGEX.find(frontmatterStr)?.groupValues?.get(1)?.trim()
        val frontmatter = NoteFrontmatter(
            uuid = uuid,
            title = title,
            encrypted = true,
            encryptionMethod = method,
        )
        return ParsedNoteContent.Encrypted(
            frontmatter = frontmatter,
            encryptionMethod = method.lowercase(),
            encryptedBody = body,
        )
    }

    /**
     * Returns true if [content] is an encrypted note (has frontmatter with encrypted: true).
     */
    fun isEncrypted(content: String): Boolean =
        parse(content) is ParsedNoteContent.Encrypted
}
