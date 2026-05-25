package com.omnimemoria.ui.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.ui.components.ShimmerBox
import com.omnimemoria.ui.home.HomeTopOverlaySpacing
import kotlinx.coroutines.delay

// ── Rotating hint strings ──────────────────────────────────────────────────────

private val HintTexts = listOf(
    "Search photos, text, people…",
    "Try: 'phone numbers'",
    "Try: 'receipts from last month'",
    "Try: 'photos with faces'",
    "Try: 'blue car'",
    "Try: 'this month'"
)

// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    onPhotoClick:   (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel:      SearchViewModel = hiltViewModel()
) {
    val query   by viewModel.query.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val recent  by viewModel.recentSearches.collectAsState()
    val counts  by viewModel.quickFilterCounts.collectAsState()

    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = HomeTopOverlaySpacing)
        ) {

            // ── Search bar (تغليف احترافي يمنع تداخل العناصر أثناء التمرير) ──
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                OmniSearchBar(
                    query          = query,
                    onQueryChange  = viewModel::setQuery,
                    onClear        = viewModel::clearQuery,
                    focusRequester = focusRequester
                )
            }

            // ── Body — animated content switch ─────────────────────────────
            AnimatedContent(
                targetState  = results,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "search_content",
                modifier = Modifier.weight(1f)
            ) { state ->
                when (state) {
                    SearchResultState.Idle -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        IdleState(
                            recent             = recent,
                            counts             = counts,
                            onRecentClick      = viewModel::setQuery,
                            onDeleteRecent     = viewModel::deleteRecent,
                            onQuickFilterClick = viewModel::applyQuickFilter,
                            onOpenSettings     = onOpenSettings
                        )
                    }
                    SearchResultState.Searching  -> SearchingState()
                    is SearchResultState.Results -> ResultsState(
                        photos       = state.photos,
                        query        = state.query,
                        onPhotoClick = onPhotoClick
                    )
                    is SearchResultState.Empty   -> EmptyState(query = state.query)
                }
            }
        }
    }
}

// ── Search bar ─────────────────────────────────────────────────────────────────

@Composable
private fun OmniSearchBar(
    query:          String,
    onQueryChange:  (String) -> Unit,
    onClear:        () -> Unit,
    focusRequester: FocusRequester
) {
    var hintIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(query) {
        if (query.isNotEmpty()) return@LaunchedEffect
        while (true) {
            delay(3_200)
            hintIndex = (hintIndex + 1) % HintTexts.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding() // يعطي الطول المناسب التلقائي بناءً على حجم شريط حالة نظام أندرويد لحمايته بالكامل
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp) // تقليل الـ padding الفوق ليتناسق بدقة وبدون تباعد عشوائي
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1E1C30))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search icon
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint               = Color(0xFF8B7FF5),
                modifier           = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))

            // Input + hint
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    AnimatedContent(
                        targetState  = hintIndex,
                        transitionSpec = {
                            (fadeIn(tween(450)) + slideInVertically { -it / 3 }) togetherWith
                                (fadeOut(tween(350)) + slideOutVertically { it / 3 })
                        },
                        label = "hint"
                    ) { idx ->
                        Text(
                            HintTexts[idx],
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF5A587A)
                        )
                    }
                }
                BasicTextField(
                    value           = query,
                    onValueChange   = onQueryChange,
                    singleLine      = true,
                    textStyle       = TextStyle(
                        color    = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush     = SolidColor(Color(0xFF8B7FF5)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
                    modifier        = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            // Clear button
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter   = fadeIn() + scaleIn(initialScale = 0.7f),
                exit    = fadeOut() + scaleOut(targetScale = 0.7f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2D2B45))
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint               = Color(0xFF8B7FF5),
                        modifier           = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ── Idle state ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdleState(
    recent:             List<String>,
    counts:             SearchQuickFilterCounts,
    onRecentClick:      (String) -> Unit,
    onDeleteRecent:     (String) -> Unit,
    onQuickFilterClick: (QuickFilterType) -> Unit,
    onOpenSettings:     () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Recent searches
        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Recent")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding        = PaddingValues(vertical = 2.dp)
                ) {
                    items(recent) { term ->
                        RecentChip(
                            term     = term,
                            onClick  = { onRecentClick(term) },
                            onDelete = { onDeleteRecent(term) }
                        )
                    }
                }
            }
        }

        // Quick filter cards
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Quick Filters")

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickFilterCard(
                    icon     = Icons.Outlined.Phone,
                    title    = "Phone Numbers",
                    count    = counts.phoneNumbers,
                    gradient = listOf(Color(0xFF0D2A4A), Color(0xFF1A3A5C)),
                    accent   = Color(0xFF4A9EFF),
                    modifier = Modifier.weight(1f),
                    onClick  = { onQuickFilterClick(QuickFilterType.PHONE_NUMBERS) }
                )
                QuickFilterCard(
                    icon     = Icons.Outlined.Email,
                    title    = "Emails",
                    count    = counts.emails,
                    gradient = listOf(Color(0xFF0D2A14), Color(0xFF1A4020)),
                    accent   = Color(0xFF50C878),
                    modifier = Modifier.weight(1f),
                    onClick  = { onQuickFilterClick(QuickFilterType.EMAILS) }
                )
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickFilterCard(
                    icon     = Icons.Outlined.Face,
                    title    = "People",
                    count    = counts.faces,
                    gradient = listOf(Color(0xFF1E0A2E), Color(0xFF2E1040)),
                    accent   = Color(0xFFBE4B8A),
                    modifier = Modifier.weight(1f),
                    onClick  = { onQuickFilterClick(QuickFilterType.PEOPLE) }
                )
                QuickFilterCard(
                    icon     = Icons.Outlined.CalendarMonth,
                    title    = "This Month",
                    count    = null,
                    gradient = listOf(Color(0xFF2A1A08), Color(0xFF3A2510)),
                    accent   = Color(0xFFFBC02D),
                    modifier = Modifier.weight(1f),
                    onClick  = { onQuickFilterClick(QuickFilterType.THIS_MONTH) }
                )
            }
        }

        // AI hint card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF1A1830), Color(0xFF1E1C38))
                    )
                )
                .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF8B7FF5).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        null,
                        tint     = Color(0xFF8B7FF5),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Smarter search with AI",
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Enable AI indexing to search by content, OCR text, phone numbers, and more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF8B7FF5).copy(alpha = 0.14f))
                            .border(1.dp, Color(0xFF8B7FF5).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            null,
                            tint     = Color(0xFF8B7FF5),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Enable in Settings",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = Color(0xFF8B7FF5),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ── Recent chip ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentChip(term: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1C30))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.History,
            null,
            tint     = Color(0xFF8B7FF5).copy(alpha = 0.6f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            term,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF2D2B45))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                "Remove",
                tint     = Color(0xFF6A6890),
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

// ── Quick filter card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickFilterCard(
    icon:     ImageVector,
    title:    String,
    count:    Int?,
    gradient: List<Color>,
    accent:   Color,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(gradient))
            .border(1.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // Icon top-left
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.14f))
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint     = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        // Text bottom
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                title,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            if (count != null) {
                Text(
                    text  = "$count found",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.8f)
                )
            }
        }

        // Chevron
        Icon(
            Icons.Outlined.ChevronRight,
            null,
            tint     = accent.copy(alpha = 0.4f),
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.TopEnd)
        )
    }
}

// ── Section label ──────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style         = MaterialTheme.typography.labelMedium,
        fontWeight    = FontWeight.SemiBold,
        color         = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.6.sp
    )
}

// ── Searching state ────────────────────────────────────────────────────────────

@Composable
private fun SearchingState() {
    val transition = rememberInfiniteTransition(label = "dots")
    val alpha by transition.animateFloat(
        initialValue   = 0.25f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label          = "dot_alpha"
    )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Searching",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            Color(0xFF8B7FF5).copy(
                                alpha = (alpha - i * 0.22f).coerceIn(0.12f, 1f)
                            )
                        )
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        // Skeleton grid
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            userScrollEnabled     = false,
            modifier              = Modifier.height(280.dp)
        ) {
            items(9) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

// ── Results state ──────────────────────────────────────────────────────────────

@Composable
private fun ResultsState(
    photos:      List<MediaPhoto>,
    query:       String,
    onPhotoClick: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        contentPadding        = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement   = Arrangement.spacedBy(3.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        // Results header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8B7FF5))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${photos.size} result${if (photos.size != 1) "s" else ""} for",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "\"$query\"",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF8B7FF5)
                )
            }
        }

        items(photos, key = { it.id }) { photo ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPhotoClick(photo.id) }
            ) {
                AsyncImage(
                    model              = photo.uri,
                    contentDescription = photo.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(120.dp).navigationBarsPadding())
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(query: String) {
    val floatY by rememberInfiniteTransition(label = "float")
        .animateFloat(
            initialValue  = 0f,
            targetValue   = -8f,
            animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label         = "float_y"
        )

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .offset(y = floatY.dp)
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1E1C30))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("🔍", fontSize = 38.sp)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "No results for",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "\"$query\"",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Try different keywords, or enable AI indexing in Settings for smarter results.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
