package com.omnimemoria.ui.components.filters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.omnimemoria.domain.model.GroupBy
import androidx.compose.foundation.clickable

@Composable
fun GallerySortFilterSheetContent(
    currentSort: SortConfig,
    currentFilter: FilterConfig,
    onDismiss: () -> Unit,
    onApply: (SortConfig, FilterConfig) -> Unit
) {
    var sortBy by remember(currentSort) { mutableStateOf(currentSort.sortBy) }
    var sortOrder by remember(currentSort) { mutableStateOf(currentSort.sortOrder) }
    var groupBy by remember(currentSort) { mutableStateOf(currentSort.groupBy) }
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
                SortBy.DATE_MODIFIED to "Date Modified",
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


        Spacer(Modifier.height(16.dp))
        Text("Group By", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            val groupOptions = listOf(
                null to "None",
                GroupBy.DAY to "Day",
                GroupBy.MONTH to "Month",
                GroupBy.YEAR to "Year",
                GroupBy.LOCATION to "Location"
            )

            groupOptions.forEach { (optionGroupBy, label) ->
                FilterChip(
                    selected = groupBy == optionGroupBy,
                    onClick = { groupBy = optionGroupBy },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(24.dp))

        // --- FILTERING SECTION ---
        Text("Media Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            MediaType.entries.forEach { mediaType ->
                val isSelected = filterBy.mediaTypes.contains(mediaType)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newTypes = if (isSelected) filterBy.mediaTypes - mediaType else filterBy.mediaTypes + mediaType
                        filterBy = filterBy.copy(mediaTypes = newTypes)
                    },
                    label = { Text(mediaType.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        ApplyButton(
            onApply = {
                onApply(currentSort.copy(sortBy = sortBy, sortOrder = sortOrder, groupBy = groupBy), filterBy)
                onDismiss()
            },
            onCancel = onDismiss
        )
    }
}
