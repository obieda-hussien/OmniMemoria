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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.title) },
        text = { Text("Save a request for this offline model. Model downloads and local processing will be available in a future update. No model files are downloaded in this version; size will be shown when a model is available.") },
        confirmButton = {
            TextButton(onClick = { onRequest(model.id); onDismiss() }) { Text("Save request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}
