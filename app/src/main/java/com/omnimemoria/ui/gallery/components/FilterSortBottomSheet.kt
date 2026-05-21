package com.omnimemoria.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnimemoria.domain.model.GroupBy
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSortBottomSheet(
    activeSortConfig: SortConfig,
    onDismiss: () -> Unit,
    onApply: (SortConfig) -> Unit,
    onReset: (SortConfig) -> Unit
) {
    var pendingConfig by remember(activeSortConfig) { mutableStateOf(activeSortConfig) }
    var selectedActionIndex by remember(activeSortConfig) { mutableStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            SectionTitle("Sort by")
            SortBySection(
                selected = pendingConfig.sortBy,
                onSelected = { pendingConfig = pendingConfig.copy(sortBy = it) }
            )

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Direction")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    SortOrder.DESCENDING to "Descending ↓",
                    SortOrder.ASCENDING to "Ascending ↑"
                )
                options.forEachIndexed { index, (order, label) ->
                    SegmentedButton(
                        selected = pendingConfig.sortOrder == order,
                        onClick = { pendingConfig = pendingConfig.copy(sortOrder = order) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Group by")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    null to "None",
                    GroupBy.DAY to "Day",
                    GroupBy.MONTH to "Month",
                    GroupBy.YEAR to "Year",
                    GroupBy.LOCATION to "Location"
                )
                options.forEachIndexed { index, (groupBy, label) ->
                    SegmentedButton(
                        selected = pendingConfig.groupBy == groupBy,
                        onClick = { pendingConfig = pendingConfig.copy(groupBy = groupBy) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedActionIndex == 0,
                    onClick = {
                        selectedActionIndex = 0
                        pendingConfig = SortConfig().also { onReset(it) }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Reset") }
                )
                SegmentedButton(
                    selected = selectedActionIndex == 1,
                    onClick = {
                        selectedActionIndex = 1
                        onApply(pendingConfig)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("✓ Apply") }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SortBySection(
    selected: SortBy,
    onSelected: (SortBy) -> Unit
) {
    val options = remember {
        listOf(
            SortBy.DATE_TAKEN to "Date Taken",
            SortBy.DATE_MODIFIED to "Date Modified",
            SortBy.SIZE to "Size",
            SortBy.NAME to "Name",
            SortBy.TYPE to "Type",
            SortBy.RESOLUTION to "Resolution",
            SortBy.DURATION to "Duration",
            SortBy.FAVORITES_FIRST to "Favorites First"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (sortBy, label) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == sortBy,
                    onClick = { onSelected(sortBy) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label)
            }
        }
    }
}
