package com.omnimemoria.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun verify(scheme: ColorScheme) {
        val pairs = listOf(scheme.onBackground to scheme.background, scheme.onSurface to scheme.surface,
            scheme.onSurface to scheme.surfaceContainer, scheme.onSurfaceVariant to scheme.surfaceVariant,
            scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer,
            scheme.onSecondary to scheme.secondary, scheme.onSecondaryContainer to scheme.secondaryContainer,
            scheme.onTertiary to scheme.tertiary, scheme.onTertiaryContainer to scheme.tertiaryContainer,
            scheme.onError to scheme.error, scheme.onErrorContainer to scheme.errorContainer)
        pairs.forEach { (text: Color, background: Color) ->
            val a = text.luminance(); val b = background.luminance()
            val ratio = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
            assertTrue("Text contrast is $ratio for $text on $background", ratio >= 4.5)
        }
    }
    @Test fun lightTextRolesRemainReadable() = verify(LightColors)
    @Test fun darkTextRolesRemainReadable() = verify(DarkColors)
}
