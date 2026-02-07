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

/** XChaCha20 encrypted body in Jotty format (JSON with alg, salt, nonce, data). Matches alg values like "xchacha20" or "XChaCha20-Poly1305". */
private val XCHACHA_JSON_CONTAINS = Regex(""""alg"\s*:\s*"[^"]*xchacha[^"]*"""", RegexOption.IGNORE_CASE)

/**
 * Parses note content and detects if it is encrypted (Jotty format: YAML frontmatter + body,
 * or body-only when API returns just the encrypted payload).
 */
object NoteEncryption {

    private const val FRONTMATTER_START = "---"
    private const val FRONTMATTER_END = "---"
    /** encrypted: true | "true" | yes | 1 (YAML-style) */
    private val ENCRYPTED_REGEX = Regex("""encrypted\s*:\s*(true|"true"|yes|1)\b""", RegexOption.IGNORE_CASE)
    private val METHOD_REGEX = Regex("""encryptionMethod\s*:\s*["']?(\w+)["']?""", RegexOption.IGNORE_CASE)
    private val TITLE_REGEX = Regex("""title\s*:\s*(.+)""")
    private val UUID_REGEX = Regex("""uuid\s*:\s*(\S+)""")

    fun parse(content: String): ParsedNoteContent {
        val trimmed = content.trim().trimStart('\uFEFF') // BOM
        if (trimmed.isEmpty()) return ParsedNoteContent.Plain(content)

        // 1) Frontmatter format: --- ... encrypted: true ... --- \n body
        if (trimmed.startsWith(FRONTMATTER_START)) {
            val afterFirst = trimmed.substring(FRONTMATTER_START.length)
            val endOfFront = afterFirst.indexOf(FRONTMATTER_END)
            if (endOfFront != -1) {
                val frontmatterStr = afterFirst.substring(0, endOfFront).trim().replace("\r\n", "\n").replace("\r", "\n")
                val body = afterFirst.substring(endOfFront + FRONTMATTER_END.length).trim()
                if (ENCRYPTED_REGEX.containsMatchIn(frontmatterStr)) {
                    val method = METHOD_REGEX.find(frontmatterStr)?.groupValues?.get(1)?.trim()?.lowercase() ?: "xchacha"
                    val title = TITLE_REGEX.find(frontmatterStr)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
                    val uuid = UUID_REGEX.find(frontmatterStr)?.groupValues?.get(1)?.trim()
                    return ParsedNoteContent.Encrypted(
                        frontmatter = NoteFrontmatter(uuid = uuid, title = title, encrypted = true, encryptionMethod = method),
                        encryptionMethod = method,
                        encryptedBody = body,
                    )
                }
            }
        }

        // 2) Body-only: API sometimes returns just the encrypted JSON (no frontmatter)
        val looksLikeEncryptedJson = trimmed.startsWith("{") && trimmed.contains("\"data\"") &&
            (XCHACHA_JSON_CONTAINS.containsMatchIn(trimmed) || (trimmed.contains("\"salt\"") && trimmed.contains("\"nonce\"")))
        if (looksLikeEncryptedJson) {
            return ParsedNoteContent.Encrypted(
                frontmatter = NoteFrontmatter(encrypted = true, encryptionMethod = "xchacha"),
                encryptionMethod = "xchacha",
                encryptedBody = trimmed,
            )
        }

        return ParsedNoteContent.Plain(content)
    }

    /**
     * Returns true if [content] is an encrypted note (frontmatter or encrypted JSON body).
     */
    fun isEncrypted(content: String): Boolean =
        parse(content) is ParsedNoteContent.Encrypted
}
