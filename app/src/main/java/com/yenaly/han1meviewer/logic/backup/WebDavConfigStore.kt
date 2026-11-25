package com.yenaly.han1meviewer.logic.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local credentials, encrypted with a non-exportable key; excluded from app backups. */
object WebDavConfigStore {
    private const val ALIAS = "han1meviewer_webdav_v1"
    private val preferences get() = applicationContext.getSharedPreferences("backup_webdav", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): WebDavConfig? {
        val stored = preferences.getString("credentials", null) ?: return null
        val parts = stored.split(':')
        check(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return Json.decodeFromString<WebDavConfig>(cipher.doFinal(ciphertext).toString(Charsets.UTF_8))
            .also { it.endpoint() }
    }

    @Synchronized
    fun save(config: WebDavConfig) {
        config.endpoint()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(Json.encodeToString(config).toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        check(preferences.edit().putString("credentials", encoded).commit())
    }

    @Synchronized
    fun clear() {
        check(preferences.edit().clear().commit())
        // Also recover from an invalidated or unusable device key on the next save.
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}
