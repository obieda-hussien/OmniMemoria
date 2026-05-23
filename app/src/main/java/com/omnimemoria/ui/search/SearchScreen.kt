package com.omnimemoria.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.delay

private val HintTexts = listOf(
    "Search photos, text, people...",
    "Try: 'phone numbers'",
    "Try: 'receipts from January'",
    "Try: 'people at the beach'",
    "Try: 'blue car'",
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // ── Custom search bar ──────────────────────────────────────────────
        OmniSearchBar(
            query         = query,
            onQueryChange = viewModel::setQuery,
            onClear       = viewModel::clearQuery,
            focusRequester = focusRequester
        )

        // ── Body ───────────────────────────────────────────────────────────
        AnimatedContent(
            targetState = results,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "search_content"
        ) { state ->
            when (state) {
                SearchResultState.Idle -> IdleState(
                    recent              = recent,
                    counts              = counts,
                    onRecentClick       = viewModel::setQuery,
                    onDeleteRecent      = viewModel::deleteRecent,
                    onQuickFilterClick  = viewModel::applyQuickFilter,
                    onOpenSettings      = onOpenSettings
                )
                SearchResultState.Searching -> SearchingState()
                is SearchResultState.Results -> ResultsState(
                    photos = state.photos,
                    query  = state.query,
                    onPhotoClick = onPhotoClick
                )
                is SearchResultState.Empty -> EmptyState(query = state.query)
            }
        }
    }
}

// ── Custom search bar ──────────────────────────────────────────────────────────

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
            delay(3_000)
            hintIndex = (hintIndex + 1) % HintTexts.size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 76.dp, start = 16.dp, end = 16.dp, bottom = 10.dp)  // below OmniTopBar
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1C30))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    AnimatedContent(
                        targetState  = hintIndex,
                        transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(400)) },
                        label        = "hint"
                    ) { idx ->
                        Text(
                            HintTexts[idx],
                            style  = MaterialTheme.typography.bodyMedium,
                            color  = Color(0xFF6A6890)
                        )
                    }
                }
                BasicTextField(
                    value         = query,
                    onValueChange = onQueryChange,
                    singleLine    = true,
                    textStyle     = TextStyle(
                        color    = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp
                    ),
                    cursorBrush  = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
                    modifier      = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            AnimatedVisibility(visible = query.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint               = Color(0xFF6A6890),
                        modifier           = Modifier.size(16.dp)
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // Recent searches
        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Recent")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    recent.forEach { term ->
                        RecentChip(
                            term     = term,
                            onClick  = { onRecentClick(term) },
                            onDelete = { onDeleteRecent(term) }
                        )
                    }
                }
            }
        }

        // Quick filters
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Quick Filters")
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickFilterCard(
                    icon    = Icons.Outlined.Phone,
                    title   = "Phone Numbers",
                    count   = counts.phoneNumbers,
                    color   = Color(0xFF1E3A5C),
                    accent  = Color(0xFF4A9EFF),
                    modifier = Modifier.weight(1f),
                    onClick = { onQuickFilterClick(QuickFilterType.PHONE_NUMBERS) }
                )
                QuickFilterCard(
                    icon    = Icons.Outlined.Email,
                    title   = "Emails",
                    count   = counts.emails,
                    color   = Color(0xFF1A3A1A),
                    accent  = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                    onClick = { onQuickFilterClick(QuickFilterType.EMAILS) }
                )
            }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickFilterCard(
                    icon    = Icons.Outlined.Face,
                    title   = "People",
                    count   = counts.faces,
                    color   = Color(0xFF2A1A3C),
                    accent  = Color(0xFFBE4B8A),
                    modifier = Modifier.weight(1f),
                    onClick = { onQuickFilterClick(QuickFilterType.PEOPLE) }
                )
                QuickFilterCard(
                    icon    = Icons.Outlined.History,
                    title   = "This Month",
                    count   = null,
                    color   = Color(0xFF2A2010),
                    accent  = Color(0xFFD97706),
                    modifier = Modifier.weight(1f),
                    onClick = { onQuickFilterClick(QuickFilterType.THIS_MONTH) }
                )
            }
        }

        // AI hint card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1C30))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "✨ Smarter search with AI",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Enable AI indexing to search by content, OCR text, and semantics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier          = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = onOpenSettings)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Open Settings",
                        style  = MaterialTheme.typography.labelMedium,
                        color  = MaterialTheme.colorScheme.primary
                    )
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
            .combinedClickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            term,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier         = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFF2D2B45))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove",
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
    color:    Color,
    accent:   Color,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .combinedClickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint     = accent,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopStart)
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                title,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White
            )
            if (count != null) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Section label ──────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp
    )
}

// ── Searching state ────────────────────────────────────────────────────────────

@Composable
private fun SearchingState() {
    // animated 3-dot ellipsis
    val transition = rememberInfiniteTransition(label = "dots")
    val alpha by transition.animateFloat(
        initialValue   = 0.3f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label          = "dot_alpha"
    )
    Column(
        modifier            = Modifier.fillMaxSize().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Searching", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(
                                alpha = (alpha - i * 0.2f).coerceIn(0.15f, 1f)
                            )
                        )
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        LazyVerticalGrid(
            columns               = GridCells.Fixed(3),
            contentPadding        = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement   = Arrangement.spacedBy(3.dp),
            userScrollEnabled     = false,
            modifier              = Modifier.height(300.dp)
        ) {
            items(9) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

// ── Results state ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultsState(
    photos:      List<MediaPhoto>,
    query:       String,
    onPhotoClick: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(3),
        contentPadding        = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement   = Arrangement.spacedBy(2.dp),
        modifier              = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "${photos.size} results for \"$query\"",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier   = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )
        }
        items(photos, key = { it.id }) { photo ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(4.dp))
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
    Column(
        modifier            = Modifier
            .fillMaxSize()
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
            Text("🔍", fontSize = 36.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "No results for",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "\"$query\"",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Try different keywords, or enable AI indexing in Settings for better results.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
