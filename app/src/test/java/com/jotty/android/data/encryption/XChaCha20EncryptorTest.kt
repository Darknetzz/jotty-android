package com.jotty.android.data.encryption

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class XChaCha20EncryptorTest {
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
    fun `encrypt then decrypt round-trip returns original plaintext`() {
        val plaintext = "Hello, secret note with unicode: 日本語 🎉"
        val passphrase = "my secure passphrase 123"
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, passphrase)
        assertNotNull(encrypted)
        assertTrue("JSON should include Argon2 params for decrypt compatibility", encrypted!!.contains("\"m\":") && encrypted.contains("\"t\":"))
        val decrypted = XChaCha20Decryptor.decrypt(encrypted, passphrase)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt with wrong passphrase returns null`() {
        val plaintext = "Secret"
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, "correct")!!
        assertNull(XChaCha20Decryptor.decrypt(encrypted, "wrong"))
    }

    @Test
    fun `decrypt with trimmed passphrase matches encrypt with trimmed passphrase`() {
        val plaintext = "Content"
        val passphraseWithSpaces = "  trim me  "
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, passphraseWithSpaces)!!
        assertEquals(plaintext, XChaCha20Decryptor.decrypt(encrypted, passphraseWithSpaces))
        assertEquals(plaintext, XChaCha20Decryptor.decrypt(encrypted, "trim me"))
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
        val passphrase = "pass"
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
        val passphrase = "pass"
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
    fun `decrypt parses hex-encoded JSON from Jotty web and fails at auth not parse`() {
        // Web app stores salt/nonce/data as hex. Minimal valid-length hex payload (wrong key → auth fail, not parse).
        val hexJson =
            """{"alg":"xchacha20","salt":"00000000000000000000000000000000",""" +
                """"nonce":"000000000000000000000000000000000000000000000000",""" +
                """"data":"0000000000000000000000000000000000000000000000000000000000000000"}"""
        val result = XChaCha20Decryptor.decryptWithReason(hexJson, "any passphrase")
        assertNull(result.plaintext)
        assertNotNull(result.failureReason)
        assertTrue(
            "Expected auth failure (hex parsed), not parse failure: ${result.failureReason}",
            result.failureReason!!.contains("Auth failed") || result.failureReason!!.contains("Key derivation"),
        )
    }
}
