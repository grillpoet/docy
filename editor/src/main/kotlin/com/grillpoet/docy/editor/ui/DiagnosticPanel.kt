package com.grillpoet.docy.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grillpoet.docy.editor.lint.Diagnostic
import com.grillpoet.docy.editor.lint.Severity

@Composable
fun DiagnosticPanel(
    diagnostics: List<Diagnostic>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onJump: (Diagnostic) -> Unit,
    modifier: Modifier = Modifier
) {
    if (diagnostics.isEmpty()) return

    val errors = diagnostics.count { it.severity == Severity.ERROR }
    val warnings = diagnostics.count { it.severity == Severity.WARNING }
    val summary = buildString {
        if (errors > 0) append("$errors error")
        if (errors > 1) append("s")
        if (errors > 0 && warnings > 0) append(" · ")
        if (warnings > 0) append("$warnings warning")
        if (warnings > 1) append("s")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                              else Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            LazyColumn(Modifier.heightIn(max = 180.dp)) {
                items(diagnostics) { d ->
                    DiagnosticRow(d, onJump)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(d: Diagnostic, onJump: (Diagnostic) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onJump(d) }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "L${d.line + 1}:${d.col + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = d.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (d.severity == Severity.ERROR)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}