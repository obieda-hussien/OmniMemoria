package com.omnimemoria.ui.vault

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omnimemoria.ui.components.ShimmerBox
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
                            listOf(Color(0xFF1E1C30), Color(0xFF1A1830))
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
                .statusBarsPadding()
                .navigationBarsPadding()
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
                            listOf(Color(0xFF2D26A0), Color(0xFF1E1C30))
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

            Spacer(Modifier.height(36.dp))

            if (state.step == PinStep.LOCKED_OUT) {
                // ── Lockout ring ──────────────────────────────────────────
                val progress = lockout / 30f
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        progress    = { progress },
                        modifier    = Modifier.fillMaxSize(),
                        color       = Color(0xFF8B7FF5),
                        trackColor  = Color(0xFF1E1C30),
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

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
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

@Composable
private fun PinKey(label: String, isDelete: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.88f else 1f,
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
                else Color(0xFF1E1C30)
            )
            .border(
                1.dp,
                if (isDelete) Color.Transparent
                else Color.White.copy(alpha = 0.06f),
                CircleShape
            )
            .clickable {
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

// ─────────────────────────────────────────────────────────────────────────────
// REAL VAULT GALLERY
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RealVaultGallery() {
    val placeholderCount = 7

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060610))
    ) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            contentPadding        = PaddingValues(
                top    = 0.dp,
                bottom = 100.dp,
                start  = 3.dp,
                end    = 3.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            modifier              = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VaultGalleryHeader(isDecoy = false, count = placeholderCount)
            }
            items(placeholderCount) { idx ->
                EncryptedPhotoCell(index = idx)
            }
            item {
                AddToVaultCell()
            }
        }

        VaultSecurityBanner(
            isDecoy  = false,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 90.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DECOY VAULT GALLERY
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DecoyVaultGallery() {
    val placeholderCount = 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            contentPadding        = PaddingValues(
                top    = 0.dp,
                bottom = 100.dp,
                start  = 3.dp,
                end    = 3.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            modifier              = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VaultGalleryHeader(isDecoy = true, count = placeholderCount)
            }
            items(placeholderCount) { idx ->
                NormalDecoyCell(index = idx)
            }
            item { AddToVaultCell() }
        }

        VaultSecurityBanner(
            isDecoy  = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 90.dp)
        )
    }
}

// ── Shared sub-composables ─────────────────────────────────────────────────────

@Composable
private fun VaultGalleryHeader(isDecoy: Boolean, count: Int) {
    val shieldIcon = if (isDecoy) Icons.Outlined.LockOpen else Icons.Outlined.Shield
    val tint       = if (isDecoy) Color(0xFF50C878) else Color(0xFF8B7FF5)
    val badgeText  = if (isDecoy) "Unlocked" else "AES-256 Encrypted"
    val badgeBg    = if (isDecoy) Color(0xFF0D2A14) else Color(0xFF2D26A0).copy(alpha = 0.3f)
    val badgeFg    = if (isDecoy) Color(0xFF50C878) else Color(0xFF8B7FF5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(shieldIcon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Vault",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "$count photo${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(badgeBg)
                .border(1.dp, badgeFg.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                badgeText,
                style      = MaterialTheme.typography.labelSmall,
                color      = badgeFg,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun VaultSecurityBanner(isDecoy: Boolean, modifier: Modifier = Modifier) {
    val bg   = if (isDecoy) Color(0xFF0D2A14) else Color(0xFF1E1C30)
    val icon = if (isDecoy) Icons.Outlined.LockOpen else Icons.Outlined.Shield
    val tint = if (isDecoy) Color(0xFF50C878) else Color(0xFF8B7FF5)
    val text = if (isDecoy) "Showing decoy photos"
               else "AES-256 · Screenshots blocked"

    Box(modifier = modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(1.dp, tint.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EncryptedPhotoCell(index: Int) {
    val tones = listOf(
        Color(0xFF12213A), Color(0xFF20123A), Color(0xFF12221A),
        Color(0xFF22180A), Color(0xFF0A2018), Color(0xFF181820), Color(0xFF220F18)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    listOf(tones[index % tones.size], tones[index % tones.size].copy(alpha = 0.7f))
                )
            )
    ) {
        Icon(
            Icons.Outlined.Shield,
            null,
            tint     = Color(0xFF8B7FF5).copy(alpha = 0.35f),
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 5.dp)
        )
    }
}

@Composable
private fun NormalDecoyCell(index: Int) {
    val tones = listOf(
        Color(0xFF0D3020), Color(0xFF1A0D3A), Color(0xFF3A1A0D)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    listOf(tones[index % tones.size], tones[index % tones.size].copy(alpha = 0.6f))
                )
            )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddToVaultCell() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .background(Color(0xFF1E1C30))
            .combinedClickable(onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B7FF5).copy(alpha = 0.16f))
                    .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    null,
                    tint     = Color(0xFF8B7FF5),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Add",
                style  = MaterialTheme.typography.labelSmall,
                color  = Color(0xFF8B7FF5)
            )
        }
    }
}
