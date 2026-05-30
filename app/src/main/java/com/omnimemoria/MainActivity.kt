package com.omnimemoria

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import com.omnimemoria.ui.navigation.AppNavGraph
import com.omnimemoria.ui.theme.OmniMemoriaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        var externalUri: String? = null
        val intentAction = intent?.action
        val intentType = intent?.type

        if (Intent.ACTION_VIEW == intentAction && intent.data != null) {
            externalUri = intent.data.toString()
        } else if (Intent.ACTION_SEND == intentAction && intentType != null) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            }
            if (uri != null) {
                externalUri = uri.toString()
            }
        }

        setContent {
            OmniMemoriaTheme {
                // تم إزالة isVaultUnlocked = false
                AppNavGraph(externalUri = externalUri, intentType = intentType)
            }
        }
    }

}
