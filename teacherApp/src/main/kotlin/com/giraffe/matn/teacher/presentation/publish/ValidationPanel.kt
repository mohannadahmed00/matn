package com.giraffe.matn.teacher.presentation.publish

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.ValidationReport
import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.common.ProblemRow
import com.giraffe.matn.teacher.presentation.common.ProblemSeverity
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import com.giraffe.matn.teacher.presentation.strings.messageFor

/**
 * `contracts/teacher-ui-contract.md` §3.6. Two sections: blocking (must fix before publishing) and
 * deferred (still outstanding — missing recordings, presented as work remaining, not failure).
 */
@Composable
fun ValidationPanel(
    report: ValidationReport,
    draft: MatnDraft,
    onProblemClick: (subjectId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalTeacherStrings.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (report.blocking.isNotEmpty()) {
            Text(strings.validationBlockingHeading, style = MaterialTheme.typography.titleMedium)
            report.blocking.forEach { error ->
                ProblemRow(
                    message = strings.messageFor(error, draft),
                    severity = ProblemSeverity.BLOCKING,
                    onClick = { onProblemClick(subjectIdOf(error, draft)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (report.deferred.isNotEmpty()) {
            Text(strings.validationDeferredHeading, style = MaterialTheme.typography.titleMedium)
            report.deferred.forEach { error ->
                ProblemRow(
                    message = strings.messageFor(error, draft),
                    severity = ProblemSeverity.DEFERRED,
                    onClick = { onProblemClick(subjectIdOf(error, draft)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** `validation-contract.md` §4: `DuplicateAudioRef` names a shared `fileRef`, not a verse — jumps
 * to the first verse referencing it. */
private fun subjectIdOf(error: ContentIntegrityError, draft: MatnDraft): String = when (error) {
    is ContentIntegrityError.MissingAudio -> error.verseId
    is ContentIntegrityError.OrphanChapterRef -> error.verseId
    is ContentIntegrityError.DuplicateAudioRef -> draft.verses.firstOrNull { it.audio?.fileRef == error.fileRef }?.id ?: ""
    else -> ""
}

private fun previewDraft(verses: List<com.giraffe.matn.domain.catalog.DraftVerse>) = MatnDraft(
    id = "m1", title = "T", author = "A", description = "", coverImageRef = null,
    structureKind = StructureKind.SIMPLE, defaultReciterId = "r1", chapters = emptyList(),
    verses = verses, publicationState = com.giraffe.matn.domain.catalog.PublicationState.DRAFT,
    createdAt = 0L, updatedAt = 0L, remoteRevision = null,
)

private fun verse(id: String, number: Int) =
    com.giraffe.matn.domain.catalog.DraftVerse(id, null, number, "text", null, 0L)

@Preview
@Composable
private fun ValidationPanelBlockingOnlyPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    val draft = previewDraft(listOf(verse("v1", 1), verse("v2", 1)))
    ValidationPanel(
        report = ValidationReport(blocking = listOf(ContentIntegrityError.DuplicateDisplayNumber("m1", 1)), deferred = emptyList()),
        draft = draft,
        onProblemClick = {},
    )
}

@Preview
@Composable
private fun ValidationPanelDeferredOnlyPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    val draft = previewDraft(listOf(verse("v1", 1)))
    ValidationPanel(
        report = ValidationReport(blocking = emptyList(), deferred = listOf(ContentIntegrityError.MissingAudio("v1"))),
        draft = draft,
        onProblemClick = {},
    )
}

@Preview
@Composable
private fun ValidationPanelBothArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    val draft = previewDraft(listOf(verse("v1", 1), verse("v2", 1)))
    ValidationPanel(
        report = ValidationReport(
            blocking = listOf(ContentIntegrityError.DuplicateDisplayNumber("m1", 1)),
            deferred = listOf(ContentIntegrityError.MissingAudio("v1")),
        ),
        draft = draft,
        onProblemClick = {},
    )
}
