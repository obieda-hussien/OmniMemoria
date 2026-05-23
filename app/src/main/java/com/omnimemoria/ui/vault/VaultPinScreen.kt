package com.omnimemoria.ui.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omnimemoria.ui.components.ShimmerBox
import kotlinx.coroutines.delay

// ── Entry point ───────────────────────────────────────────────────────────────

@Composable
fun VaultTabScreen(
    onGoToSettings: () -> Unit,
    viewModel: VaultTabViewModel = hiltViewModel()
) {
    val enabled by viewModel.vaultEnabled.collectAsState()

    if (!enabled) {
        VaultDisabledState(onGoToSettings = onGoToSettings)
    } else {
        VaultPinScreen()
    }
}

// ── Vault disabled state ───────────────────────────────────────────────────────

@Composable
private fun VaultDisabledState(onGoToSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E1C30)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Vault is disabled",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enable the Vault feature in Settings to protect your private photos with AES-256 encryption.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onGoToSettings)
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Go to Settings", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PIN SCREEN
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

    // Routed to correct gallery after unlock
    when (unlockedAs) {
        UnlockType.REAL  -> { RealVaultGallery(); return }
        UnlockType.DECOY -> { DecoyVaultGallery(); return }
        null             -> { /* show PIN screen */ }
    }

    // Shake on error
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(state.errorTick) {
        if (state.errorTick <= 0) return@LaunchedEffect
        shakeX.animateTo(1f, keyframes {
            durationMillis = 400
            0f at 0; -14f at 50; 14f at 100; -12f at 150
            12f at 200; -8f at 260; 8f at 310; 0f at 400
        })
        shakeX.snapTo(0f)
        repeat(3) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); delay(80) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        // Lock icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1C30)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(20.dp))

        // Title & subtitle
        Text(
            text = when {
                !setup && state.step == PinStep.CONFIRM -> "Confirm Vault PIN"
                !setup -> "Set Vault PIN"
                else   -> "Enter Vault PIN"
            },
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                state.step == PinStep.CONFIRM   -> "Re-enter your 4-digit PIN"
                state.step == PinStep.LOCKED_OUT -> "Too many attempts — try again in:"
                !setup -> "Choose a PIN to protect your private photos"
                else   -> "Enter your 4-digit PIN"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        if (state.step == PinStep.LOCKED_OUT) {
            // Countdown ring
            val progress = lockout / 30f
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                CircularProgressIndicator(
                    progress    = { progress },
                    modifier    = Modifier.fillMaxSize(),
                    color       = MaterialTheme.colorScheme.primary,
                    strokeWidth = 5.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.LockClock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text("${lockout}s", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // PIN dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shakeX.value.dp)
            ) {
                repeat(4) { i ->
                    val filled = state.digits[i] != null
                    val dotScale by animateFloatAsState(
                        targetValue   = if (filled) 1f else 0.85f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label         = "dot_$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .scale(dotScale)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }
            }

            // Error / info message
            AnimatedVisibility(visible = state.message != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    state.message ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.message?.contains("Incorrect") == true ||
                                state.message?.contains("match") == true)
                               MaterialTheme.colorScheme.error
                           else
                               MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            if (setup && state.step != PinStep.LOCKED_OUT && attemptsLeft < 5) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$attemptsLeft attempts remaining",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (attemptsLeft <= 2) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(36.dp))

            // PIN pad
            PinPad(
                onDigit  = viewModel::enterDigit,
                onDelete = viewModel::deleteDigit
            )
        }
    }
}

@Composable
private fun PinPad(onDigit: (Int) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1","2","3"),
        listOf("4","5","6"),
        listOf("7","8","9"),
        listOf("","0","⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(72.dp))
                    } else {
                        val isDelete = key == "⌫"
                        Surface(
                            shape = CircleShape,
                            color = if (isDelete) Color.Transparent
                                    else Color(0xFF1E1C30),
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    if (isDelete) onDelete() else onDigit(key.toInt())
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text  = key,
                                    style = if (isDelete) MaterialTheme.typography.titleMedium
                                            else MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDelete) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REAL VAULT GALLERY
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RealVaultGallery() {
    // Placeholder grid — Phase 5 wires real encrypted photos
    val placeholderCount = 7

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060610))    // extra dark for vault
    ) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            contentPadding        = PaddingValues(
                top    = 0.dp,
                bottom = 100.dp,
                start  = 2.dp,
                end    = 2.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement   = Arrangement.spacedBy(2.dp),
            modifier              = Modifier.fillMaxSize()
        ) {
            // Header
            item(span = { GridItemSpan(maxLineSpan) }) {
                VaultHeader(isDecoy = false, count = placeholderCount)
            }

            // Encrypted photo cells (placeholder)
            items(placeholderCount) { idx ->
                EncryptedPhotoCell(index = idx)
            }

            // Add photos cell
            item {
                AddToVaultCell()
            }
        }

        // AES badge bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)   // above bottom nav pill
        ) {
            VaultSecurityBanner(isDecoy = false)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DECOY VAULT GALLERY
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DecoyVaultGallery() {
    // Shows normal photos tagged as decoy items
    val placeholderCount = 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)   // same as rest of app — no suspicious darkness
    ) {
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            contentPadding        = PaddingValues(
                top    = 0.dp,
                bottom = 100.dp,
                start  = 2.dp,
                end    = 2.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement   = Arrangement.spacedBy(2.dp),
            modifier              = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                VaultHeader(isDecoy = true, count = placeholderCount)
            }

            // Normal-looking photo stubs
            items(placeholderCount) { idx ->
                NormalDecoyPhotoCell(index = idx)
            }

            item {
                AddToVaultCell()
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
        ) {
            VaultSecurityBanner(isDecoy = true)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VaultHeader(isDecoy: Boolean, count: Int) {
    val lockIcon  = if (isDecoy) Icons.Outlined.LockOpen else Icons.Outlined.Shield
    val lockTint  = if (isDecoy) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    val badgeBg   = if (isDecoy)
        Color(0xFF1A3A1A)
    else
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    val badgeText = if (isDecoy) "Unlocked" else "Secured · AES-256"
    val badgeFg   = if (isDecoy) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(lockIcon, null, tint = lockTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
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
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeFg, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun VaultSecurityBanner(isDecoy: Boolean) {
    val bg   = if (isDecoy) Color(0xFF1A3A1A) else Color(0xFF1E1C30)
    val icon = if (isDecoy) Icons.Outlined.LockOpen else Icons.Outlined.Shield
    val tint = if (isDecoy) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    val text = if (isDecoy)
        "Showing hand-picked photos"
    else
        "AES-256 encrypted · Screenshots blocked"

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
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

// Encrypted photo cell (shows lock badge)
@Composable
private fun EncryptedPhotoCell(index: Int) {
    val tones = listOf(
        Color(0xFF1a3050), Color(0xFF301a50), Color(0xFF1a3020),
        Color(0xFF302010), Color(0xFF103020), Color(0xFF202030), Color(0xFF301520)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(tones[index % tones.size])
    ) {
        // Tiny lock badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Shield, null, tint = Color.White, modifier = Modifier.size(10.dp))
        }
    }
}

// Normal-looking cell for decoy vault
@Composable
private fun NormalDecoyPhotoCell(index: Int) {
    val tones = listOf(Color(0xFF1a5c3a), Color(0xFF3a1a5c), Color(0xFF5c3a1a))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(listOf(tones[index % tones.size], tones[index % tones.size].copy(alpha = 0.6f)))
            )
    )
}

// + Add photos cell
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddToVaultCell() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .combinedClickable(onClick = {})
            .background(Color(0xFF1E1C30)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add photos",
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Add",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
