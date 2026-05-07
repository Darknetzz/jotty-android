package com.jotty.android.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryMigrationTest {

    private val keyServerUrl = stringPreferencesKey("server_url")
    private val keyApiKey = stringPreferencesKey("api_key")
    private val keyInstances = stringPreferencesKey("instances")

    @Before
    fun clearDataStore() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.jottySettingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun migrateFromLegacyIfNeeded_keepsPlaintextApiKeyWhenEncryptedStoreUnavailable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.jottySettingsDataStore.edit {
            it[keyServerUrl] = "https://example.com/"
            it[keyApiKey] = "plain-secret-key"
        }
        val fake = object : ApiKeyStorage {
            override val isEncrypted = false
            val setApiKeyCalls = mutableListOf<Pair<String, String>>()
            override fun getApiKey(instanceId: String) = null
            override suspend fun setApiKey(instanceId: String, apiKey: String) {
                setApiKeyCalls.add(instanceId to apiKey)
            }

            override suspend fun removeApiKey(instanceId: String) {}
            override suspend fun clearAll() {}
        }
        val repo = SettingsRepository(context, fake)
        repo.migrateFromLegacyIfNeeded()

        assertEquals("plain-secret-key", repo.currentInstance.first()?.apiKey)
        assertTrue("setApiKey should not run when encryption unavailable", fake.setApiKeyCalls.isEmpty())

        val after = context.jottySettingsDataStore.data.first()
        assertNull(after[keyServerUrl])
        assertNull(after[keyApiKey])
        val instancesJson = after[keyInstances].orEmpty()
        assertTrue(instancesJson.contains("plain-secret-key"))
    }

    @Test
    fun migrateFromLegacyIfNeeded_storesKeyInEncryptedStoreWhenAvailable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.jottySettingsDataStore.edit {
            it[keyServerUrl] = "https://example.com/"
            it[keyApiKey] = "encrypted-path-secret"
        }
        val fake = FakeInMemoryApiKeyStorage(isEncrypted = true)
        val repo = SettingsRepository(context, fake)
        repo.migrateFromLegacyIfNeeded()

        val current = repo.currentInstance.first()
        assertEquals("encrypted-path-secret", current?.apiKey)
        assertEquals(1, fake.setApiKeyCalls.size)
        assertEquals("encrypted-path-secret", fake.getApiKey(requireNotNull(current?.id)))

        val after = context.jottySettingsDataStore.data.first()
        val instancesJson = after[keyInstances].orEmpty()
        assertTrue(instancesJson.contains("\"apiKey\":\"\""))
        assertTrue(!instancesJson.contains("encrypted-path-secret"))
    }

    /** Mimics [ApiKeyStore] persistence enough for migration tests. */
    private class FakeInMemoryApiKeyStorage(
        override val isEncrypted: Boolean,
    ) : ApiKeyStorage {
        private val keys = mutableMapOf<String, String>()
        val setApiKeyCalls = mutableListOf<Pair<String, String>>()

        override fun getApiKey(instanceId: String): String? =
            keys[instanceId]?.takeIf { it.isNotBlank() }

        override suspend fun setApiKey(instanceId: String, apiKey: String) {
            if (apiKey.isBlank()) return
            keys[instanceId] = apiKey
            setApiKeyCalls.add(instanceId to apiKey)
        }

        override suspend fun removeApiKey(instanceId: String) {
            keys.remove(instanceId)
        }

        override suspend fun clearAll() {
            keys.clear()
        }
    }
}
