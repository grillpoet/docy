package com.grillpoet.docy.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun FindReplaceSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    replaceText: String,
    onReplaceChange: (String) -> Unit,
    matchCount: Int,
    currentIndex: Int,
    caseSensitive: Boolean,
    onToggleCaseSensitive: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 局部 TextFieldValue：自己维护光标，避免每次重组被外部 String 重置。
    var queryTfv by remember { mutableStateOf(TextFieldValue(query)) }
    var replaceTfv by remember { mutableStateOf(TextFieldValue(replaceText)) }

    // 外部 query 变了（比如清空、上一个/下一个改词）才同步，且同步时光标到末尾。
    LaunchedEffect(query) {
        if (queryTfv.text != query) {
            queryTfv = TextFieldValue(query, TextRange(query.length))
        }
    }
    LaunchedEffect(replaceText) {
        if (replaceTfv.text != replaceText) {
            replaceTfv = TextFieldValue(replaceText, TextRange(replaceText.length))
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = queryTfv,
                    onValueChange = {
                        queryTfv = it
                        onQueryChange(it.text)
                    },
                    placeholder = { Text("查找") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (matchCount == 0) "0/0"
                           else "${currentIndex + 1}/$matchCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onPrev, enabled = matchCount > 0) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                }
                IconButton(onClick = onNext, enabled = matchCount > 0) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replaceTfv,
                    onValueChange = {
                        replaceTfv = it
                        onReplaceChange(it.text)
                    },
                    placeholder = { Text("替换为") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onReplaceOne,
                    enabled = matchCount > 0
                ) { Text("替换") }
                TextButton(
                    onClick = onReplaceAll,
                    enabled = matchCount > 0
                ) { Text("全部") }
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CaseToggle(caseSensitive, onToggleCaseSensitive)
            }
        }
    }
}

@Composable
private fun CaseToggle(active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface
    val fg = if (active) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        color = bg
    ) {
        TextButton(onClick = onClick) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.labelLarge,
                color = fg
            )
        }
    }
}
