package com.giraffe.matn.teacher.presentation.publish

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** States that students will no longer see the matn and that it can be republished unchanged (FR-038). */
@Composable
fun UnpublishConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalTeacherStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.unpublishConfirmTitle) },
        text = { Text(strings.unpublishConfirmBody) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.unpublishConfirmAction) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelAction) } },
    )
}

@Preview
@Composable
private fun UnpublishConfirmDialogArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    UnpublishConfirmDialog(onConfirm = {}, onDismiss = {})
}

@Preview
@Composable
private fun UnpublishConfirmDialogEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    UnpublishConfirmDialog(onConfirm = {}, onDismiss = {})
}
