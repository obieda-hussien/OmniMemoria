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

@Composable
fun AlbumSortFilterSheetContent(
    currentSort: FolderSortConfig,
    onDismiss: () -> Unit,
    onApply: (FolderSortConfig) -> Unit
) {
    var sortBy by remember(currentSort) { mutableStateOf(currentSort.sortBy) }
    var sortOrder by remember(currentSort) { mutableStateOf(currentSort.sortOrder) }

    OmniSortFilterSheet(
        title = "Sort Albums",
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(
                FolderSortBy.DATE_LATEST_PHOTO to "Latest Photo",
                FolderSortBy.NAME to "Name",
                FolderSortBy.PHOTO_COUNT to "Photo Count"
            )

            options.forEach { (optionSortBy, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { sortBy = optionSortBy },
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

        ApplyButton(
            onApply = {
                onApply(FolderSortConfig(sortBy = sortBy, sortOrder = sortOrder))
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}
