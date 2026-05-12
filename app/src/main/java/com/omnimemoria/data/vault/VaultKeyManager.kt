package com.omnimemoria.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

class VaultKeyManager @Inject constructor() {
    private val keyAlias = "omnimemoria_vault_key"
    private val keystoreProvider = "AndroidKeyStore"

    fun getOrCreateSecretKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(keystoreProvider).apply { load(null) }
        keyStore.getKey(keyAlias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keystoreProvider)
        val parameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    fun encrypt(bytes: ByteArray): EncryptedData {
        return EncryptedData(cipherText = bytes, iv = ByteArray(0))
    }

    fun decrypt(data: EncryptedData): ByteArray {
        return data.cipherText
    }
}

data class EncryptedData(
    val cipherText: ByteArray,
    val iv: ByteArray
)
