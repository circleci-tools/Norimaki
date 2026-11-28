package com.unhappychoice.norimaki.infrastructure.preference

import com.unhappychoice.norimaki.domain.service.SessionService
import io.mockk.mockk
import io.reactivex.Single
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class APITokenPreferenceTest {
    @Test
    fun roundTripsTheStoredTokenThroughTheDefaultConstructor() {
        val preference = APITokenPreference(RuntimeEnvironment.getApplication())

        preference.token = "a-dummy-token"

        assertEquals("a-dummy-token", preference.token)
    }

    @Test
    fun defaultsToAnEmptyTokenWhenNothingIsStored() {
        val preference = APITokenPreference(RuntimeEnvironment.getApplication())

        assertEquals("", preference.token)
    }

    @Test
    fun neverWritesTheTokenInPlaintextWhenAKeyIsAvailable() {
        val preferences = fakeSharedPreferences()
        val preference = APITokenPreference(preferences, generateAesKey())

        preference.token = "a-dummy-token"

        assertNotEquals("a-dummy-token", preferences.getString(TOKEN_KEY, null))
        assertEquals("a-dummy-token", preference.token)
    }

    @Test
    fun treatsUndecryptableStoredDataAsNoTokenInsteadOfCrashing() {
        val preferences = fakeSharedPreferences()
        preferences.edit().putString(TOKEN_KEY, "not-a-valid-ciphertext").apply()
        val preference = APITokenPreference(preferences, generateAesKey())

        assertEquals("", preference.token)
    }

    @Test
    fun fallsBackToPlainStorageWhenNoKeyIsAvailable() {
        val preferences = fakeSharedPreferences()
        val preference = APITokenPreference(preferences, secretKey = null)

        preference.token = "a-dummy-token"

        assertEquals("a-dummy-token", preference.token)
    }

    @Test
    fun restoresAnEncryptedReplacementFromDiskWithAFreshSession() {
        val application = RuntimeEnvironment.getApplication()
        val preferences = application.getSharedPreferences("original-token-store", 0)
        val key = generateAesKey()
        val preference = APITokenPreference(preferences, key).apply { token = "saved-token" }
        val session = createSession(preference)
        assertEquals("saved-token", session.restore().blockingGet().token)

        assertEquals("replacement-token", session.replace("replacement-token").blockingGet().token)
        assertNotEquals("replacement-token", preferences.getString(TOKEN_KEY, null))

        val directory = File(application.dataDir, "shared_prefs")
        File(directory, "original-token-store.xml").copyTo(File(directory, "restarted-token-store.xml"))
        val reloaded = application.getSharedPreferences("restarted-token-store", 0)
        val restarted = createSession(APITokenPreference(reloaded, key))
        assertEquals("replacement-token", restarted.restore().blockingGet().token)
    }

    private fun createSession(preference: APITokenPreference) = SessionService(
        readToken = { preference.token },
        writeToken = { preference.token = it },
        createClient = { mockk() },
        verify = { Single.just(mockk()) }
    )

    private fun fakeSharedPreferences() =
        RuntimeEnvironment.getApplication().getSharedPreferences("test_${System.nanoTime()}", 0)

    private fun generateAesKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
