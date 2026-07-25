package com.giraffe.matn.presentation.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.confirm_removal_cancel
import matn.shared.generated.resources.confirm_removal_confirm
import matn.shared.generated.resources.confirm_removal_message_pending
import matn.shared.generated.resources.confirm_removal_message_reclaimed
import matn.shared.generated.resources.confirm_removal_title
import org.jetbrains.compose.resources.stringResource

/**
 * Removal confirmation (T057, FR-018) — states the bytes at stake before deleting. **The copy
 * switches on platform semantics**: when [isReleasedPendingSystemReclaim] is true (iOS, research
 * D2), the text says the space is released and reclaimed by the system when needed, and never
 * promises an immediate figure. Stateless: driven entirely by [matnTitle]/[bytes]/
 * [isReleasedPendingSystemReclaim] plus [onConfirm]/[onDismiss].
 */
@Composable
fun ConfirmRemovalDialog(
    matnTitle: String,
    bytes: Long,
    isReleasedPendingSystemReclaim: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MatnShapes.xl,
        title = { Text(stringResource(Res.string.confirm_removal_title, matnTitle)) },
        text = {
            val message = if (isReleasedPendingSystemReclaim) {
                stringResource(Res.string.confirm_removal_message_pending, formatBytes(bytes))
            } else {
                stringResource(Res.string.confirm_removal_message_reclaimed, formatBytes(bytes))
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.confirm_removal_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm_removal_cancel))
            }
        },
    )
}

// --------------------------------------------------------------------------- Previews

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ConfirmRemovalDialogReclaimedPreview() {
    MatnTheme {
        ConfirmRemovalDialog(
            matnTitle = "الأجرومية المهذبة",
            bytes = 2_400_000,
            isReleasedPendingSystemReclaim = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ConfirmRemovalDialogPendingReclaimPreview() {
    MatnTheme {
        ConfirmRemovalDialog(
            matnTitle = "الأجرومية المهذبة",
            bytes = 2_400_000,
            isReleasedPendingSystemReclaim = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
