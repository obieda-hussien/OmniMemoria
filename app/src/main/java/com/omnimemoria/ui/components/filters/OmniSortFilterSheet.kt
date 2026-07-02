package com.omnimemoria.ui.components.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnimemoria.domain.model.FilterConfig
import com.omnimemoria.domain.model.SortConfig
import com.omnimemoria.domain.model.SortBy
import com.omnimemoria.domain.model.SortOrder
import com.omnimemoria.domain.model.FolderSortConfig
import com.omnimemoria.domain.model.FolderSortBy
import com.omnimemoria.ui.theme.OmniSheetContainerColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniSortFilterSheet(
    title: String = "Sort & Filter",
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OmniSheetContainerColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3A3860))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(24.dp))

            content()
        }
    }
}

@Composable
fun SortOrderRow(
    currentOrder: SortOrder,
    onOrderChanged: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val ascColor = if (currentOrder == SortOrder.ASCENDING) MaterialTheme.colorScheme.primary else Color.Transparent
        val ascContentColor = if (currentOrder == SortOrder.ASCENDING) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        val descColor = if (currentOrder == SortOrder.DESCENDING) MaterialTheme.colorScheme.primary else Color.Transparent
        val descContentColor = if (currentOrder == SortOrder.DESCENDING) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

        Surface(
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = ascColor,
            border = if (currentOrder != SortOrder.ASCENDING) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            onClick = { onOrderChanged(SortOrder.ASCENDING) }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("↑ Oldest", color = ascContentColor, fontWeight = FontWeight.Medium)
            }
        }

        Surface(
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = descColor,
            border = if (currentOrder != SortOrder.DESCENDING) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            onClick = { onOrderChanged(SortOrder.DESCENDING) }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("↓ Newest", color = descContentColor, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ApplyButton(
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clickable { onCancel() },
            contentAlignment = Alignment.Center
        ) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .weight(2f)
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onApply() },
            contentAlignment = Alignment.Center
        ) {
            Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        }
    }
}
