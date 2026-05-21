package com.omnimemoria.ui.gallery.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder

@Composable
fun QuickSortBar(
    activeSortConfig: SortConfig,
    onSortChanged: (SortConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val chipItems = listOf(
        "📅 Date" to SortBy.DATE_TAKEN,
        "💾 Size" to SortBy.SIZE,
        "🔤 Name" to SortBy.NAME,
        "📐 Type" to SortBy.TYPE
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chipItems.forEach { (label, sortBy) ->
            val isSelected = activeSortConfig.sortBy == sortBy
            val borderColor = animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = spring(),
                label = "quick_sort_chip_border_color_$sortBy"
            )
            val borderWidth = animateDpAsState(
                targetValue = if (isSelected) 2.dp else 1.dp,
                animationSpec = spring(),
                label = "quick_sort_chip_border_width_$sortBy"
            )

            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        val toggledOrder = when (activeSortConfig.sortOrder) {
                            SortOrder.ASCENDING -> SortOrder.DESCENDING
                            SortOrder.DESCENDING -> SortOrder.ASCENDING
                        }
                        onSortChanged(activeSortConfig.copy(sortOrder = toggledOrder))
                    } else {
                        onSortChanged(activeSortConfig.copy(sortBy = sortBy))
                    }
                },
                label = { Text(label) },
                border = BorderStroke(borderWidth.value, borderColor.value),
                trailingIcon = {
                    if (isSelected) {
                        Icon(
                            imageVector = if (activeSortConfig.sortOrder == SortOrder.ASCENDING) {
                                Icons.Outlined.ArrowUpward
                            } else {
                                Icons.Outlined.ArrowDownward
                            },
                            contentDescription = null
                        )
                    }
                }
            )
        }
    }
}
