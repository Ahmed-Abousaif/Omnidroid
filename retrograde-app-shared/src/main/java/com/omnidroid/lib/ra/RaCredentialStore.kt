package com.omnidroid.lib.ra

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class RaStoredCredentials(
    val username: String,
    val token: String,
)

interface RaTokenCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}

/**
 * Android Keystore AES-256-GCM cipher. Encrypted payload is safe to persist inside
 * cross-process Harmony SharedPreferences so both the main process and `:game` process
 * read/clear the token cleanly without storing raw passwords.
 */
class AndroidKeystoreRaCipher(
    private val keyAlias: String = KEY_ALIAS,
) : RaTokenCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext)
        return byteArrayOf(iv.size.toByte()) + iv + encrypted
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size < 2) throw IllegalArgumentException("Malformed ciphertext")
        val ivLen = ciphertext[0].toInt() and 0xFF
        if (ciphertext.size <= 1 + ivLen) throw IllegalArgumentException("Truncated IV")
        val iv = ciphertext.copyOfRange(1, 1 + ivLen)
        val encrypted = ciphertext.copyOfRange(1 + ivLen, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getExistingSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.getKey(keyAlias, null) as? SecretKey
            ?: throw IllegalStateException("Keystore key missing")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        gen.init(spec)
        return gen.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "omnidroid_ra_token_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

class RaCredentialStore(
    private val prefs: SharedPreferences,
    private val cipher: RaTokenCipher = AndroidKeystoreRaCipher(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun saveToken(
        username: String,
        token: String,
    ) {
        if (username.isBlank() || token.isBlank()) return
        val payload = json.encodeToString(RaStoredCredentials(username = username, token = token))
        val encrypted = cipher.encrypt(payload.toByteArray(StandardCharsets.UTF_8))
        prefs
            .edit()
            .putString(KEY_ENCRYPTED_CREDS, encodeBase64(encrypted))
            .putString(KEY_DISPLAY_USERNAME, username)
            .commit()
    }

    fun loadToken(): RaStoredCredentials? {
        val b64 = prefs.getString(KEY_ENCRYPTED_CREDS, null) ?: return null
        return try {
            val raw = decodeBase64(b64)
            val decrypted = cipher.decrypt(raw)
            json.decodeFromString<RaStoredCredentials>(String(decrypted, StandardCharsets.UTF_8))
        } catch (t: Throwable) {
            Timber.w(t, "Failed to decrypt RA token cleanly")
            null
        }
    }

    fun getSavedUsername(): String? = prefs.getString(KEY_DISPLAY_USERNAME, null)

    fun hasCredentials(): Boolean =
        !prefs.getString(KEY_ENCRYPTED_CREDS, null).isNullOrBlank() &&
            !prefs.getString(KEY_DISPLAY_USERNAME, null).isNullOrBlank()

    fun clear() {
        prefs
            .edit()
            .remove(KEY_ENCRYPTED_CREDS)
            .remove(KEY_DISPLAY_USERNAME)
            .commit()
    }

    companion object {
        private const val KEY_ENCRYPTED_CREDS = "ra_encrypted_credentials_v1"
        private const val KEY_DISPLAY_USERNAME = "ra_username_hint"

        private fun encodeBase64(data: ByteArray): String =
            try {
                java.util.Base64
                    .getEncoder()
                    .encodeToString(data)
            } catch (_: Throwable) {
                Base64.encodeToString(data, Base64.NO_WRAP)
            }

        private fun decodeBase64(value: String): ByteArray =
            try {
                java.util.Base64
                    .getDecoder()
                    .decode(value)
            } catch (_: Throwable) {
                Base64.decode(value, Base64.DEFAULT)
            }
    }
}
