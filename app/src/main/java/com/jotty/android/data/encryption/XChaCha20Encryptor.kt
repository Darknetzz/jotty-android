package com.jotty.android.data.encryption

import com.jotty.android.util.AppLog
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.SecureRandom

private fun encodeHex(bytes: ByteArray): String {
    val sb = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        sb.append(String.format("%02x", b.toInt() and 0xff))
    }
    return sb.toString()
}

/**
 * Encrypts plaintext with XChaCha20-Poly1305 for Jotty.
 * Output format: JSON with "alg", Argon2 "t"/"m"/"p", "salt", "nonce", "data" (hex).
 * [t]/[m]/[p] are always written so decryptors (app and Jotty web) use the same Argon2 cost as this build.
 * Uses AEAD combined format (ciphertext then tag) used by Jotty web's libsodium implementation.
 */
object XChaCha20Encryptor {
    internal data class Argon2Dims(
        val iterations: Int,
        val memoryKb: Int,
        val parallelism: Int,
    )

    /** Match libsodium INTERACTIVE-style default when 64 MiB is available. */
    internal val ARGON_PRIMARY = Argon2Dims(iterations = 2, memoryKb = 65536, parallelism = 1)

    /** Fallback when 64 MiB Argon2 cannot run (low RAM devices); still in [XChaCha20Decryptor] preset list. */
    internal val ARGON_FALLBACK = Argon2Dims(iterations = 2, memoryKb = 32768, parallelism = 1)
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 24
    private const val TAG_BYTES = 16

    private val random = SecureRandom()

    /**
     * Encrypts [plaintext] with [passphrase]. Returns JSON body (no frontmatter).
     * Passphrase is trimmed so it matches decryption behavior.
     */
    fun encrypt(
        plaintext: String,
        passphrase: CharArray,
    ): String? {
        val trimmed = passphrase.copyTrimmedOrNull() ?: return null
        return try {
            val primary = encryptAttempt(plaintext, trimmed, ARGON_PRIMARY)
            if (primary != null) {
                primary
            } else {
                AppLog.d("encryption", "Encrypt: retrying with Argon2 memoryKb=${ARGON_FALLBACK.memoryKb}")
                val fallback = encryptAttempt(plaintext, trimmed, ARGON_FALLBACK)
                if (fallback == null) {
                    AppLog.d("encryption", "Encrypt: failed with primary (64 MiB) and fallback (32 MiB) Argon2 presets")
                }
                fallback
            }
        } catch (_: OutOfMemoryError) {
            AppLog.d("encryption", "Encrypt: OutOfMemoryError — retrying with Argon2 memoryKb=${ARGON_FALLBACK.memoryKb}")
            encryptAttempt(plaintext, trimmed, ARGON_FALLBACK)
                ?: run {
                    AppLog.d("encryption", "Encrypt: failed after OutOfMemoryError and fallback (32 MiB) Argon2")
                    null
                }
        } finally {
            trimmed.clearPassphrase()
        }
    }

    /** Convenience for tests and callers that only have a [String]; clears a temporary [CharArray]. */
    fun encrypt(
        plaintext: String,
        passphrase: String,
    ): String? {
        val buf = passphrase.toCharArray()
        return try {
            encrypt(plaintext, buf)
        } finally {
            buf.clearPassphrase()
        }
    }

    /**
     * Builds full note content with YAML frontmatter for Jotty (encrypted: true, encryptionMethod: xchacha).
     * Title and uuid are quoted when needed so values containing `---` or colons do not break parsing.
     */
    fun wrapWithFrontmatter(
        noteId: String,
        noteTitle: String,
        category: String,
        encryptedBodyJson: String,
    ): String {
        val safeUuid = yamlScalar(noteId)
        val safeTitle = yamlScalar(noteTitle)
        return """
            |---
            |uuid: $safeUuid
            |title: $safeTitle
            |encrypted: true
            |encryptionMethod: xchacha
            |---
            |$encryptedBodyJson
            """.trimMargin()
    }

    /** YAML scalar for frontmatter; double-quoted when special characters would confuse [NoteEncryption.parse]. */
    internal fun yamlScalar(value: String): String {
        if (value.isEmpty()) return "\"\""
        val needsQuotes =
            value.any { it == '\n' || it == '\r' || it == ':' || it == '#' } ||
                value.contains("---") ||
                value != value.trim() ||
                value.any { it == '"' || it == '\\' }
        return if (needsQuotes) {
            "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            value
        }
    }

    /**
     * Encrypts with explicit Argon2 cost (for tests and diagnostics).
     * Production callers should use [encrypt].
     */
    internal fun encryptWithArgonDims(
        plaintext: String,
        passphrase: CharArray,
        dims: Argon2Dims,
    ): String? {
        val trimmed = passphrase.copyTrimmedOrNull() ?: return null
        return try {
            encryptAttempt(plaintext, trimmed, dims)
        } finally {
            trimmed.clearPassphrase()
        }
    }

    internal fun encryptAttempt(
        plaintext: String,
        passphrase: CharArray,
        dims: Argon2Dims,
    ): String? {
        return try {
            val salt = ByteArray(SALT_BYTES).apply { random.nextBytes(this) }
            val nonce24 = ByteArray(NONCE_BYTES).apply { random.nextBytes(this) }
            val key = deriveKey(passphrase, salt, dims) ?: return null
            try {
                val ciphertextAndTag =
                    encryptXChaCha20Poly1305(key, nonce24, plaintext.toByteArray(Charsets.UTF_8)) ?: return null
                val saltHex = encodeHex(salt)
                val nonceHex = encodeHex(nonce24)
                val dataHex = encodeHex(ciphertextAndTag)
                """{"alg":"xchacha20","t":${dims.iterations},"m":${dims.memoryKb},"p":${dims.parallelism},"salt":"$saltHex","nonce":"$nonceHex","data":"$dataHex"}"""
            } finally {
                key.fill(0)
            }
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        dims: Argon2Dims,
    ): ByteArray? {
        return try {
            val params =
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(dims.iterations)
                    .withMemoryAsKB(dims.memoryKb)
                    .withParallelism(dims.parallelism)
                    .withSalt(salt)
                    .build()
            val generator = Argon2BytesGenerator()
            generator.init(params)
            val key = ByteArray(KEY_BYTES)
            val pwBytes = utf8Encode(passphrase)
            try {
                generator.generateBytes(pwBytes, key, 0, key.size)
            } finally {
                pwBytes.fill(0)
            }
            key
        } catch (e: OutOfMemoryError) {
            AppLog.d("encryption", "Encrypt: Argon2 key derivation OOM (memoryKb=${dims.memoryKb})")
            throw e
        } catch (e: Exception) {
            AppLog.d("encryption", "Encrypt: Argon2 key derivation failed (memoryKb=${dims.memoryKb}): ${e.message}")
            null
        }
    }

    /**
     * Encrypts with XChaCha20-Poly1305. Returns payload in AEAD combined order:
     * ciphertext then tag, which is what Jotty web expects.
     */
    private fun encryptXChaCha20Poly1305(
        key: ByteArray,
        nonce24: ByteArray,
        plaintext: ByteArray,
    ): ByteArray? {
        if (nonce24.size != NONCE_BYTES || key.size != KEY_BYTES) return null
        val subkey = XChaCha20Decryptor.hChaCha20Block(key, nonce24.copyOfRange(0, 16)) ?: return null
        // Match libsodium: npub2[0..3]=0, npub2[4..11]=nonce24[16..23]
        val nonce12 = ByteArray(12).apply { System.arraycopy(nonce24, 16, this, 4, 8) }
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(true, ParametersWithIV(KeyParameter(subkey), nonce12))
            // Use getOutputSize so the buffer is correct whether BC flushes in processBytes
            // or buffers everything and flushes in doFinal (both are valid implementations).
            val outBuf = ByteArray(cipher.getOutputSize(plaintext.size))
            var outLen = cipher.processBytes(plaintext, 0, plaintext.size, outBuf, 0)
            outLen += cipher.doFinal(outBuf, outLen)
            // BC outputs ciphertext||tag (AEAD combined / IETF order), which Jotty web expects.
            outBuf.copyOf(outLen)
        } catch (_: Exception) {
            null
        }
    }
}
