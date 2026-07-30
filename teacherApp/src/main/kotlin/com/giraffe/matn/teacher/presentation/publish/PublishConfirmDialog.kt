package com.giraffe.matn.teacher.presentation.publish

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** States plainly that the matn will publish with no recordings yet (US4). */
@Composable
fun PublishConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalTeacherStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.publishConfirmTitle) },
        text = { Text(strings.publishConfirmBody) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.publishConfirmAction) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelAction) } },
    )
}

@Preview
@Composable
private fun PublishConfirmDialogArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    PublishConfirmDialog(onConfirm = {}, onDismiss = {})
}

@Preview
@Composable
private fun PublishConfirmDialogEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    PublishConfirmDialog(onConfirm = {}, onDismiss = {})
}
