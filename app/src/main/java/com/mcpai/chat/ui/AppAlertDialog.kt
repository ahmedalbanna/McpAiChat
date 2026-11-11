package com.mcpai.chat.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Payload for the shared error dialog. */
data class AppAlertDialogData(
    val title: Int,
    val message: String,
    val confirmLabel: Int,
    val onConfirm: () -> Unit,
)

/**
 * Shared Material 3 dialog for the error cases: SPKI pin mismatch and an
 * unreachable server. One button retries or re-trusts; the other cancels.
 */
@Composable
fun AppAlertDialog(
    data: AppAlertDialogData?,
    onDismiss: () -> Unit,
) {
    if (data == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(data.title), style = MaterialTheme.typography.titleMedium) },
        text = { Text(data.message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                data.onConfirm()
                onDismiss()
            }) {
                Text(stringResource(data.confirmLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.mcpai.chat.R.string.action_cancel))
            }
        },
    )
}
