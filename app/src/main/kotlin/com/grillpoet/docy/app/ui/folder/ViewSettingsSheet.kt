package com.grillpoet.docy.app.ui.folder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grillpoet.docy.core.sort.SortMode
import com.grillpoet.docy.core.sort.SortOrder
import com.grillpoet.docy.core.sort.SortSpec
import com.grillpoet.docy.core.sort.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingsSheet(
    sort: SortSpec,
    view: ViewMode,
    gridColumns: Int,
    onSortMode: (SortMode) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
    onFoldersFirst: (Boolean) -> Unit,
    onView: (ViewMode) -> Unit,
    onGridColumns: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "视图设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // 视图模式
            SectionLabel("视图")
            Row {
                FilterChip(
                    selected = view == ViewMode.LIST,
                    onClick = { onView(ViewMode.LIST) },
                    label = { Text("列表") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = null
                        )
                    }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = view == ViewMode.GRID,
                    onClick = { onView(ViewMode.GRID) },
                    label = { Text("网格") },
                    leadingIcon = {
                        Icon(Icons.Default.GridView, contentDescription = null)
                    }
                )
            }

            // 网格列数（仅网格）
            if (view == ViewMode.GRID) {
                Spacer(Modifier.height(12.dp))
                SectionLabel("网格列数")
                Row {
                    (2..5).forEach { n ->
                        FilterChip(
                            selected = gridColumns == n,
                            onClick = { onGridColumns(n) },
                            label = { Text("$n") },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 排序
            SectionLabel("排序")
            Row {
                SortModeChip("名称", sort.mode == SortMode.NAME) { onSortMode(SortMode.NAME) }
                SortModeChip("时间", sort.mode == SortMode.MODIFIED) { onSortMode(SortMode.MODIFIED) }
                SortModeChip("大小", sort.mode == SortMode.SIZE) { onSortMode(SortMode.SIZE) }
                SortModeChip("扩展名", sort.mode == SortMode.EXTENSION) { onSortMode(SortMode.EXTENSION) }
            }

            Spacer(Modifier.height(12.dp))

            // 顺序
            SectionLabel("顺序")
            Row {
                FilterChip(
                    selected = sort.order == SortOrder.ASC,
                    onClick = { onSortOrder(SortOrder.ASC) },
                    label = { Text("升序") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = sort.order == SortOrder.DESC,
                    onClick = { onSortOrder(SortOrder.DESC) },
                    label = { Text("降序") }
                )
            }

            Spacer(Modifier.height(12.dp))

            // 文件夹优先
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "文件夹优先",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = sort.foldersFirst,
                    onCheckedChange = onFoldersFirst
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun SortModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(end = 6.dp)
    )
}