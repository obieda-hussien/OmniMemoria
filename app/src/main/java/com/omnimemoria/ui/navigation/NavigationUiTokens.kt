package com.omnimemoria.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app navigation surface follows the current light/dark theme.
val NavigationSurfaceColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)

// Near-opaque theme surfaces keep controls readable over any media content.
val MediaChromeSurfaceColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
