package com.omnimemoria.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

@Composable
fun VaultTabScreen(
    onGoToSettings: () -> Unit,
    viewModel: VaultTabViewModel = hiltViewModel()
) {
    val enabled by viewModel.vaultEnabled.collectAsState()
    if (!enabled) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Vault feature is disabled. Enable in Settings.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGoToSettings) { Text("Go to Settings") }
        }
    } else {
        VaultPinScreen()
    }
}

@Composable
fun VaultPinScreen(
    viewModel: VaultPinViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val setup by viewModel.vaultSetup.collectAsState()
    val state by viewModel.pinState.collectAsState()
    val attemptsLeft by viewModel.attemptsLeft.collectAsState()
    val lockout by viewModel.lockoutRemainingSeconds.collectAsState()

    if (state.success) {
        VaultGalleryScreen()
        return
    }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.errorTick) {
        if (state.errorTick <= 0) return@LaunchedEffect
        shake.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = 400
                0f at 0
                -12f at 50
                12f at 100
                -12f at 150
                12f at 200
                -12f at 250
                12f at 300
                0f at 400
            }
        )
        shake.snapTo(0f)
        if (setup) {
            repeat(3) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(80)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            if (!setup) {
                if (state.step == PinStep.CONFIRM) "Confirm Vault PIN" else "Set Vault PIN"
            } else "Enter Vault PIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                state.step == PinStep.CONFIRM -> "Re-enter your PIN"
                !setup -> "Choose a 4-digit PIN to protect your private photos"
                state.step == PinStep.LOCKED_OUT -> "Too many attempts. Try again in 30 seconds"
                else -> "Enter your 4-digit PIN"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.offset(x = shake.value.dp)
        ) {
            repeat(4) { i ->
                val filled = state.digits[i] != null
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
                            CircleShape
                        )
                        .then(
                            if (!filled) Modifier.background(Color.Transparent) else Modifier
                        )
                ) {
                    if (!filled) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Transparent, CircleShape)
                        )
                    }
                }
            }
        }

        if (state.message != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                state.message!!,
                color = if (state.message!!.contains("Incorrect") || state.message!!.contains("match")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        if (setup && state.step != PinStep.LOCKED_OUT) {
            Spacer(Modifier.height(6.dp))
            Text("Attempts left: $attemptsLeft", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(28.dp))

        if (state.step == PinStep.LOCKED_OUT) {
            val progress = lockout / 30f
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize())
                Icon(Icons.Outlined.LockClock, contentDescription = null)
            }
            Spacer(Modifier.height(8.dp))
            Text("${lockout}s")
        } else {
            PinPad(
                onDigit = viewModel::enterDigit,
                onDelete = viewModel::deleteDigit
            )
        }
    }
}

@Composable
private fun PinPad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .size(64.dp)
                            .clickable {
                                when (key) {
                                    "⌫" -> onDelete()
                                    "*" -> Unit
                                    else -> onDigit(key.toInt())
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}
