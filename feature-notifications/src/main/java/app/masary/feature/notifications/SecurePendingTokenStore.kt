package app.masary.feature.notifications

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores pending FCM material encrypted with a non-exportable Android Keystore key. */
class SecurePendingTokenStore(context: Context, private val purpose: String = "fcm") {
    private val preferences = context.getSharedPreferences("notification_pending_v1_$purpose", Context.MODE_PRIVATE)
    fun write(token: String): Boolean {
        if (token.isBlank()) return false
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            preferences.edit().putString(DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit()
        }.getOrDefault(false)
    }
    fun read(): String? = runCatching {
        val data = preferences.getString(DATA, null) ?: return null
        val iv = preferences.getString(IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
    fun clear() { preferences.edit().clear().apply() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private val alias get() = "masary.notifications.pending.v1.$purpose"
    private companion object { const val DATA="ciphertext"; const val IV="iv"; const val TRANSFORMATION="AES/GCM/NoPadding" }
}
