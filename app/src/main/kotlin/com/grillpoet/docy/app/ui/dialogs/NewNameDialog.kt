package com.grillpoet.docy.app.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun NewNameDialog(
    title: String,
    modifier: Modifier = Modifier,
    initialName: String = "",
    confirmLabel: String = "创建",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(TextFieldValue(initialName)) }
    val name = value.text
    val invalid = name.isBlank() || name.contains('/') || name == "." || name == ".."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = invalid && name.isNotEmpty(),
                supportingText = if (invalid && name.isNotEmpty()) {
                    { Text("名称不能为空或含 /") }
                } else null,
                modifier = Modifier
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = !invalid
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}