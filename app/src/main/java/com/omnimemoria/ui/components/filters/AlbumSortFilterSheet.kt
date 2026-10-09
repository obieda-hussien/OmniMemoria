package com.omnimemoria.ui.components.filters

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.omnimemoria.domain.model.FolderSortConfig
import com.omnimemoria.domain.model.FolderSortBy
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.domain.model.FilterConfig

@Composable
fun AlbumSortFilterSheetContent(
    currentSort: FolderSortConfig,
    currentFilter: FilterConfig = FilterConfig(),
    onDismiss: () -> Unit,
    onApply: (FolderSortConfig, FilterConfig) -> Unit
) {
    var filter by remember(currentFilter) { mutableStateOf(currentFilter) }
    var sortBy by remember(currentSort) { mutableStateOf(currentSort.sortBy) }
    var sortOrder by remember(currentSort) { mutableStateOf(currentSort.sortOrder) }

    OmniSortFilterSheet(
        title = "Sort & Filter Albums",
        onDismiss = onDismiss,
        footer = { ApplyButton(onApply = { onApply(FolderSortConfig(sortBy = sortBy, sortOrder = sortOrder), filter) }, onCancel = onDismiss) }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(
                FolderSortBy.DATE_LATEST_PHOTO to "Latest Photo",
                FolderSortBy.NAME to "Name",
                FolderSortBy.PHOTO_COUNT to "Photo Count"
            )

            options.forEach { (candidate, label) ->
                SortOptionRow(
                    label = label,
                    selected = sortBy == candidate,
                    onClick = { sortBy = candidate }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Direction", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        SortOrderRow(currentOrder = sortOrder, onOrderChanged = { sortOrder = it })
        Spacer(Modifier.height(20.dp))
        MediaFilterControls(filter) { filter = it }

    }
}
