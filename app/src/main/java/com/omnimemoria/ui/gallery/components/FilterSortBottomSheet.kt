package com.omnimemoria.ui.gallery.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.GroupBy
import com.omnimemoria.domain.model.MediaType
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortOrder
import java.util.Calendar
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSortBottomSheet(
    activeSortConfig: SortConfig,
    activeFilterConfig: FilterConfig,
    onDismiss: () -> Unit,
    onApply: (SortConfig, FilterConfig) -> Unit
) {
    var pendingSortConfig by remember(activeSortConfig) { mutableStateOf(activeSortConfig) }
    var pendingFilterConfig by remember(activeFilterConfig) { mutableStateOf(activeFilterConfig) }
    var pendingDateOption by remember(activeFilterConfig) {
        mutableStateOf(if (activeFilterConfig.dateRange == null) DateFilterOption.ALL else DateFilterOption.SPECIFIC_DATE)
    }
    var showDateDialog by remember { mutableStateOf(false) }

    val sliderValues = remember(pendingFilterConfig.minSizeBytes, pendingFilterConfig.maxSizeBytes) {
        val min = bytesToNormalized(pendingFilterConfig.minSizeBytes ?: 0L)
        val max = bytesToNormalized(pendingFilterConfig.maxSizeBytes ?: MAX_SIZE_BYTES)
        min..max
    }

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
                selected = pendingSortConfig.sortBy,
                onSelected = { pendingSortConfig = pendingSortConfig.copy(sortBy = it) }
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
                        selected = pendingSortConfig.sortOrder == order,
                        onClick = { pendingSortConfig = pendingSortConfig.copy(sortOrder = order) },
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
                        selected = pendingSortConfig.groupBy == groupBy,
                        onClick = { pendingSortConfig = pendingSortConfig.copy(groupBy = groupBy) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Type")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val typeOptions = listOf(
                    MediaType.IMAGE to "Images",
                    MediaType.VIDEO to "Video",
                    MediaType.GIF to "GIF",
                    MediaType.RAW to "RAW"
                )
                typeOptions.forEach { (type, label) ->
                    FilterChip(
                        selected = type in pendingFilterConfig.mediaTypes,
                        onClick = {
                            val nextTypes = pendingFilterConfig.mediaTypes.toMutableSet().apply {
                                if (type in this) remove(type) else add(type)
                            }
                            pendingFilterConfig = pendingFilterConfig.copy(mediaTypes = nextTypes)
                        },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Format")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val mimeOptions = listOf(
                    "image/jpeg" to "JPEG",
                    "image/png" to "PNG",
                    "image/webp" to "WEBP",
                    "image/heic" to "HEIC",
                    "image/avif" to "AVIF",
                    "video/mp4" to "MP4",
                    "video/x-matroska" to "MKV"
                )
                mimeOptions.forEach { (mimeType, label) ->
                    FilterChip(
                        selected = mimeType in pendingFilterConfig.mimeTypes,
                        onClick = {
                            val nextTypes = pendingFilterConfig.mimeTypes.toMutableSet().apply {
                                if (mimeType in this) remove(mimeType) else add(mimeType)
                            }
                            pendingFilterConfig = pendingFilterConfig.copy(mimeTypes = nextTypes)
                        },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Size")
            val minSize = normalizedToBytes(sliderValues.start)
            val maxSize = normalizedToBytes(sliderValues.endInclusive)
            Text(
                text = "${formatSizeValue(minSize)} - ${formatSizeValue(maxSize)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RangeSlider(
                value = sliderValues,
                onValueChange = { range ->
                    pendingFilterConfig = pendingFilterConfig.copy(
                        minSizeBytes = normalizedToBytes(range.start).coerceAtLeast(0L),
                        maxSizeBytes = normalizedToBytes(range.endInclusive).coerceAtMost(MAX_SIZE_BYTES)
                    )
                },
                valueRange = 0f..1f
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val presets = listOf(
                    SizePreset("Under 1MB", 0L, 1L * MB),
                    SizePreset("1-10MB", 1L * MB, 10L * MB),
                    SizePreset("10-100MB", 10L * MB, 100L * MB),
                    SizePreset("Over 100MB", 100L * MB, MAX_SIZE_BYTES)
                )
                presets.forEach { preset ->
                    val selected = (pendingFilterConfig.minSizeBytes ?: 0L) == preset.min &&
                        (pendingFilterConfig.maxSizeBytes ?: MAX_SIZE_BYTES) == preset.max
                    FilterChip(
                        selected = selected,
                        onClick = {
                            pendingFilterConfig = pendingFilterConfig.copy(
                                minSizeBytes = preset.min,
                                maxSizeBytes = preset.max
                            )
                        },
                        label = { Text(preset.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SectionTitle("Date")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow {
                    DateFilterOption.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = pendingDateOption == option,
                            onClick = {
                                pendingDateOption = option
                                when (option) {
                                    DateFilterOption.ALL -> {
                                        pendingFilterConfig = pendingFilterConfig.copy(dateRange = null)
                                    }
                                    DateFilterOption.THIS_WEEK -> {
                                        pendingFilterConfig = pendingFilterConfig.copy(dateRange = getCurrentWeekRange())
                                    }
                                    DateFilterOption.THIS_MONTH -> {
                                        pendingFilterConfig = pendingFilterConfig.copy(dateRange = getCurrentMonthRange())
                                    }
                                    DateFilterOption.THIS_YEAR -> {
                                        pendingFilterConfig = pendingFilterConfig.copy(dateRange = getCurrentYearRange())
                                    }
                                    DateFilterOption.SPECIFIC_DATE -> {
                                        showDateDialog = true
                                    }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = DateFilterOption.entries.size),
                            label = { Text(option.label) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = pendingSortConfig == SortConfig() && pendingFilterConfig == FilterConfig(),
                    onClick = {
                        pendingSortConfig = SortConfig()
                        pendingFilterConfig = FilterConfig()
                        pendingDateOption = DateFilterOption.ALL
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Reset") }
                )
                SegmentedButton(
                    selected = pendingSortConfig != activeSortConfig || pendingFilterConfig != activeFilterConfig,
                    onClick = {
                        onApply(pendingSortConfig, pendingFilterConfig)
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("✓ Apply") }
                )
            }
        }
    }

    if (showDateDialog) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = pendingFilterConfig.dateRange?.first,
            initialSelectedEndDateMillis = pendingFilterConfig.dateRange?.second
        )
        DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            pendingFilterConfig = pendingFilterConfig.copy(dateRange = start to end)
                        }
                        showDateDialog = false
                    },
                    enabled = pickerState.selectedStartDateMillis != null && pickerState.selectedEndDateMillis != null
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = pickerState, showModeToggle = false)
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

private data class SizePreset(val label: String, val min: Long, val max: Long)

private enum class DateFilterOption(val label: String) {
    ALL("All"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year"),
    SPECIFIC_DATE("Specific Date")
}

private const val MB = 1024L * 1024L
private const val MAX_SIZE_BYTES = 500L * MB

private fun normalizedToBytes(value: Float): Long {
    if (value <= 0f) return 0L
    val max = (MAX_SIZE_BYTES + 1L).toDouble()
    return (exp(value * ln(max)) - 1.0).roundToLong()
}

private fun bytesToNormalized(bytes: Long): Float {
    if (bytes <= 0L) return 0f
    val max = (MAX_SIZE_BYTES + 1L).toDouble()
    return (ln((bytes + 1L).toDouble()) / ln(max)).toFloat().coerceIn(0f, 1f)
}

private fun formatSizeValue(sizeBytes: Long): String = when {
    sizeBytes < MB -> "<1MB"
    else -> "${(sizeBytes / MB).coerceAtMost(500)}MB"
}

private fun getCurrentWeekRange(): Pair<Long, Long> {
    val calendar = Calendar.getInstance()
    calendar.firstDayOfWeek = Calendar.MONDAY
    calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis to System.currentTimeMillis()
}

private fun getCurrentMonthRange(): Pair<Long, Long> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis to System.currentTimeMillis()
}

private fun getCurrentYearRange(): Pair<Long, Long> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.DAY_OF_YEAR, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis to System.currentTimeMillis()
}
