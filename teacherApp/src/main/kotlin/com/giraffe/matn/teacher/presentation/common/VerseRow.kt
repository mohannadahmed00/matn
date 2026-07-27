package com.giraffe.matn.teacher.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/**
 * Stateless verse row (`contracts/teacher-ui-contract.md` §3.4 verse-list rules). Text state lives
 * in the ViewModel — this row holds none of its own, which is what keeps a 500-row list from
 * stuttering (FR-025). The Arabic text field always renders RTL regardless of the interface
 * language (FR-021).
 */
@Composable
fun VerseRow(
    verse: DraftVerse,
    isFlagged: Boolean,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val strings = LocalTeacherStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isFlagged) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
            )
            .padding(vertical = MatnSpacing.unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "⠿",
            modifier = dragHandleModifier
                .padding(horizontal = MatnSpacing.unit)
                .semantics { contentDescription = strings.dragToReorder },
        )

        Box(
            modifier = Modifier
                .size(MatnSpacing.unit * 4)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = verse.displayNumber.toString(), style = MaterialTheme.typography.labelMedium)
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            OutlinedTextField(
                value = verse.arabicText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f).padding(horizontal = MatnSpacing.unit),
            )
        }

        IconButton(onClick = onMoveUp, modifier = Modifier.semantics { contentDescription = strings.moveVerseUp }) {
            Text("▲")
        }
        IconButton(onClick = onMoveDown, modifier = Modifier.semantics { contentDescription = strings.moveVerseDown }) {
            Text("▼")
        }
        IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = strings.deleteVerse }) {
            Text("✕")
        }
    }
}

private fun sampleVerse(text: String = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ") =
    DraftVerse(id = "v1", chapterId = null, displayNumber = 1, arabicText = text, audio = null, durationMs = 0L)

@Preview
@Composable
private fun VerseRowNormalPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    VerseRow(sampleVerse(), isFlagged = false, {}, {}, {}, {}, Modifier)
}

@Preview
@Composable
private fun VerseRowFlaggedPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    VerseRow(sampleVerse(), isFlagged = true, {}, {}, {}, {}, Modifier)
}

@Preview
@Composable
private fun VerseRowEnglishChromePreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    VerseRow(sampleVerse(), isFlagged = false, {}, {}, {}, {}, Modifier)
}
