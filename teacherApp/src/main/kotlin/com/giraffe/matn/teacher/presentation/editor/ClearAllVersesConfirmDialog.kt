package com.giraffe.matn.teacher.presentation.editor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** Confirms before wiping the whole verse list — the fast undo for a mistaken bulk import. */
@Composable
fun ClearAllVersesConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalTeacherStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.clearAllVersesConfirmTitle) },
        text = { Text(strings.clearAllVersesConfirmBody) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.clearAllVersesConfirmAction) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelAction) } },
    )
}

@Preview
@Composable
private fun ClearAllVersesConfirmDialogArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    ClearAllVersesConfirmDialog(onConfirm = {}, onDismiss = {})
}

@Preview
@Composable
private fun ClearAllVersesConfirmDialogEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    ClearAllVersesConfirmDialog(onConfirm = {}, onDismiss = {})
}
