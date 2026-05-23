package com.omnimemoria.data.vault

import android.util.Log
import com.omnimemoria.data.preferences.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

@Singleton
class VaultRepository @Inject constructor(
    private val appPreferences: AppPreferences
) {
    fun isVaultSetupFlow(): Flow<Boolean> =
        appPreferences.getString(AppPreferences.PreferencesKeys.VAULT_PIN_HASH)
            .map { it.isNotBlank() }

    fun isVaultSetup(): Boolean = runBlocking { isVaultSetupFlow().first() }

    suspend fun savePinHash(hash: String) {
        appPreferences.setString(AppPreferences.PreferencesKeys.VAULT_PIN_HASH, hash)
    }

    suspend fun verifyPinHash(hash: String): Boolean {
        return appPreferences.getString(AppPreferences.PreferencesKeys.VAULT_PIN_HASH)
            .map { it == hash }
            .first()
    }

    fun moveToVault(mediaStoreId: Long) {
        Log.d("VaultRepository", "moveToVault stub mediaStoreId=$mediaStoreId")
    }

    fun removeFromVault(mediaStoreId: Long) {
        Log.d("VaultRepository", "removeFromVault stub mediaStoreId=$mediaStoreId")
    }
}
