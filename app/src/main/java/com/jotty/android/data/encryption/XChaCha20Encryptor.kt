package com.jotty.android.data.encryption

import com.jotty.android.util.AppLog
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.SecureRandom
import java.util.Base64

/**
 * Encrypts plaintext with XChaCha20-Poly1305 for Jotty.
 * Output format: JSON with "alg", optional Argon2 "t"/"m"/"p", "salt", "nonce", "data" (base64).
 * [t]/[m]/[p] are always written so decryptors (app and Jotty web) use the same Argon2 cost as this build.
 * Uses libsodium secretbox format (tag then ciphertext) so notes encrypted here decrypt in the Jotty web app.
 */
object XChaCha20Encryptor {
    private data class Argon2Dims(
        val iterations: Int,
        val memoryKb: Int,
        val parallelism: Int,
    )

    /** Match libsodium INTERACTIVE-style default when 64 MiB is available. */
    private val ARGON_PRIMARY = Argon2Dims(iterations = 2, memoryKb = 65536, parallelism = 1)

    /** Fallback when 64 MiB Argon2 cannot run (low RAM devices); still in [XChaCha20Decryptor] preset list. */
    private val ARGON_FALLBACK = Argon2Dims(iterations = 2, memoryKb = 32768, parallelism = 1)

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
            encryptAttempt(plaintext, trimmed, ARGON_PRIMARY)
                ?: run {
                    AppLog.d("encryption", "Encrypt: retrying with Argon2 memoryKb=${ARGON_FALLBACK.memoryKb}")
                    encryptAttempt(plaintext, trimmed, ARGON_FALLBACK)
                }
        } catch (_: OutOfMemoryError) {
            AppLog.d("encryption", "Encrypt: OutOfMemoryError — retrying with Argon2 memoryKb=${ARGON_FALLBACK.memoryKb}")
            encryptAttempt(plaintext, trimmed, ARGON_FALLBACK)
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
     */
    fun wrapWithFrontmatter(
        noteId: String,
        noteTitle: String,
        category: String,
        encryptedBodyJson: String,
    ): String {
        return """
            |---
            |uuid: $noteId
            |title: $noteTitle
            |encrypted: true
            |encryptionMethod: xchacha
            |---
            |$encryptedBodyJson
            """.trimMargin()
    }

    private fun encryptAttempt(
        plaintext: String,
        passphrase: CharArray,
        dims: Argon2Dims,
    ): String? {
        return try {
            val salt = ByteArray(SALT_BYTES).apply { random.nextBytes(this) }
            val nonce24 = ByteArray(NONCE_BYTES).apply { random.nextBytes(this) }
            val key = deriveKey(passphrase, salt, dims) ?: return null
            val ciphertextAndTag =
                encryptXChaCha20Poly1305(key, nonce24, plaintext.toByteArray(Charsets.UTF_8)) ?: return null
            val saltB64 = Base64.getEncoder().encodeToString(salt)
            val nonceB64 = Base64.getEncoder().encodeToString(nonce24)
            val dataB64 = Base64.getEncoder().encodeToString(ciphertextAndTag)
            """{"alg":"xchacha20","t":${dims.iterations},"m":${dims.memoryKb},"p":${dims.parallelism},"salt":"$saltB64","nonce":"$nonceB64","data":"$dataB64"}"""
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
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts with XChaCha20-Poly1305. Returns payload in libsodium secretbox order:
     * tag (16 bytes) then ciphertext, so the Jotty web app can decrypt.
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
            // BC outputs ciphertext||tag (IETF order); libsodium secretbox expects tag||ciphertext.
            val ciphertextLen = outLen - TAG_BYTES
            ByteArray(outLen).apply {
                System.arraycopy(outBuf, ciphertextLen, this, 0, TAG_BYTES)
                System.arraycopy(outBuf, 0, this, TAG_BYTES, ciphertextLen)
            }
        } catch (_: Exception) {
            null
        }
    }
}
