package com.omnimemoria.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal val LightColors = lightColorScheme(
    primary              = PrimaryLight,
    onPrimary            = OnPrimaryLight,
    primaryContainer     = PrimaryContainerLight,
    onPrimaryContainer   = OnPrimaryContainerLight,
    secondary            = SecondaryLight,
    onSecondary          = OnSecondaryLight,
    secondaryContainer   = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary             = TertiaryLight,
    onTertiary           = OnTertiaryLight,
    tertiaryContainer    = TertiaryContainerLight,
    onTertiaryContainer  = OnTertiaryContainerLight,
    onBackground         = Color(0xFF1D1B24),
    onSurface            = Color(0xFF1D1B24),
    surfaceTint          = PrimaryLight,
    surfaceDim           = Color(0xFFDDD8E5),
    surfaceBright        = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow  = Color(0xFFF4F0FA),
    surfaceContainer     = Color(0xFFEEEBF5),
    surfaceContainerHigh = Color(0xFFE8E5F0),
    surfaceContainerHighest = Color(0xFFE2DEEA),
    outline              = Color(0xFF756F82),
    outlineVariant       = Color(0xFFCEC7DA),
    error                = Color(0xFFBA1A1A),
    onError              = Color.White,
    errorContainer       = Color(0xFFFFDAD6),
    onErrorContainer     = Color(0xFF410002),
    inverseSurface       = Color(0xFF302D38),
    inverseOnSurface     = Color(0xFFF4F0FA),
    inversePrimary       = Color(0xFFC4BDFF),
    background           = BackgroundLight,
    surface              = SurfaceLight,
    surfaceVariant       = SurfaceVariantLight,
    onSurfaceVariant     = OnSurfaceVariantLight,
)

internal val DarkColors = darkColorScheme(
    primary              = PrimaryDark,
    onPrimary            = OnPrimaryDark,
    primaryContainer     = PrimaryContainerDark,
    onPrimaryContainer   = OnPrimaryContainerDark,
    secondary            = SecondaryDark,
    onSecondary          = OnSecondaryDark,
    secondaryContainer   = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary             = TertiaryDark,
    onTertiary           = OnTertiaryDark,
    tertiaryContainer    = TertiaryContainerDark,
    onTertiaryContainer  = OnTertiaryContainerDark,
    onBackground         = Color(0xFFECE6F2),
    onSurface            = Color(0xFFECE6F2),
    surfaceTint          = PrimaryDark,
    surfaceDim           = Color(0xFF100E17),
    surfaceBright        = Color(0xFF3A3544),
    surfaceContainerLowest = Color(0xFF0C0A12),
    surfaceContainerLow  = Color(0xFF191620),
    surfaceContainer     = Color(0xFF201C28),
    surfaceContainerHigh = Color(0xFF2A2533),
    surfaceContainerHighest = Color(0xFF35303F),
    outline              = Color(0xFF9B93A9),
    outlineVariant       = Color(0xFF4B4458),
    error                = Color(0xFFFFB4AB),
    onError              = Color(0xFF690005),
    errorContainer       = Color(0xFF93000A),
    onErrorContainer     = Color(0xFFFFDAD6),
    inverseSurface       = Color(0xFFECE6F2),
    inverseOnSurface     = Color(0xFF302D38),
    inversePrimary       = PrimaryLight,
    background           = BackgroundDark,
    surface              = SurfaceDark,
    surfaceVariant       = SurfaceVariantDark,
    onSurfaceVariant     = OnSurfaceVariantDark,
)

@Composable
fun OmniMemoriaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled — enforcing Lumina brand identity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        shapes      = AppShapes,
        content = { Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background,
            contentColor = colorScheme.onBackground, content = content) }
    )
}
