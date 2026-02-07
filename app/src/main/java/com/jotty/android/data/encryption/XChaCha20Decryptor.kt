package com.jotty.android.data.encryption

import com.google.gson.annotations.SerializedName
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.util.Base64

/**
 * Decrypts Jotty XChaCha20-Poly1305 encrypted note body.
 * Format: JSON with "alg","salt","nonce","data" (base64). Key derived with Argon2id.
 * Compatible with Jotty web app encryption (see howto/ENCRYPTION.md).
 */
object XChaCha20Decryptor {

    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_MEMORY_KB = 65536
    private const val ARGON2_PARALLELISM = 1
    private const val KEY_BYTES = 32
    private const val TAG_BYTES = 16

    /**
     * Decrypts [encryptedBodyJson] (JSON with salt, nonce, data in base64) using [passphrase].
     * Passphrase is trimmed. Accepts standard and URL-safe base64 in the JSON.
     * @return decrypted plaintext or null on failure (wrong passphrase, bad format, etc.)
     */
    fun decrypt(encryptedBodyJson: String, passphrase: String): String? {
        val (salt, nonce, data) = parseEncryptedBody(encryptedBodyJson) ?: return null
        val key = deriveKey(passphrase.trim(), salt) ?: return null
        return decryptXChaCha20Poly1305(key, nonce, data)
    }

    private fun parseEncryptedBody(json: String): Triple<ByteArray, ByteArray, ByteArray>? {
        return try {
            val body = GSON.fromJson(json.trim(), EncryptedBodyJson::class.java)
                ?: return null
            val saltB64 = body.salt?.takeIf { it.isNotBlank() } ?: return null
            val nonceB64 = body.nonce?.takeIf { it.isNotBlank() } ?: return null
            val dataB64 = body.data?.takeIf { it.isNotBlank() } ?: return null
            val salt = decodeBase64(saltB64) ?: return null
            val nonce = decodeBase64(nonceB64) ?: return null
            val data = decodeBase64(dataB64) ?: return null
            if (nonce.size != 24 || data.size < TAG_BYTES) return null
            Triple(salt, nonce, data)
        } catch (_: Exception) {
            null
        }
    }

    /** Decodes base64 string; accepts both standard and URL-safe base64 (e.g. from Jotty web). */
    private fun decodeBase64(s: String): ByteArray? {
        val normalized = s.replace('-', '+').replace('_', '/')
        return try {
            Base64.getDecoder().decode(normalized)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private data class EncryptedBodyJson(
        @SerializedName("alg") val alg: String? = null,
        @SerializedName("salt") val salt: String? = null,
        @SerializedName("nonce") val nonce: String? = null,
        @SerializedName("data") val data: String? = null,
    )

    private val GSON = com.google.gson.Gson()

    private fun deriveKey(passphrase: String, salt: ByteArray): ByteArray? {
        if (passphrase.isEmpty()) return null
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
     * XChaCha20-Poly1305: HChaCha20(key, nonce[0..15]) -> subkey; then
     * ChaCha20-Poly1305(subkey, nonce[16..23] || 0x00000000, ciphertext).
     */
    private fun decryptXChaCha20Poly1305(key: ByteArray, nonce24: ByteArray, ciphertextAndTag: ByteArray): String? {
        if (nonce24.size != 24 || key.size != 32 || ciphertextAndTag.size < TAG_BYTES) return null
        val subkey = hChaCha20Block(key, nonce24.copyOfRange(0, 16)) ?: return null
        val nonce12 = ByteArray(12).apply {
            System.arraycopy(nonce24, 16, this, 0, 8)
        }
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(false, ParametersWithIV(KeyParameter(subkey), nonce12))
            val plain = ByteArray(ciphertextAndTag.size - TAG_BYTES)
            cipher.processBytes(ciphertextAndTag, 0, ciphertextAndTag.size, plain, 0)
            cipher.doFinal(plain, 0)
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    internal fun hChaCha20Block(key: ByteArray, nonce: ByteArray): ByteArray? {
        // HChaCha20: state = constants || key (8 words) || nonce (4 words = 16 bytes)
        if (key.size != 32 || nonce.size != 16) return null
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = leBytesToInt(key, i * 4)
        state[12] = leBytesToInt(nonce, 0)
        state[13] = leBytesToInt(nonce, 4)
        state[14] = leBytesToInt(nonce, 8)
        state[15] = leBytesToInt(nonce, 12)
        // 20 rounds (10 double rounds)
        for (i in 0 until 10) {
            quarterRound(state, 0, 4, 8, 12)
            quarterRound(state, 1, 5, 9, 13)
            quarterRound(state, 2, 6, 10, 14)
            quarterRound(state, 3, 7, 11, 15)
            quarterRound(state, 0, 5, 10, 15)
            quarterRound(state, 1, 6, 11, 12)
            quarterRound(state, 2, 7, 8, 13)
            quarterRound(state, 3, 4, 9, 14)
        }
        // HChaCha20 output: state[0,1,2,3], state[12], state[13], state[14], state[15] (no addition with initial state for these)
        return byteArrayOf(
            *intToLeBytes(state[0]), *intToLeBytes(state[1]), *intToLeBytes(state[2]), *intToLeBytes(state[3]),
            *intToLeBytes(state[12]), *intToLeBytes(state[13]), *intToLeBytes(state[14]), *intToLeBytes(state[15])
        )
    }

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] = s[a] + s[b]; s[d] = rotl(s[d] xor s[a], 16)
        s[c] = s[c] + s[d]; s[b] = rotl(s[b] xor s[c], 12)
        s[a] = s[a] + s[b]; s[d] = rotl(s[d] xor s[a], 8)
        s[c] = s[c] + s[d]; s[b] = rotl(s[b] xor s[c], 7)
    }

    private fun rotl(x: Int, n: Int) = (x shl n) or (x ushr (32 - n))

    private fun leBytesToInt(b: ByteArray, i: Int): Int {
        return (b[i].toInt() and 0xff) or
            ((b[i + 1].toInt() and 0xff) shl 8) or
            ((b[i + 2].toInt() and 0xff) shl 16) or
            ((b[i + 3].toInt() and 0xff) shl 24)
    }

    private fun intToLeBytes(v: Int): ByteArray {
        return byteArrayOf(
            (v and 0xff).toByte(),
            (v shr 8 and 0xff).toByte(),
            (v shr 16 and 0xff).toByte(),
            (v shr 24 and 0xff).toByte()
        )
    }
}
