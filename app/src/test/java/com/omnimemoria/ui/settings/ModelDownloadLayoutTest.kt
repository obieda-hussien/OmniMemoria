package com.omnimemoria.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
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
    private lateinit var renderedView: View

    private fun show(dark: Boolean, direction: LayoutDirection, phase: ModelPhase) {
        compose.setContent {
            val view = LocalView.current
            SideEffect { renderedView = view }
            CompositionLocalProvider(LocalLayoutDirection provides direction,
                LocalDensity provides Density(1f, fontScale = 1.5f)) {
                OmniMemoriaTheme(darkTheme = dark) {
                    Box(Modifier.width(320.dp)) {
                        ModelDownloadRow("Arabic + English OCR (PaddleOCRv5)", Icons.Outlined.Language,
                            ModelStatus(phase, "Queued · Wi-Fi or mobile data required"), {}, {})
                    }
                }
            }
        }
    }
    private fun savePreview(name: String) {
        // PixelCopy requires a real window compositor. Draw the native-rendered
        // view directly when running on Robolectric instead of timing out.
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(renderedView.width, renderedView.height, Bitmap.Config.ARGB_8888)
            renderedView.draw(Canvas(bitmap))
            val directory = File("build/outputs/ui-previews").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun darkRtlWithLargeTextDoesNotOverlapStatusAndActions() {
        show(true, LayoutDirection.Rtl, ModelPhase.QUEUED)
        val title = compose.onNodeWithText("Arabic + English OCR (PaddleOCRv5)").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithText("Queued · Wi-Fi or mobile data required").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("Download ~13 MB").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= status.top)
        assertTrue(status.bottom <= button.top)
        compose.onNodeWithText("Cancel download").assertIsDisplayed()
        savePreview("model-dark-rtl-large-text")
    }
    @Test fun lightPlannedModelHasNoMisleadingDownloadButton() {
        show(false, LayoutDirection.Ltr, ModelPhase.PLANNED)
        compose.onNodeWithText("Tesseract Arabic").assertIsDisplayed()
        compose.onNodeWithText("Download ~13 MB").assertDoesNotExist()
        savePreview("model-light-ltr-planned")
    }
    @Test fun installedStandardModelOffersTheOptionalAccuracyUpgrade() {
        compose.setContent { OmniMemoriaTheme {
            ModelDownloadRow("Tesseract Arabic", Icons.Outlined.Language,
                ModelStatus(ModelPhase.INSTALLED, "Legacy Arabic model installed · upgrade to the multilingual PaddleOCRv5 model", canUpgrade = true), {}, {})
        } }
        compose.onNodeWithText("Upgrade · ~13 MB").assertIsDisplayed()
        compose.onNodeWithText("Cancel download").assertDoesNotExist()
    }
}
