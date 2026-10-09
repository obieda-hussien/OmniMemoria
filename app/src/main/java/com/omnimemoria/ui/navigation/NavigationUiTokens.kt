package com.omnimemoria.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app navigation surface follows the current light/dark theme.
val NavigationSurfaceColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)

// On top of a photo or video, controls intentionally stay dark for legibility.
val MediaChromeSurfaceColor = Color(0xFF141220).copy(alpha = 0.90f)
