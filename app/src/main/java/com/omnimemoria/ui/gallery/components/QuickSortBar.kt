package com.omnimemoria.ui.gallery.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.filled.Favorite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSortBar(
    currentSort: SortConfig,
    onSortChanged: (SortConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val options = listOf(
        SortOption(SortBy.DATE_TAKEN, "Date", Icons.Outlined.CalendarMonth),
        SortOption(SortBy.SIZE, "Size", Icons.Outlined.SdStorage),
        SortOption(SortBy.NAME, "Name", Icons.Outlined.Title),
        SortOption(SortBy.RESOLUTION, "Resolution", Icons.Outlined.AspectRatio),
        SortOption(SortBy.FAVORITES_FIRST, "Favorites First", Icons.Filled.Favorite)
    )

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = currentSort.sortBy == option.sortBy
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        val newOrder = if (currentSort.sortOrder == SortOrder.DESCENDING) SortOrder.ASCENDING else SortOrder.DESCENDING
                        onSortChanged(currentSort.copy(sortOrder = newOrder))
                    } else {
                        onSortChanged(currentSort.copy(sortBy = option.sortBy, sortOrder = SortOrder.DESCENDING))
                    }
                },
                label = { Text(option.label) },
                leadingIcon = {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (isSelected) {
                        Icon(
                            imageVector = if (currentSort.sortOrder == SortOrder.DESCENDING) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.primary
                ),
                border = if (isSelected) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
            )
        }
    }
}

private data class SortOption(val sortBy: SortBy, val label: String, val icon: ImageVector)
