package com.jotty.android.data.encryption

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

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
        val decrypted = XChaCha20Decryptor.decrypt(encrypted!!, passphrase)
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
    fun `decrypt accepts URL-safe base64 and unpadded base64 in JSON`() {
        val plaintext = "Note encrypted with standard base64"
        val passphrase = "pass"
        val encrypted = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        // Convert to URL-safe base64 (e.g. as produced by Jotty web): + -> -, / -> _, remove padding
        val urlSafeUnpadded = encrypted.replace("+", "-").replace("/", "_").replace("=", "")
        val decrypted = XChaCha20Decryptor.decrypt(urlSafeUnpadded, passphrase)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt accepts libsodium secretbox format tag then ciphertext`() {
        // Encryptor now produces tag||ciphertext (libsodium format) so web can decrypt. Verify round-trip.
        val plaintext = "Secret from Jotty web"
        val passphrase = "web passphrase"
        val encryptedJson = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val decrypted = XChaCha20Decryptor.decrypt(encryptedJson, passphrase)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt accepts BC order ciphertext then tag for backward compatibility`() {
        // Encryptor outputs tag||ciphertext. Reorder to ciphertext||tag (old app format); decryptor should still accept.
        val plaintext = "Backward compat"
        val passphrase = "pass"
        val encryptedJson = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val regex = """"data"\s*:\s*"([^"]+)"""".toRegex()
        val dataB64 = regex.find(encryptedJson)?.groupValues?.get(1) ?: fail("no data in JSON")
        val data = Base64.getDecoder().decode(dataB64)
        assertTrue(data.size >= 16)
        val ciphertextThenTag = ByteArray(data.size).apply {
            System.arraycopy(data, 16, this, 0, data.size - 16)
            System.arraycopy(data, 0, this, data.size - 16, 16)
        }
        val bcOrderB64 = Base64.getEncoder().encodeToString(ciphertextThenTag)
        val bcOrderJson = encryptedJson.replace("\"$dataB64\"", "\"$bcOrderB64\"")
        val decrypted = XChaCha20Decryptor.decrypt(bcOrderJson, passphrase)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `decrypt parses hex-encoded JSON from Jotty web and fails at auth not parse`() {
        // Web app stores salt/nonce/data as hex. Minimal valid-length hex payload (wrong key → auth fail, not parse).
        val hexJson = """{"alg":"xchacha20","salt":"00000000000000000000000000000000","nonce":"000000000000000000000000000000000000000000000000","data":"0000000000000000000000000000000000000000000000000000000000000000"}"""
        val result = XChaCha20Decryptor.decryptWithReason(hexJson, "any passphrase")
        assertNull(result.plaintext)
        assertNotNull(result.failureReason)
        assertTrue(
            "Expected auth failure (hex parsed), not parse failure: ${result.failureReason}",
            result.failureReason!!.contains("Auth failed") || result.failureReason!!.contains("Key derivation")
        )
    }
}
