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
        // Our encryptor produces ciphertext||tag (BC/IETF). Jotty web may use libsodium secretbox (tag||ciphertext).
        // Simulate: take our encrypted JSON, decode "data", reorder to tag||ciphertext, re-encode, decrypt.
        val plaintext = "Secret from Jotty web"
        val passphrase = "web passphrase"
        val encryptedJson = XChaCha20Encryptor.encrypt(plaintext, passphrase)!!
        val regex = """"data"\s*:\s*"([^"]+)"""".toRegex()
        val dataB64 = regex.find(encryptedJson)?.groupValues?.get(1) ?: fail("no data in JSON")
        val data = Base64.getDecoder().decode(dataB64)
        assertTrue(data.size >= 16)
        val tagFirst = ByteArray(data.size).apply {
            System.arraycopy(data, data.size - 16, this, 0, 16)
            System.arraycopy(data, 0, this, 16, data.size - 16)
        }
        val tagFirstB64 = Base64.getEncoder().encodeToString(tagFirst)
        val libsodiumStyleJson = encryptedJson.replace("\"$dataB64\"", "\"$tagFirstB64\"")
        val decrypted = XChaCha20Decryptor.decrypt(libsodiumStyleJson, passphrase)
        assertNotNull(decrypted)
        assertEquals(plaintext, decrypted)
    }
}
