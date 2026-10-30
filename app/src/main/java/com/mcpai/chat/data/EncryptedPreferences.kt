package com.mcpai.chat.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.Key
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * Thin wrapper around [SharedPreferences] that encrypts values with an
 * AES-256-GCM key stored in the Android Keystore.
 *
 * The key is generated once and persisted in the keystore under
 * [ALIAS]. On every read/write, the value is encrypted/decrypted with
 * a random 96-bit IV, so identical plaintexts produce different
 * ciphertexts (semantic security).
 *
 * Requires API 23+ (Android Keystore). This app targets minSdk 26.
 */
/** Minimal encrypted key-value store used by [Settings]. Implemented by [EncryptedPreferences]. */
interface EncryptedStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
}

class EncryptedPreferences private constructor(
    private val delegate: SharedPreferences,
    private val cipher: Cipher,
) : EncryptedStore {

    companion object {
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
        private const val ALIAS = "mcp_ai_chat_master_key"

        fun create(context: Context): EncryptedPreferences {
            ensureKeyExists(context)
            val delegate = context.getSharedPreferences("mcp_ai_chat_encrypted", Context.MODE_PRIVATE)
            val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" +
                KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE)
            return EncryptedPreferences(delegate, cipher)
        }

        private fun ensureKeyExists(context: Context) {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (ks.containsAlias(ALIAS)) return

            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            generator.init(KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).apply {
                setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                setKeySize(256)
                setRandomizedEncryptionRequired(false)
            }.build())
            generator.generateKey()
        }
    }

    override fun getString(key: String, default: String?): String? {
        val raw = delegate.getString(key, null) ?: return default
        return runCatching {
            val (iv, ciphertext) = split(raw)
            cipher.init(Cipher.DECRYPT_MODE, keyStoreKey,
                GCMParameterSpec(TAG_SIZE_BITS, iv))
            String(cipher.doFinal(ciphertext), charset = Charsets.UTF_8)
        }.getOrNull() ?: default
    }

    override fun putString(key: String, value: String) {
        // Let the Keystore generate the IV (required when randomizedEncryptionRequired is true).
        // This works with both old keys (randomizedEncryptionRequired=true) and new keys.
        cipher.init(Cipher.ENCRYPT_MODE, keyStoreKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        delegate.edit().putString(key, encode(iv + ciphertext)).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        getString(key, null)?.let { it == "1" } ?: default

    override fun putBoolean(key: String, value: Boolean) {
        putString(key, if (value) "1" else "0")
    }

    override fun getInt(key: String, default: Int): Int =
        getString(key, null)?.let { it.toIntOrNull() } ?: default

    override fun putInt(key: String, value: Int) {
        putString(key, value.toString())
    }

    fun getFloat(key: String, default: Float): Float =
        getString(key, null)?.let { it.toFloatOrNull() } ?: default

    fun putFloat(key: String, value: Float) {
        putString(key, value.toString())
    }

    fun edit(): SharedPreferences.Editor = delegate.edit()

    /** Base64-encode without padding (URL-safe). */
    private fun encode(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun split(encoded: String): Pair<ByteArray, ByteArray> {
        val raw = java.util.Base64.getUrlDecoder().decode(encoded)
        return Pair(raw.copyOfRange(0, IV_SIZE_BYTES),
            raw.copyOfRange(IV_SIZE_BYTES, raw.size))
    }

    private val keyStoreKey: Key
        get() {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            return ks.getKey(ALIAS, null) as Key
        }
}
