package com.jotty.android.data.encryption

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.SecureRandom
import java.util.Base64

/**
 * Encrypts plaintext with XChaCha20-Poly1305 for Jotty.
 * Output format: JSON with "alg","salt","nonce","data" (base64). Same params as XChaCha20Decryptor.
 * Uses libsodium secretbox format (tag then ciphertext) so notes encrypted here decrypt in the Jotty web app.
 */
object XChaCha20Encryptor {

    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_MEMORY_KB = 65536
    private const val ARGON2_PARALLELISM = 1
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 24
    private const val TAG_BYTES = 16

    private val random = SecureRandom()

    /**
     * Encrypts [plaintext] with [passphrase]. Returns JSON body (no frontmatter).
     * Passphrase is trimmed so it matches decryption behavior.
     */
    fun encrypt(plaintext: String, passphrase: String): String? {
        val trimmed = passphrase.trim()
        if (trimmed.isEmpty()) return null
        val salt = ByteArray(SALT_BYTES).apply { random.nextBytes(this) }
        val nonce24 = ByteArray(NONCE_BYTES).apply { random.nextBytes(this) }
        val key = deriveKey(trimmed, salt) ?: return null
        val ciphertextAndTag = encryptXChaCha20Poly1305(key, nonce24, plaintext.toByteArray(Charsets.UTF_8)) ?: return null
        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val nonceB64 = Base64.getEncoder().encodeToString(nonce24)
        val dataB64 = Base64.getEncoder().encodeToString(ciphertextAndTag)
        return """{"alg":"xchacha20","salt":"$saltB64","nonce":"$nonceB64","data":"$dataB64"}"""
    }

    /**
     * Builds full note content with YAML frontmatter for Jotty (encrypted: true, encryptionMethod: xchacha).
     */
    fun wrapWithFrontmatter(noteId: String, noteTitle: String, category: String, encryptedBodyJson: String): String {
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

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray? {
        return try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ARGON2_ITERATIONS)
                .withMemoryAsKB(ARGON2_MEMORY_KB)
                .withParallelism(ARGON2_PARALLELISM)
                .withSalt(salt)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(params)
            val key = ByteArray(KEY_BYTES)
            generator.generateBytes(passphrase.toByteArray(Charsets.UTF_8), key, 0, key.size)
            key
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts with XChaCha20-Poly1305. Returns payload in libsodium secretbox order:
     * tag (16 bytes) then ciphertext, so the Jotty web app can decrypt.
     */
    private fun encryptXChaCha20Poly1305(key: ByteArray, nonce24: ByteArray, plaintext: ByteArray): ByteArray? {
        if (nonce24.size != NONCE_BYTES || key.size != KEY_BYTES) return null
        val subkey = XChaCha20Decryptor.hChaCha20Block(key, nonce24.copyOfRange(0, 16)) ?: return null
        val nonce12 = ByteArray(12).apply { System.arraycopy(nonce24, 16, this, 0, 8) }
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(true, ParametersWithIV(KeyParameter(subkey), nonce12))
            val ciphertextThenTag = ByteArray(plaintext.size + TAG_BYTES)
            cipher.processBytes(plaintext, 0, plaintext.size, ciphertextThenTag, 0)
            cipher.doFinal(ciphertextThenTag, plaintext.size)
            // Bouncy Castle outputs ciphertext||tag; libsodium secretbox expects tag||ciphertext
            val tagThenCiphertext = ByteArray(ciphertextThenTag.size).apply {
                System.arraycopy(ciphertextThenTag, plaintext.size, this, 0, TAG_BYTES)
                System.arraycopy(ciphertextThenTag, 0, this, TAG_BYTES, plaintext.size)
            }
            tagThenCiphertext
        } catch (_: Exception) {
            null
        }
    }
}
