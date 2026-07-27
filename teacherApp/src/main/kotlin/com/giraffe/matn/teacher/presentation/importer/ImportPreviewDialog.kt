package com.giraffe.matn.teacher.presentation.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.giraffe.matn.domain.catalog.ImportPreview
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** `contracts/teacher-ui-contract.md` §3.5. Nothing is committed until confirmed. */
@Composable
fun ImportPreviewDialog(preview: ImportPreview, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val strings = LocalTeacherStrings.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings.importPreviewTitle) },
        text = {
            Column {
                Text(strings.importPreviewLineCount.replace("%d", preview.lines.size.toString()))
                preview.problemLineNumbers.forEach { number ->
                    Text(
                        text = strings.importPreviewProblemLine.replace("%d", number.toString()),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(preview.lines.take(10)) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(min = MatnSpacing.unit * 3))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(strings.importConfirm) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(strings.importCancel) } },
    )
}

@Preview
@Composable
private fun ImportPreviewDialogCleanArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    ImportPreviewDialog(
        preview = ImportPreview(lines = listOf("بيت أول", "بيت ثانٍ", "بيت ثالث"), problemLineNumbers = emptyList()),
        onCancel = {},
        onConfirm = {},
    )
}

@Preview
@Composable
private fun ImportPreviewDialogWithProblemsEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    ImportPreviewDialog(
        preview = ImportPreview(lines = listOf("line one", "line two"), problemLineNumbers = listOf(3, 7)),
        onCancel = {},
        onConfirm = {},
    )
}
