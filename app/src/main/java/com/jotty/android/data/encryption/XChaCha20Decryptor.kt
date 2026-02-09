package com.jotty.android.data.encryption

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.jotty.android.util.AppLog
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.text.Normalizer
import java.util.Base64

/**
 * Result of a decrypt attempt. When [plaintext] is null, [failureReason] describes why (for UI when debug is on).
 */
data class DecryptResult(
    val plaintext: String?,
    val failureReason: String?,
) {
    val isSuccess: Boolean get() = plaintext != null
}

/**
 * Decrypts Jotty XChaCha20-Poly1305 encrypted note body.
 * Format: JSON with "alg","salt","nonce","data" (base64). Key derived with Argon2id.
 * Compatible with Jotty web app encryption (see howto/ENCRYPTION.md).
 */
object XChaCha20Decryptor {

    private const val LOG_TAG = "Jotty/encryption"

    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_MEMORY_KB = 65536
    private const val ARGON2_PARALLELISM = 1
    private const val KEY_BYTES = 32
    private const val TAG_BYTES = 16

    /**
     * Argon2 (iterations, memoryKB, parallelism) presets to try when decrypting.
     * Primary: Android app format. Fallbacks: common libsodium/JS variants so notes
     * encrypted by the Jotty web app (libsodium-wrappers-sumo) decrypt correctly.
     */
    private data class Argon2Preset(val iterations: Int, val memoryKb: Int, val parallelism: Int = 1)

    private val ARGON2_PRESETS = listOf(
        Argon2Preset(2, 65536),      // App + libsodium INTERACTIVE (64 MiB)
        Argon2Preset(2, 65536, 2),   // same with parallelism 2 (some bindings)
        Argon2Preset(3, 65536),
        Argon2Preset(4, 32768),      // Some libsodium bindings use 32 MiB
        Argon2Preset(2, 32768),
        Argon2Preset(1, 65536),      // Min ops (e.g. test/default in some libs)
        Argon2Preset(2, 131072),     // 128 MiB (capped; 256 MiB can OOM on devices)
    )

    /** Failure reason strings for UI (shown when Settings → Debug logging is on). */
    const val FAILURE_PARSE = "Parse failed (invalid JSON or base64)"
    const val FAILURE_KEY_DERIVATION = "Key derivation failed"
    const val FAILURE_AUTH = "Auth failed (wrong passphrase or tag mismatch)"

    /**
     * Decrypts and returns a [DecryptResult] with optional [failureReason] for diagnostics.
     * Use [decrypt] when only the plaintext is needed.
     */
    fun decryptWithReason(encryptedBodyJson: String, passphrase: String): DecryptResult {
        return try {
            decryptWithReasonOrThrow(encryptedBodyJson, passphrase)
        } catch (e: OutOfMemoryError) {
            Log.e(LOG_TAG, "Decrypt: OutOfMemoryError (e.g. Argon2 preset too large)", e)
            AppLog.d("encryption", "Decrypt: OOM — try a note with smaller Argon2 params")
            DecryptResult(null, "Decryption failed (out of memory)")
        } catch (e: Throwable) {
            Log.e(LOG_TAG, "Decrypt: unexpected error", e)
            AppLog.d("encryption", "Decrypt: ${e.message}")
            DecryptResult(null, "Decryption failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun decryptWithReasonOrThrow(encryptedBodyJson: String, passphrase: String): DecryptResult {
        var json = encryptedBodyJson.trim().trimStart('\uFEFF')
        json = stripMarkdownCodeFence(json)
        Log.i(LOG_TAG, "Decrypt attempt: jsonLength=${json.length}, passphraseLength=${passphrase.length}, jsonStart=${json.take(80).replace("\n", " ")}")
        val parsed = parseEncryptedBody(json)
        if (parsed.first == null) {
            val reason = parsed.second ?: FAILURE_PARSE
            Log.w(LOG_TAG, "Decrypt: parse failed — $reason")
            AppLog.d("encryption", "Decrypt: parse failed — $reason")
            return DecryptResult(null, reason)
        }
        val payload = parsed.first!!
        val (salt, nonce, data) = Triple(payload.salt, payload.nonce, payload.data)
        // Jotty web uses IETF AEAD (ciphertext||tag). App uses libsodium secretbox (tag||ciphertext). Try the right one first.
        val preferLibsodiumOrder = !payload.usedHex
        if (passphrase.trim().isEmpty()) {
            Log.w(LOG_TAG, "Decrypt: key derivation failed (empty passphrase)")
            return DecryptResult(null, FAILURE_KEY_DERIVATION)
        }
        val nonce24Candidates = when {
            nonce.size == 24 -> listOf(nonce)
            nonce.size > 24 -> listOf(
                nonce.copyOfRange(0, 24),
                nonce.copyOfRange(nonce.size - 24, nonce.size),
            )
            else -> emptyList()
        }
        // Try trimmed, NFC-normalized, and raw passphrase (web may use different Unicode form).
        val trimmed = passphrase.trim()
        val nfcTrimmed = Normalizer.normalize(trimmed, Normalizer.Form.NFC)
        val nfcRaw = Normalizer.normalize(passphrase, Normalizer.Form.NFC)
        val passphrasesToTry = listOfNotNull(trimmed, nfcTrimmed, nfcRaw)
            .distinct() + (if (passphrase != trimmed) listOf(passphrase) else emptyList())
        for (pass in passphrasesToTry) {
            if (pass.isEmpty()) continue
            for (preset in ARGON2_PRESETS) {
                val key = try {
                    deriveKey(pass, salt, preset.iterations, preset.memoryKb, preset.parallelism)
                } catch (_: OutOfMemoryError) {
                    null
                } ?: continue
                for (nonce24 in nonce24Candidates) {
                    val result = decryptXChaCha20Poly1305(key, nonce24, data, preferLibsodiumOrder)
                    if (result != null) {
                        if (pass != passphrase.trim()) {
                            Log.i(LOG_TAG, "Decrypt success with untrimmed passphrase")
                        }
                        if (preset.iterations != ARGON2_ITERATIONS || preset.memoryKb != ARGON2_MEMORY_KB || preset.parallelism != ARGON2_PARALLELISM) {
                            Log.i(LOG_TAG, "Decrypt success with Argon2 fallback (iterations=${preset.iterations}, memoryKb=${preset.memoryKb}, parallelism=${preset.parallelism})")
                        }
                        if (nonce.size > 24 && nonce24 === nonce24Candidates.getOrNull(1)) {
                            Log.i(LOG_TAG, "Decrypt success with last 24 bytes of ${nonce.size}-byte nonce")
                        }
                        Log.i(LOG_TAG, "Decrypt success: plaintextLength=${result.length}")
                        return DecryptResult(result, null)
                    }
                }
            }
        }
        Log.w(LOG_TAG, "Decrypt: auth failed (wrong passphrase or corrupted data — Poly1305 tag mismatch)")
        AppLog.d("encryption", "Decrypt: auth failed")
        return DecryptResult(null, FAILURE_AUTH)
    }

    /**
     * Decrypts [encryptedBodyJson] (JSON with salt, nonce, data in base64) using [passphrase].
     * Passphrase is trimmed. Accepts standard and URL-safe base64 in the JSON.
     * @return decrypted plaintext or null on failure (wrong passphrase, bad format, etc.)
     */
    fun decrypt(encryptedBodyJson: String, passphrase: String): String? =
        decryptWithReason(encryptedBodyJson, passphrase).plaintext

    /** Removes markdown code fence (e.g. ```json ... ```) if present, so pasted/wrapped payloads still parse. */
    private fun stripMarkdownCodeFence(s: String): String {
        val trimmed = s.trim()
        val regex = Regex("^```(?:json)?\\s*\\n?(.*)\\n?```\\s*$", RegexOption.DOT_MATCHES_ALL)
        return regex.replace(trimmed) { it.groupValues[1].trim() }.trim().ifEmpty { trimmed }
    }

    /** Result of parse: salt, nonce, data, and whether any field was decoded from hex (Jotty web format). */
    private data class ParsedPayload(val salt: ByteArray, val nonce: ByteArray, val data: ByteArray, val usedHex: Boolean) {
        override fun equals(other: Any?) = (other is ParsedPayload) && salt.contentEquals(other.salt) && nonce.contentEquals(other.nonce) && data.contentEquals(other.data) && usedHex == other.usedHex
        override fun hashCode() = salt.contentHashCode() + 31 * (nonce.contentHashCode() + 31 * (data.contentHashCode() + 31 * usedHex.hashCode()))
    }

    /**
     * Returns (ParsedPayload, null) on success, (null, reason) on parse failure.
     * Reason is a short string for UI when debug logging is on.
     */
    private fun parseEncryptedBody(json: String): Pair<ParsedPayload?, String?> {
        return try {
            val body = GSON.fromJson(json, EncryptedBodyJson::class.java)
            if (body == null) {
                Log.w(LOG_TAG, "Parse: GSON returned null")
                return null to "Invalid JSON (empty or not an object)"
            }
            val saltStr = body.salt?.takeIf { it.isNotBlank() }
            if (saltStr == null) {
                Log.w(LOG_TAG, "Parse: missing or blank salt")
                return null to "Missing or blank salt"
            }
            val nonceStr = body.nonce?.takeIf { it.isNotBlank() }
            if (nonceStr == null) {
                Log.w(LOG_TAG, "Parse: missing or blank nonce")
                return null to "Missing or blank nonce"
            }
            val dataStr = body.data?.takeIf { it.isNotBlank() }
            if (dataStr == null) {
                Log.w(LOG_TAG, "Parse: missing or blank data")
                return null to "Missing or blank data"
            }
            val (salt, saltHex) = decodeBase64OrHexWithSource(saltStr)
            if (salt == null) {
                Log.w(LOG_TAG, "Parse: salt decode failed (length=${saltStr.length}, sample=${saltStr.take(20)})")
                AppLog.d("encryption", "Parse: salt decode failed; first chars: ${saltStr.take(30)}")
                return null to "Invalid base64/hex in salt"
            }
            val (nonce, nonceHex) = decodeBase64OrHexWithSource(nonceStr)
            if (nonce == null) {
                Log.w(LOG_TAG, "Parse: nonce decode failed (length=${nonceStr.length})")
                return null to "Invalid base64/hex in nonce"
            }
            val (data, dataHex) = decodeBase64OrHexWithSource(dataStr)
            if (data == null) {
                Log.w(LOG_TAG, "Parse: data decode failed (length=${dataStr.length})")
                return null to "Invalid base64/hex in data"
            }
            val usedHex = saltHex || nonceHex || dataHex
            if (usedHex) {
                Log.i(LOG_TAG, "Parse: payload from hex (Jotty web format); salt=${salt.size}B nonce=${nonce.size}B data=${data.size}B")
                AppLog.d("encryption", "Parse: hex payload (web); will try IETF order first")
            }
            if (nonce.size < 24) {
                Log.w(LOG_TAG, "Parse: nonce size ${nonce.size} < 24")
                return null to "Nonce must be at least 24 bytes (got ${nonce.size})"
            }
            if (nonce.size > 24) {
                Log.i(LOG_TAG, "Parse: nonce size ${nonce.size}; will try first 24 and last 24 bytes")
            }
            if (data.size < TAG_BYTES) {
                Log.w(LOG_TAG, "Parse: data size ${data.size} < TAG_BYTES ($TAG_BYTES)")
                return null to "Ciphertext too short"
            }
            ParsedPayload(salt, nonce, data, usedHex) to null
        } catch (e: com.google.gson.JsonSyntaxException) {
            Log.w(LOG_TAG, "Parse: JSON syntax exception", e)
            null to "Invalid JSON (syntax error)"
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Parse: exception", e)
            null to "Parse error: ${e.message ?: "unknown"}"
        }
    }

    /**
     * Decodes salt/nonce/data from JSON. Jotty web app uses hex (to_hex); Android app uses base64.
     * Returns (bytes, true) if decoded from hex, (bytes, false) from base64, (null, false) on failure.
     * Tries hex first when string is only hex chars (after stripping whitespace); else base64.
     */
    private fun decodeBase64OrHexWithSource(s: String): Pair<ByteArray?, Boolean> {
        val stripped = s.replace("\\s".toRegex(), "")
        val hexOnly = stripped.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        if (hexOnly.length % 2 == 0 && hexOnly.length == stripped.length && hexOnly.isNotEmpty()) {
            val decoded = decodeHex(stripped)
            return if (decoded != null) decoded to true else null to false
        }
        val decoded = decodeBase64(stripped)
        return if (decoded != null) decoded to false else null to false
    }

    private fun decodeHex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val high = s[i * 2].hexDigitValue() ?: return null
            val low = s[i * 2 + 1].hexDigitValue() ?: return null
            out[i] = (high * 16 + low).toByte()
        }
        return out
    }

    private fun Char.hexDigitValue(): Int? = when (this) {
        in '0'..'9' -> code - '0'.code
        in 'a'..'f' -> code - 'a'.code + 10
        in 'A'..'F' -> code - 'A'.code + 10
        else -> null
    }

    /** Decodes base64 string; accepts standard and URL-safe base64, with or without padding. */
    private fun decodeBase64(s: String): ByteArray? {
        val normalized = s.replace('-', '+').replace('_', '/')
        val padded = when (normalized.length % 4) {
            0 -> normalized
            2 -> normalized + "=="
            3 -> normalized + "="
            else -> normalized
        }
        return try {
            Base64.getDecoder().decode(padded)
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

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = ARGON2_ITERATIONS, memoryKb: Int = ARGON2_MEMORY_KB, parallelism: Int = ARGON2_PARALLELISM): ByteArray? {
        if (passphrase.isEmpty()) return null
        return try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKb)
                .withParallelism(parallelism)
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
     * Bouncy Castle expects [ciphertext][tag] (tag at end). Libsodium secretbox uses [tag][ciphertext].
     * When [preferLibsodiumOrder] is true (e.g. 36-byte nonce from web), try tag||ciphertext first.
     */
    private fun decryptXChaCha20Poly1305(key: ByteArray, nonce24: ByteArray, ciphertextAndTag: ByteArray, preferLibsodiumOrder: Boolean = false): String? {
        if (nonce24.size != 24 || key.size != 32 || ciphertextAndTag.size < TAG_BYTES) return null
        val subkey = hChaCha20Block(key, nonce24.copyOfRange(0, 16)) ?: return null
        val nonce12 = ByteArray(12).apply {
            System.arraycopy(nonce24, 16, this, 0, 8)
        }
        if (preferLibsodiumOrder && ciphertextAndTag.size > TAG_BYTES) {
            val reordered = ByteArray(ciphertextAndTag.size).apply {
                System.arraycopy(ciphertextAndTag, TAG_BYTES, this, 0, size - TAG_BYTES)
                System.arraycopy(ciphertextAndTag, 0, this, size - TAG_BYTES, TAG_BYTES)
            }
            tryDecrypt(subkey, nonce12, reordered)?.let { return it }
        }
        tryDecrypt(subkey, nonce12, ciphertextAndTag)?.let { return it }
        if (!preferLibsodiumOrder && ciphertextAndTag.size > TAG_BYTES) {
            val reordered = ByteArray(ciphertextAndTag.size).apply {
                System.arraycopy(ciphertextAndTag, TAG_BYTES, this, 0, size - TAG_BYTES)
                System.arraycopy(ciphertextAndTag, 0, this, size - TAG_BYTES, TAG_BYTES)
            }
            tryDecrypt(subkey, nonce12, reordered)?.let { return it }
        }
        return null
    }

    /** Decrypts assuming [ciphertext][tag] (BC / IETF order). Returns null on auth failure or error. */
    private fun tryDecrypt(subkey: ByteArray, nonce12: ByteArray, ciphertextThenTag: ByteArray): String? {
        return try {
            val cipher = ChaCha20Poly1305()
            cipher.init(false, ParametersWithIV(KeyParameter(subkey), nonce12))
            val plain = ByteArray(ciphertextThenTag.size - TAG_BYTES)
            cipher.processBytes(ciphertextThenTag, 0, ciphertextThenTag.size, plain, 0)
            cipher.doFinal(plain, 0)
            try {
                String(plain, Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
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
