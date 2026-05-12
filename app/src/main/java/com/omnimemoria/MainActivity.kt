package com.omnimemoria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.omnimemoria.ui.navigation.AppNavGraph
import com.omnimemoria.ui.theme.OmniMemoriaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniMemoriaTheme {
                AppNavGraph(isVaultUnlocked = false)
            }
        }
    }
}
