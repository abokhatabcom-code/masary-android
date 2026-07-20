package app.masary.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.masary.core.models.auth.AuthTokens
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Stores only AES-GCM encrypted token material in app-private preferences.
 * The encryption key is generated inside Android Keystore and cannot be exported.
 */
class AndroidKeystoreTokenStore(context: Context) : TokenStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override suspend fun read(): AuthTokens? = synchronized(lock) {
        val encodedIv = preferences.getString(KEY_IV, null) ?: return@synchronized null
        val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return@synchronized null

        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            val plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
            val json = JSONObject(String(plaintext, StandardCharsets.UTF_8))
            AuthTokens(
                accessToken = json.getString(JSON_ACCESS_TOKEN),
                refreshToken = json.getString(JSON_REFRESH_TOKEN),
                expiresInSeconds = json.getLong(JSON_EXPIRES_IN),
                accessTokenExpiresAtEpochSeconds = json.getLong(JSON_EXPIRES_AT),
            ).takeIf { it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() }
        }.getOrElse {
            clearEncryptedValues()
            null
        }
    }

    override suspend fun save(tokens: AuthTokens) = synchronized(lock) {
        require(tokens.accessToken.isNotBlank()) { "Access token must not be blank" }
        require(tokens.refreshToken.isNotBlank()) { "Refresh token must not be blank" }

        val plaintext = JSONObject()
            .put(JSON_ACCESS_TOKEN, tokens.accessToken)
            .put(JSON_REFRESH_TOKEN, tokens.refreshToken)
            .put(JSON_EXPIRES_IN, tokens.expiresInSeconds)
            .put(JSON_EXPIRES_AT, tokens.accessTokenExpiresAtEpochSeconds)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        val saved = preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .commit()
        check(saved) { "Unable to persist encrypted session" }
    }

    override suspend fun clear() = synchronized(lock) {
        check(clearEncryptedValues()) { "Unable to clear encrypted session" }
    }

    private fun clearEncryptedValues(): Boolean = preferences.edit()
        .remove(KEY_IV)
        .remove(KEY_CIPHERTEXT)
        .commit()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "masary.student.auth.tokens.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val PREFERENCES_NAME = "masary_secure_auth_v1"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val JSON_ACCESS_TOKEN = "access_token"
        const val JSON_REFRESH_TOKEN = "refresh_token"
        const val JSON_EXPIRES_IN = "expires_in"
        const val JSON_EXPIRES_AT = "expires_at"
    }
}
