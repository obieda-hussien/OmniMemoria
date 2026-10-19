package com.omnimemoria.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.omnimemoria.domain.flags.OfflineModel

@Composable
fun DownloadModelDialog(
    modelName: String,
    onDismiss: () -> Unit,
    onRequest: (String) -> Unit
) {
    val model = OfflineModel.fromId(modelName) ?: return
    if (model != OfflineModel.TESSERACT_ARABIC) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.title) },
        text = { Text("Download the Arabic OCR model (1.4 MB) from the Tesseract project. The file is checked before installation. Photos stay on your device. Gemini Nano is not needed.") },
        confirmButton = {
            TextButton(onClick = { onRequest(model.id); onDismiss() }) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}
