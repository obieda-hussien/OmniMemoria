package com.omnimemoria.ui.components.filters

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.domain.model.MediaType

@Composable
fun GallerySortFilterSheetContent(
    currentSort: SortConfig,
    currentFilter: FilterConfig,
    onDismiss: () -> Unit,
    onApply: (SortConfig, FilterConfig) -> Unit
) {
    var sortBy by remember(currentSort) { mutableStateOf(currentSort.sortBy) }
    var sortOrder by remember(currentSort) { mutableStateOf(currentSort.sortOrder) }
    var filterBy by remember(currentFilter) { mutableStateOf(currentFilter) }

    OmniSortFilterSheet(
        title = "Sort & Filter",
        onDismiss = onDismiss
    ) {
        // --- SORTING SECTION ---
        Text("Sort By", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(
                SortBy.DATE_TAKEN to "Date Taken",
                SortBy.NAME to "File Name",
                SortBy.TYPE to "File Type",
                SortBy.RESOLUTION to "Resolution",
                SortBy.DURATION to "Duration",
                SortBy.SIZE to "Size",
                SortBy.FAVORITES_FIRST to "Favorites First"
            )

            options.forEach { (optionSortBy, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label)
                    RadioButton(
                        selected = sortBy == optionSortBy,
                        onClick = { sortBy = optionSortBy }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Direction", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SortOrderRow(currentOrder = sortOrder, onOrderChanged = { sortOrder = it })

        Spacer(Modifier.height(24.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(24.dp))

        // --- FILTERING SECTION ---
        Text("Media Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val isImageSelected = filterBy.mediaTypes.contains(MediaType.IMAGE)
            val isVideoSelected = filterBy.mediaTypes.contains(MediaType.VIDEO)

            FilterChip(
                selected = isImageSelected,
                onClick = {
                    val newTypes = if (isImageSelected) filterBy.mediaTypes - MediaType.IMAGE else filterBy.mediaTypes + MediaType.IMAGE
                    filterBy = filterBy.copy(mediaTypes = newTypes)
                },
                label = { Text("Images") }
            )
            FilterChip(
                selected = isVideoSelected,
                onClick = {
                    val newTypes = if (isVideoSelected) filterBy.mediaTypes - MediaType.VIDEO else filterBy.mediaTypes + MediaType.VIDEO
                    filterBy = filterBy.copy(mediaTypes = newTypes)
                },
                label = { Text("Videos") }
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Smart Filters", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Has Faces")
                Switch(
                    checked = filterBy.hasFaces == true,
                    onCheckedChange = { filterBy = filterBy.copy(hasFaces = if (it) true else null) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Has Text")
                Switch(
                    checked = filterBy.hasText == true,
                    onCheckedChange = { filterBy = filterBy.copy(hasText = if (it) true else null) }
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Favorites Only")
                Switch(
                    checked = filterBy.isFavorite == true,
                    onCheckedChange = { filterBy = filterBy.copy(isFavorite = if (it) true else null) }
                )
            }
        }

        ApplyButton(
            onApply = {
                onApply(currentSort.copy(sortBy = sortBy, sortOrder = sortOrder), filterBy)
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}
