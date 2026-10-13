package com.grillpoet.docy.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 键盘辅助行。移动端没有 Tab 键，提供常用符号和跳转。
 * 每个按钮回调 onInsert 或 onMove。
 */
@Composable
fun KeyboardAuxRow(
    indent: Int,
    onInsert: (String) -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        "Tab" to " ".repeat(indent),
        "←" to "",
        "→" to "",
        "↑" to "",
        "↓" to "",
        "(" to "(",
        ")" to ")",
        "{" to "{",
        "}" to "}",
        "[" to "[",
        "]" to "]",
        "\"" to "\"",
        "'" to "'",
        "=" to "=",
        "--" to "--",
        "." to ".",
        "," to ",",
        ":" to ":"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            keys.forEach { (label, insert) ->
                TextButton(
                    onClick = {
                        when (label) {
                            "←" -> onMoveLeft()
                            "→" -> onMoveRight()
                            "↑" -> onMoveUp()
                            "↓" -> onMoveDown()
                            else -> onInsert(insert)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}