package com.omnimemoria.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.omnimemoria.domain.model.MediaPhoto
import com.omnimemoria.ui.components.ShimmerBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPhotoClick: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val counts by viewModel.quickFilterCounts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp)
    ) {
        SearchBar(
            query = query,
            onQueryChange = viewModel::setQuery,
            onSearch = viewModel::setQuery,
            active = true,
            onActiveChange = {},
            placeholder = { Text("Search photos, text, people...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {}

        Spacer(modifier = Modifier.height(8.dp))

        when (val state = results) {
            SearchResultState.Idle -> {
                IdleSearchState(
                    recentSearches = recent,
                    counts = counts,
                    onRecentClick = viewModel::setQuery,
                    onDeleteRecent = viewModel::deleteRecent,
                    onQuickFilterClick = viewModel::applyQuickFilter,
                    onOpenSettings = onOpenSettings
                )
            }

            SearchResultState.Searching -> SearchLoadingState()
            is SearchResultState.Results -> SearchResultsGrid(state.photos, state.query, onPhotoClick)
            is SearchResultState.Empty -> SearchEmptyState(state.query)
        }
    }
}

@Composable
private fun IdleSearchState(
    recentSearches: List<String>,
    counts: SearchQuickFilterCounts,
    onRecentClick: (String) -> Unit,
    onDeleteRecent: (String) -> Unit,
    onQuickFilterClick: (QuickFilterType) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (recentSearches.isNotEmpty()) {
            Text("Recent Searches", fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentSearches) { term ->
                    AssistChip(
                        onClick = { onRecentClick(term) },
                        label = { Text(term) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Delete",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { onDeleteRecent(term) }
                            )
                        }
                    )
                }
            }
        }

        Text("Quick Filters", fontWeight = FontWeight.Bold)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
            modifier = Modifier.height(190.dp)
        ) {
            item {
                QuickFilterCard("📱 Phone Numbers", Color(0xFF204E9A), counts.phoneNumbers) {
                    onQuickFilterClick(QuickFilterType.PHONE_NUMBERS)
                }
            }
            item {
                QuickFilterCard("📧 Emails", Color(0xFF1D7A4A), counts.emails) {
                    onQuickFilterClick(QuickFilterType.EMAILS)
                }
            }
            item {
                QuickFilterCard("👤 People", Color(0xFF643D9D), counts.faces) {
                    onQuickFilterClick(QuickFilterType.PEOPLE)
                }
            }
            item {
                QuickFilterCard("📅 This Month", Color(0xFF9A6A1E), null) {
                    onQuickFilterClick(QuickFilterType.THIS_MONTH)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("✨ Your photos aren't analyzed yet", fontWeight = FontWeight.SemiBold)
                Text(
                    "Enable AI indexing in Settings for smarter text search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Open Settings",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenSettings)
                )
            }
        }
    }
}

@Composable
private fun QuickFilterCard(
    title: String,
    color: Color,
    count: Int?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (count != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("$count", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SearchLoadingState() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.fillMaxSize()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsGrid(
    photos: List<MediaPhoto>,
    query: String,
    onPhotoClick: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                "${photos.size} results for '$query'",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(6.dp)
            )
        }
        items(photos, key = { it.id }) { photo ->
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { onPhotoClick(photo.id) }
            )
        }
    }
}

@Composable
private fun SearchEmptyState(query: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔎", style = MaterialTheme.typography.displaySmall)
        Text("No results for '$query'", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Try different keywords or enable AI indexing in Settings",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
