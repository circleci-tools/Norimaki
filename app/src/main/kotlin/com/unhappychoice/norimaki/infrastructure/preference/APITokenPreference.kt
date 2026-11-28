package com.unhappychoice.norimaki.infrastructure.preference

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class APITokenPreference internal constructor(
    private val preferences: SharedPreferences,
    private val secretKey: SecretKey?
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(API_TOKEN_PREFERENCE_NAME, Context.MODE_PRIVATE),
        loadOrGenerateKey()
    )

    var token: String
        get() {
            val stored = preferences.getString(TOKEN_KEY, null) ?: return ""
            val key = secretKey ?: return stored
            return try {
                decrypt(stored, key)
            } catch (e: Exception) {
                // Stored data can no longer be decrypted (Keystore key lost/invalidated, or
                // it predates encryption). Treat it as absent so TokenVerification.REQUIRED
                // simply asks the user to re-enter their token instead of crashing.
                ""
            }
        }
        set(value) {
            val key = secretKey
            val toStore = if (key == null) {
                value
            } else {
                try {
                    encrypt(value, key)
                } catch (e: Exception) {
                    return
                }
            }
            preferences.edit().putString(TOKEN_KEY, toStore).apply()
        }

    private fun encrypt(value: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + IV_SEPARATOR + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String, key: SecretKey): String {
        val parts = stored.split(IV_SEPARATOR, limit = 2)
        require(parts.size == 2) { "malformed stored token" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private fun loadOrGenerateKey(): SecretKey? = try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
        } catch (e: Exception) {
            // No Keystore provider on this device/environment. Falling back to storing the
            // token as-is keeps the app usable; on every real Android device (minSdk 23+)
            // AndroidKeyStore is always available, so this only happens off-device.
            null
        }

        private fun generateKey(): SecretKey {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return keyGenerator.generateKey()
        }
    }
}

internal const val API_TOKEN_PREFERENCE_NAME = "access_token"
internal const val TOKEN_KEY = "token"
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private const val KEY_ALIAS = "circleci_api_token"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
private const val IV_SEPARATOR = ":"
