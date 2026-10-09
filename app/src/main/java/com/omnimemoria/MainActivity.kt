package com.omnimemoria

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat

import com.omnimemoria.ui.navigation.AppNavGraph
import com.omnimemoria.ui.theme.OmniMemoriaTheme
import com.omnimemoria.ui.components.PermissionsScreen
import com.omnimemoria.data.worker.OnThisDayWorker
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Consuming a reminder increments observable navigation state, even on an existing activity.
    private var reminderRequests by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(OnThisDayWorker.EXTRA_OPEN_ON_THIS_DAY, false)) {
            reminderRequests++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(OnThisDayWorker.EXTRA_OPEN_ON_THIS_DAY, false) == true) {
            reminderRequests++
        }

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
            val darkTheme = isSystemInDarkTheme()
            // Update system bar icon contrast every time Android changes its UI mode.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            OmniMemoriaTheme(darkTheme = darkTheme) {
                var permissionsGranted by remember { mutableStateOf(false) }

                if (permissionsGranted) {
                    AppNavGraph(externalUri = externalUri, intentType = intentType, reminderRequests = reminderRequests)
                } else {
                    PermissionsScreen(onPermissionsGranted = {
                        permissionsGranted = true
                    })
                }
            }
        }
    }

}
