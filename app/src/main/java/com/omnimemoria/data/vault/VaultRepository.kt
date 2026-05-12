package com.omnimemoria.data.vault

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "omnimemoria_settings")

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isVaultSetup(): Boolean = runBlocking {
        val preferences = context.dataStore.data.first()
        preferences
            .asMap()
            .any { entry -> entry.key.name == "vault_pin_hash" && (entry.value as? String).isNullOrBlank().not() }
    }

    fun moveToVault(mediaStoreId: Long) {
        Log.d("VaultRepository", "moveToVault stub mediaStoreId=$mediaStoreId")
    }

    fun removeFromVault(mediaStoreId: Long) {
        Log.d("VaultRepository", "removeFromVault stub mediaStoreId=$mediaStoreId")
    }
}
