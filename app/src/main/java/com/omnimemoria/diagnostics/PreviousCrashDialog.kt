package com.omnimemoria.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PreviousCrashDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { report = withContext(Dispatchers.IO) { CrashReport.read(context) } }
    val text = report ?: return
    AlertDialog(
        onDismissRequest = { report = null },
        title = { Text("The app closed unexpectedly") },
        text = {
            if (expanded) SelectionContainer {
                Text(text, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
            } else Text("A diagnostic report was saved on this device. Copy it to help identify the crash. Nothing is sent automatically.")
        },
        confirmButton = { TextButton(onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("OmniMemoria crash report", text))
            expanded = true
        }) { Text("Copy report") } },
        dismissButton = { TextButton(onClick = { CrashReport.clear(context); report = null }) { Text("Dismiss") } }
    )
}
