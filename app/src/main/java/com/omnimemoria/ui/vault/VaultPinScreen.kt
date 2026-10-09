package com.omnimemoria.ui.vault

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.components.ShimmerBox
import com.omnimemoria.ui.home.HomeTopOverlaySpacing
import kotlinx.coroutines.delay

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun VaultTabScreen(
    onGoToSettings: () -> Unit,
    viewModel: VaultTabViewModel = hiltViewModel()
) {
    val enabled by viewModel.vaultEnabled.collectAsState()

    AnimatedContent(
        targetState  = enabled,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label        = "vault_entry"
    ) { isEnabled ->
        if (!isEnabled) VaultDisabledState(onGoToSettings = onGoToSettings)
        else VaultPinScreen()
    }
}

// ── Vault disabled ────────────────────────────────────────────────────────────

@Composable
private fun VaultDisabledState(onGoToSettings: () -> Unit) {
    val floatY by rememberInfiniteTransition(label = "float")
        .animateFloat(
            initialValue  = 0f,
            targetValue   = -7f,
            animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label         = "float_y"
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated lock icon
            Box(
                modifier = Modifier
                    .offset(y = floatY.dp)
                    .size(88.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.primaryContainer)
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF8B7FF5).copy(alpha = 0.2f),
                        RoundedCornerShape(26.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    null,
                    tint     = Color(0xFF8B7FF5),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Vault is disabled",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Enable the Vault in Settings to encrypt and protect your private photos with AES-256.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            // CTA button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF5548D9), Color(0xFF8B7FF5))
                        )
                    )
                    .clickable(onClick = onGoToSettings)
                    .padding(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Settings,
                        null,
                        tint     = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Go to Settings",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        style      = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VAULT PIN SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun VaultPinScreen(
    viewModel: VaultPinViewModel = hiltViewModel()
) {
    val haptic       = LocalHapticFeedback.current
    val setup        by viewModel.vaultSetup.collectAsState()
    val state        by viewModel.pinState.collectAsState()
    val attemptsLeft by viewModel.attemptsLeft.collectAsState()
    val lockout      by viewModel.lockoutRemainingSeconds.collectAsState()
    val unlockedAs   by viewModel.unlockedAs.collectAsState()

    // Route to gallery after unlock
    when (unlockedAs) {
        UnlockType.REAL  -> { RealVaultGallery(); return }
        UnlockType.DECOY -> { DecoyVaultGallery(); return }
        null             -> { /* show PIN */ }
    }

    // Shake on error
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(state.errorTick) {
        if (state.errorTick <= 0) return@LaunchedEffect
        shakeX.animateTo(1f, keyframes {
            durationMillis = 420
            0f  at 0
            -14f at 55
            14f  at 110
            -12f at 165
            12f  at 220
            -8f  at 280
            8f   at 330
            0f   at 420
        })
        shakeX.snapTo(0f)
        repeat(3) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(90)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle indigo glow in background
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-80).dp)
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF8B7FF5).copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // ── Vault icon ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant)
                        )
                    )
                    .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.3f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    null,
                    tint     = Color(0xFF8B7FF5),
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(22.dp))

            // ── Title ────────────────────────────────────────────────────
            Text(
                text = when {
                    !setup && state.step == PinStep.CONFIRM -> "Confirm PIN"
                    !setup -> "Set Vault PIN"
                    else   -> "Enter Vault PIN"
                },
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    state.step == PinStep.CONFIRM    -> "Re-enter your 4-digit PIN"
                    state.step == PinStep.LOCKED_OUT -> "Too many attempts — wait:"
                    !setup -> "Choose a PIN to protect your private photos"
                    else   -> "Enter your 4-digit PIN"
                },
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "PIN preview only · encrypted photo storage isn't available yet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (state.step == PinStep.LOCKED_OUT) {
                // ── Lockout ring ──────────────────────────────────────────
                val progress = lockout / 30f
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        progress    = { progress },
                        modifier    = Modifier.fillMaxSize(),
                        color       = Color(0xFF8B7FF5),
                        trackColor  = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 5.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.LockClock,
                            null,
                            tint     = Color(0xFF8B7FF5),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${lockout}s",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                // ── PIN dots ───────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier              = Modifier.offset(x = shakeX.value.dp)
                ) {
                    repeat(4) { i ->
                        val filled = state.digits[i] != null
                        val dotScale by animateFloatAsState(
                            targetValue   = if (filled) 1f else 0.8f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label         = "dot_$i"
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .scale(dotScale)
                                .clip(CircleShape)
                                .background(
                                    if (filled)
                                        Brush.radialGradient(
                                            listOf(Color(0xFFA89CF7), Color(0xFF8B7FF5))
                                        )
                                    else Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
                                )
                                .border(
                                    2.dp,
                                    if (filled) Color(0xFF8B7FF5)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    CircleShape
                                )
                        )
                    }
                }

                // ── Status message ────────────────────────────────────────
                AnimatedVisibility(
                    visible = state.message != null,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(14.dp))
                        val isError = state.message?.contains("Incorrect") == true ||
                            state.message?.contains("match") == true
                        Text(
                            state.message ?: "",
                            style     = MaterialTheme.typography.bodySmall,
                            color     = if (isError) Color(0xFFFF6B6B) else Color(0xFF8B7FF5),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Attempts remaining ────────────────────────────────────
                if (setup && state.step != PinStep.LOCKED_OUT && attemptsLeft < 5) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$attemptsLeft attempt${if (attemptsLeft != 1) "s" else ""} remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (attemptsLeft <= 2) Color(0xFFFF6B6B)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(40.dp))

                // ── PIN pad ───────────────────────────────────────────────
                PinPad(
                    onDigit  = viewModel::enterDigit,
                    onDelete = viewModel::deleteDigit
                )
            }

            Spacer(Modifier.weight(1.2f))
        }
    }
}

// ── PIN pad ────────────────────────────────────────────────────────────────────

@Composable
private fun PinPad(onDigit: (Int) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1","2","3"),
        listOf("4","5","6"),
        listOf("7","8","9"),
        listOf("","0","⌫")
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else {
                        val isDelete = key == "⌫"
                        PinKey(
                            label    = key,
                            isDelete = isDelete,
                            onClick  = { if (isDelete) onDelete() else onDigit(key.toInt()) }
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, isDelete: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label         = "key_scale"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (isDelete) Color.Transparent
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (isDelete) Color.Transparent
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                CircleShape
            )
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            style      = if (isDelete) MaterialTheme.typography.titleLarge
                         else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color      = if (isDelete) Color(0xFF8B7FF5)
                         else MaterialTheme.colorScheme.onBackground
        )
    }
}

// ── Vault storage placeholder ─────────────────────────────────────────────────
// There is currently no encrypted media repository; never fabricate protected
// thumbnails, a file count or an "AES-256 encrypted" badge in the UI.

@Composable
fun RealVaultGallery() { VaultStorageUnavailable() }

@Composable
fun DecoyVaultGallery() { VaultStorageUnavailable() }

@Composable
private fun VaultStorageUnavailable() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                "Private gallery is not ready",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "PIN verification is only a preview. Encrypted photo storage and import are not implemented. Your existing gallery photos haven't been moved or hidden.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
