package com.giraffe.matn.teacher.presentation.publish

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.common.AudioCompletenessBadge
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** Names what is being published — verse count and audio completeness together — since publish is
 * the terminal action (US4, `contracts/teacher-ui-contract.md` §4). By the time this dialog can be
 * confirmed, the publish gate (FR-028) already guarantees [audioCompleteness] is `COMPLETE`. */
@Composable
fun PublishConfirmDialog(
    verseCount: Int,
    audioCompleteness: AudioCompleteness,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalTeacherStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.publishConfirmTitle) },
        text = {
            Column {
                Text(strings.publishConfirmBody)
                Text(
                    strings.publishConfirmVerseCount.replace("%d", verseCount.toString()),
                    modifier = Modifier.padding(top = MatnSpacing.unit),
                )
                AudioCompletenessBadge(audioCompleteness, modifier = Modifier.padding(top = MatnSpacing.unit))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.publishConfirmAction) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancelAction) } },
    )
}

@Preview
@Composable
private fun PublishConfirmDialogArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    PublishConfirmDialog(verseCount = 109, audioCompleteness = AudioCompleteness.COMPLETE, onConfirm = {}, onDismiss = {})
}

@Preview
@Composable
private fun PublishConfirmDialogEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    PublishConfirmDialog(verseCount = 109, audioCompleteness = AudioCompleteness.COMPLETE, onConfirm = {}, onDismiss = {})
}
