package com.omnimemoria.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation

private const val VaultGraphRoute = "vault_graph"
private const val VaultLockedRoute = "vault_locked"

fun NavGraphBuilder.vaultNavGraph() {
    navigation(
        route = VaultGraphRoute,
        startDestination = VaultLockedRoute
    ) {
        composable(VaultLockedRoute) {
            VaultLockedScreen()
        }
    }
}

@Composable
fun VaultLockedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Vault is locked")
    }
}
