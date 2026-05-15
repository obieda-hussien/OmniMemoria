package com.omnimemoria.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.omnimemoria.data.worker.ModelDownloadWorker

@Composable
fun DownloadModelDialog(
    modelName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Download $modelName") },
        text = { Text(text = "Size: ~30MB — WiFi Recommended") },
        confirmButton = {
            TextButton(
                onClick = {
                    val inputData = Data.Builder()
                        .putString(ModelDownloadWorker.MODEL_NAME, modelName)
                        .build()
                    val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                        .setInputData(inputData)
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "model_download_$modelName",
                        ExistingWorkPolicy.KEEP,
                        request
                    )
                    onDismiss()
                }
            ) {
                Text(text = "Download Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Later")
            }
        }
    )
}
