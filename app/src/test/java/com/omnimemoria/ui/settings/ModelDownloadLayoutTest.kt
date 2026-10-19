package com.omnimemoria.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnimemoria.ui.theme.OmniMemoriaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelDownloadLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun show(dark: Boolean, direction: LayoutDirection, phase: ModelPhase) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction,
                LocalDensity provides Density(1f, fontScale = 1.5f)) {
                OmniMemoriaTheme(darkTheme = dark) {
                    Box(Modifier.width(320.dp)) {
                        ModelDownloadRow("Tesseract Arabic", Icons.Outlined.Language,
                            ModelStatus(phase, "Queued · internet access required"), {}, {})
                    }
                }
            }
        }
    }
    private fun savePreview(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/outputs/ui-previews").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun darkRtlWithLargeTextDoesNotOverlapStatusAndActions() {
        show(true, LayoutDirection.Rtl, ModelPhase.QUEUED)
        val title = compose.onNodeWithText("Tesseract Arabic").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithText("Queued · internet access required").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Download 1.4 MB").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= status.top)
        assertTrue(status.bottom <= button.top)
        compose.onNodeWithText("Cancel download").assertIsDisplayed()
        savePreview("model-dark-rtl-large-text")
    }
    @Test fun lightPlannedModelHasNoMisleadingDownloadButton() {
        show(false, LayoutDirection.Ltr, ModelPhase.PLANNED)
        compose.onNodeWithText("Tesseract Arabic").assertIsDisplayed()
        compose.onNodeWithText("Download 1.4 MB").assertDoesNotExist()
        savePreview("model-light-ltr-planned")
    }
}
