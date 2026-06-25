package com.jotty.android.data.encryption

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.text.Normalizer
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XChaCha20EncryptorTest {
    @Test
    fun `wrapWithFrontmatter quotes title containing frontmatter delimiter`() {
        val body = """{"alg":"xchacha20","salt":"abc","nonce":"def","data":"ghi"}"""
        val wrapped =
            XChaCha20Encryptor.wrapWithFrontmatter(
                "note-1",
                "Section --- notes",
                "Work",
                body,
            )
        val parsed = NoteEncryption.parse(wrapped)
        assertTrue(parsed is ParsedNoteContent.Encrypted)
        val enc = parsed as ParsedNoteContent.Encrypted
        assertEquals(body, enc.encryptedBody)
        assertEquals("Section --- notes", enc.frontmatter.title)
    }

    @Test
    fun `yamlScalar leaves simple titles unquoted`() {
        assertEquals("My Title", XChaCha20Encryptor.yamlScalar("My Title"))
    }

    @Test
    fun `yamlScalar quotes titles with colons`() {
        assertEquals("\"My: Title\"", XChaCha20Encryptor.yamlScalar("My: Title"))
    }

    @Test
    fun `wrapWithFrontmatter produces valid YAML frontmatter`() {
        val body = """{"alg":"xchacha20","salt":"abc","nonce":"def","data":"ghi"}"""
        val result = XChaCha20Encryptor.wrapWithFrontmatter("note-1", "My Title", "Work", body)

        assertTrue(result.startsWith("---"))
        assertTrue(result.contains("uuid: note-1"))
        assertTrue(result.contains("title: My Title"))
        assertTrue(result.contains("encrypted: true"))
        assertTrue(result.contains("encryptionMethod: xchacha"))
        assertTrue(result.contains(body))
    }

    @Test
    fun `wrapWithFrontmatter output is parseable by NoteEncryption`() {
        val body = """{"alg":"xchacha20","salt":"abc","nonce":"def","data":"ghi"}"""
        val wrapped = XChaCha20Encryptor.wrapWithFrontmatter("id-1", "Test", "General", body)
        val parsed = NoteEncryption.parse(wrapped)

        assertTrue(parsed is ParsedNoteContent.Encrypted)
        val enc = parsed as ParsedNoteContent.Encrypted
        assertEquals("xchacha", enc.encryptionMethod)
        assertEquals("id-1", enc.frontmatter.uuid)
        assertEquals("Test", enc.frontmatter.title)
    }

    @Test
    fun `encrypt then decrypt round-trip accepts empty plaintext`() {
        val passphrase = "my secure passphrase 123"
        val encrypted = XChaCha20Encryptor.encrypt("", passphrase)
        assertNotNull(encrypted)
        assertEquals("", XChaCha20Decryptor.decrypt(encrypted!!, passphrase))
    }

    @Test
    fun `encrypt then decrypt round-trip returns original plaintext`() {
        val plaintext = "Hello, secret note with unicode: 日本語 🎉"
        val passphrase = "my secure passphrase 123"
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, passphrase)
        assertNotNull(encrypted)
        assertArgonParamsPresent(encrypted!!)
        val decrypted = XChaCha20Decryptor.decrypt(encrypted, passphrase)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt uses 64 MiB Argon2 on devices where primary preset succeeds`() {
        val encrypted =
            XChaCha20Encryptor.encrypt("Primary preset probe", "my secure passphrase 123")
                ?: return // Primary OOM on this runner; covered by 32 MiB fallback test
        assertEquals(65536, parseArgonMemoryKb(encrypted))
        assertEquals(2, parseArgonIterations(encrypted))
        assertEquals(1, parseArgonParallelism(encrypted))
    }

    @Test
    fun `32 MiB Argon2 params encrypt and decrypt round-trip`() {
        val plaintext = "Low memory device note"
        val passphrase = "my secure passphrase 123".toCharArray()
        val encrypted =
            XChaCha20Encryptor.encryptWithArgonDims(
                plaintext,
                passphrase,
                XChaCha20Encryptor.ARGON_FALLBACK,
            )
        passphrase.clearPassphrase()
        assertNotNull(encrypted)
        assertEquals(32768, parseArgonMemoryKb(encrypted!!))
        assertEquals(plaintext, XChaCha20Decryptor.decrypt(encrypted, "my secure passphrase 123"))
    }

    @Test
    fun `decrypt uses embedded m field before preset guessing`() {
        val plaintext = "Embedded Argon params"
        val passphrase = "my secure passphrase 123".toCharArray()
        val encrypted =
            XChaCha20Encryptor.encryptWithArgonDims(
                plaintext,
                passphrase,
                XChaCha20Encryptor.ARGON_FALLBACK,
            )
        passphrase.clearPassphrase()
        assertNotNull(encrypted)
        val result = XChaCha20Decryptor.decryptWithReason(encrypted!!, "my secure passphrase 123")
        assertEquals(plaintext, result.plaintext)
        assertFalse(result.usedLegacyDataOrder)
    }

    @Test
    fun `encrypt with whitespace-only passphrase returns null`() {
        assertNull(XChaCha20Encryptor.encrypt("Secret", "   \t\n  "))
    }

    @Test
    fun `encrypt with empty passphrase returns null`() {
        assertNull(XChaCha20Encryptor.encrypt("Secret", ""))
    }

    @Test
    fun `decrypt with wrong passphrase returns null`() {
        val plaintext = "Secret"
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, "correct-pass12")!!
        assertNull(XChaCha20Decryptor.decrypt(encrypted, "wrong-pass-12"))
    }

    @Test
    fun `decrypt with trimmed passphrase matches encrypt with trimmed passphrase`() {
        val plaintext = "Content"
        val passphraseWithSpaces = "  trim-me-pass  "
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, passphraseWithSpaces)!!
        assertEquals(plaintext, XChaCha20Decryptor.decrypt(encrypted, passphraseWithSpaces))
        assertEquals(plaintext, XChaCha20Decryptor.decrypt(encrypted, "trim-me-pass"))
    }

    @Test
    fun `encrypt outputs hex-encoded fields for Jotty web compatibility`() {
        val plaintext = "Web-compatible secret"
        val passphrase = "my secure passphrase 123"
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, passphrase)
        assertNotNull(encrypted)
        val hexField = """"salt"\s*:\s*"([^"]+)"""".toRegex().find(encrypted!!)?.groupValues?.get(1)
        assertNotNull(hexField)
        assertTrue(hexField!!.matches(Regex("[0-9a-f]+")))
        assertFalse(hexField.contains("+"))
        assertFalse(hexField.contains("/"))
        assertEquals(plaintext, XChaCha20Decryptor.decrypt(encrypted, passphrase))
    }

    @Test
    fun `decrypt accepts URL-safe base64 and unpadded base64 in JSON`() {
        val plaintext = "Note encrypted with standard base64"
        val passphrase = "test-pass-12"
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        // Legacy base64 payloads (older Android builds): convert to URL-safe unpadded form.
        val base64Json =
            encrypted
                .replace(Regex(""""salt"\s*:\s*"([0-9a-f]+)"""")) {
                    """"salt":"${Base64.getEncoder().encodeToString(hexToBytes(it.groupValues[1]))}""""
                }.replace(Regex(""""nonce"\s*:\s*"([0-9a-f]+)"""")) {
                    """"nonce":"${Base64.getEncoder().encodeToString(hexToBytes(it.groupValues[1]))}""""
                }.replace(Regex(""""data"\s*:\s*"([0-9a-f]+)"""")) {
                    """"data":"${Base64.getEncoder().encodeToString(hexToBytes(it.groupValues[1]))}""""
                }
        val urlSafeUnpadded = base64Json.replace("+", "-").replace("/", "_").replace("=", "")
        val decrypted = XChaCha20Decryptor.decrypt(urlSafeUnpadded, passphrase)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun assertArgonParamsPresent(json: String) {
        assertTrue("JSON should include Argon2 params for decrypt compatibility", json.contains("\"m\":") && json.contains("\"t\":"))
        assertTrue("JSON should include Argon2 parallelism", json.contains("\"p\":"))
    }

    private fun parseArgonMemoryKb(json: String): Int =
        """"m"\s*:\s*(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("missing m in $json")

    private fun parseArgonIterations(json: String): Int =
        """"t"\s*:\s*(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("missing t in $json")

    private fun parseArgonParallelism(json: String): Int =
        """"p"\s*:\s*(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toInt()
            ?: throw AssertionError("missing p in $json")

    @Test
    fun `encrypt outputs BC order ciphertext then tag and decrypt round-trips`() {
        // Encryptor must output ciphertext||tag (AEAD combined) for Jotty web compatibility.
        val plaintext = "Secret from Jotty web"
        val passphrase = "web passphrase"
        val encryptedJson = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val decrypted = XChaCha20Decryptor.decrypt(encryptedJson, passphrase)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
        val withReason = XChaCha20Decryptor.decryptWithReason(encryptedJson, passphrase)
        assertFalse(withReason.usedLegacyDataOrder)
    }

    @Test
    fun `decrypt accepts tag then ciphertext for backward compatibility`() {
        // Old app builds wrote tag||ciphertext. Reorder current ciphertext||tag output and ensure decrypt still works.
        val plaintext = "Backward compat"
        val passphrase = "test-pass-12"
        val encryptedJson = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val regex = """"data"\s*:\s*"([^"]+)"""".toRegex()
        val dataHex =
            regex.find(encryptedJson)?.groupValues?.get(1)
                ?: throw AssertionError("no data in JSON")
        val data =
            dataHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertTrue(data.size >= 16)
        val tagThenCiphertext =
            ByteArray(data.size).apply {
                val ciphertextLen = data.size - 16
                System.arraycopy(data, ciphertextLen, this, 0, 16)
                System.arraycopy(data, 0, this, 16, ciphertextLen)
            }
        val legacyOrderHex = tagThenCiphertext.joinToString("") { "%02x".format(it) }
        val legacyOrderJson = encryptedJson.replace("\"$dataHex\"", "\"$legacyOrderHex\"")
        val result = XChaCha20Decryptor.decryptWithReason(legacyOrderJson, passphrase)
        assertNotNull(result.plaintext)
        assertEquals(plaintext, result.plaintext)
        assertTrue(result.usedLegacyDataOrder)
    }

    @Test
    fun `encrypt rejects passphrase shorter than minimum`() {
        assertNull(XChaCha20Encryptor.encrypt("secret", "short".toCharArray()))
        assertNull(XChaCha20Encryptor.encrypt("secret", "exactly11ch".toCharArray()))
    }

    @Test
    fun `encryptWithArgonDims fallback preset produces 32 MiB params`() {
        val passphrase = "my secure passphrase 123".toCharArray()
        val body =
            XChaCha20Encryptor.encryptWithArgonDims(
                "low memory note",
                passphrase,
                XChaCha20Encryptor.ARGON_FALLBACK,
            )
        passphrase.fill(' ')
        assertNotNull(body)
        assertTrue(XChaCha20Encryptor.bodyUsesArgonFallback(body!!))
        assertEquals("low memory note", XChaCha20Decryptor.decrypt(body, "my secure passphrase 123"))
    }

    @Test
    fun `decrypt accepts NFC-normalized passphrase variant`() {
        val plaintext = "NFC passphrase test"
        val passphraseNfc = "caf\u00e9-passphrase-12".toCharArray()
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, passphraseNfc)!!
        passphraseNfc.fill(' ')
        val nfdPass = Normalizer.normalize("caf\u00e9-passphrase-12", Normalizer.Form.NFD).toCharArray()
        assertEquals(plaintext, XChaCha20Decryptor.decrypt(encrypted, nfdPass))
        nfdPass.fill(' ')
    }

    @Test
    fun `golden jotty web fixture decrypts from test resources`() {
        val fixtureJson =
            requireNotNull(javaClass.classLoader)
                .getResourceAsStream("jotty_web_encrypted_note.json")!!
                .bufferedReader()
                .readText()
        val fixture = JSONObject(fixtureJson)
        val body = fixture.getString("body")
        val passphrase = fixture.getString("passphrase")
        val expectedPlaintext = fixture.getString("plaintext")
        val result = XChaCha20Decryptor.decryptWithReason(body, passphrase)
        assertEquals(expectedPlaintext, result.plaintext)
        assertFalse(result.usedLegacyDataOrder)
    }

    @Test
    fun `app decrypts a note encrypted by Jotty web libsodium`() {
        // Cross-library vector produced by libsodium (pynacl) — the exact stack the Jotty web app
        // uses (Argon2id t=2/m=64MiB/p=1, XChaCha20-Poly1305 IETF, hex fields, ciphertext||tag).
        // Deterministic salt (00..0f) and nonce (0x64..0x7b). Guards web→app compatibility.
        val webBody =
            """{"alg":"xchacha20","t":2,"m":65536,"p":1,""" +
                """"salt":"000102030405060708090a0b0c0d0e0f",""" +
                """"nonce":"6465666768696a6b6c6d6e6f707172737475767778797a7b",""" +
                """"data":"bccbbac58782d14e991c46d57e169ae1a874024f1e3949fd8c21c80c2598be6a""" +
                """ab71587a15275e7ba11fb565e0b3e923b0eb0ef45e47d215e82821a8700e2f76cba479710216b7dcbf8a4d25ce"}"""
        val expected = "Jotty web encrypted this\nLine2 unicode cafe \u00e9 \u65e5\u672c\u8a9e \uD83C\uDF89"
        val result = XChaCha20Decryptor.decryptWithReason(webBody, "web passphrase 123")
        assertEquals(expected, result.plaintext)
        assertFalse("Web/libsodium note must decrypt in IETF order, not legacy", result.usedLegacyDataOrder)
    }

    @Test
    fun `decrypt parses hex-encoded JSON from Jotty web and fails at auth not parse`() {
        // Web app stores salt/nonce/data as hex. Minimal valid-length hex payload (wrong key → auth fail, not parse).
        val hexJson =
            """{"alg":"xchacha20","salt":"00000000000000000000000000000000",""" +
                """"nonce":"000000000000000000000000000000000000000000000000",""" +
                """"data":"0000000000000000000000000000000000000000000000000000000000000000"}"""
        val result = XChaCha20Decryptor.decryptWithReason(hexJson, "any passphrase")
        assertNull(result.plaintext)
        assertNotNull(result.failureReason)
        val reason = requireNotNull(result.failureReason)
        assertTrue(
            "Expected auth failure (hex parsed), not parse failure: $reason",
            reason.contains("Auth failed") || reason.contains("Key derivation"),
        )
    }
}
