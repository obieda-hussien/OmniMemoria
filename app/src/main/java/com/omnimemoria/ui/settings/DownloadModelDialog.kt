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
        text = { Text("Download the PaddleOCRv5 Arabic + English model bundle (${com.omnimemoria.data.intelligence.PaddleOcrModelStore.SIZE_LABEL}). It recognizes Arabic, English, mixed-script lines and digits offline. The two ONNX model files are checksum-verified before installation. Wi-Fi or mobile data is supported; your photos stay on your device.") },
        confirmButton = {
            TextButton(onClick = { onRequest(model.id); onDismiss() }) { Text("Download") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}
