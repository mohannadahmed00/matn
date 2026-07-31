package com.giraffe.matn.teacher.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.giraffe.matn.teacher.presentation.strings.messageFor

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
    audioState: VerseAudioUiState,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAttachAudio: () -> Unit,
    onPlayAudio: () -> Unit,
    onRemoveAudio: () -> Unit,
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

        VerseAudioSlot(audioState, onAttachAudio, onPlayAudio, onRemoveAudio)

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

/** The per-verse audio column (`contracts/teacher-ui-contract.md` §1, `design-notes.md` T039). No
 * state of its own — [audioState] is the ViewModel's, keyed by verse id, so a scrolled-away row
 * that resumes shows its true progress rather than resetting to `remember`'s initial value. */
@Composable
private fun VerseAudioSlot(
    audioState: VerseAudioUiState,
    onAttach: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val strings = LocalTeacherStrings.current
    Column(
        modifier = Modifier.padding(horizontal = MatnSpacing.unit),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (audioState) {
            is VerseAudioUiState.Empty -> {
                TextButton(onClick = onAttach) { Text(strings.addRecording) }
            }
            is VerseAudioUiState.Uploading -> {
                Text(strings.uploadingAudio, style = MaterialTheme.typography.labelSmall)
                val progress = if (audioState.totalBytes > 0) audioState.uploadedBytes.toFloat() / audioState.totalBytes else 0f
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.size(width = MatnSpacing.unit * 8, height = MatnSpacing.unit))
            }
            is VerseAudioUiState.Loaded -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlay, modifier = Modifier.semantics { contentDescription = strings.playRecording }) {
                        Text(if (audioState.isPlaying) "⏸" else "▶")
                    }
                    Text(formatDurationMs(audioState.durationMs), style = MaterialTheme.typography.labelSmall)
                }
                Row {
                    TextButton(onClick = onAttach) { Text(strings.replaceRecording) }
                    TextButton(onClick = onRemove) { Text(strings.removeRecording) }
                }
            }
            is VerseAudioUiState.Failed -> {
                Text(strings.messageFor(audioState.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onAttach) { Text(strings.replaceRecording) }
            }
        }
    }
}

private fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun sampleVerse(text: String = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ") =
    DraftVerse(id = "v1", chapterId = null, displayNumber = 1, arabicText = text, audio = null, durationMs = 0L)

@Composable
private fun previewVerseRow(audioState: VerseAudioUiState, isFlagged: Boolean = false) = VerseRow(
    verse = sampleVerse(),
    isFlagged = isFlagged,
    audioState = audioState,
    onTextChange = {},
    onDelete = {},
    onMoveUp = {},
    onMoveDown = {},
    onAttachAudio = {},
    onPlayAudio = {},
    onRemoveAudio = {},
    dragHandleModifier = Modifier,
)

@Preview
@Composable
private fun VerseRowFlaggedPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    previewVerseRow(VerseAudioUiState.Empty, isFlagged = true)
}

@Preview
@Composable
private fun VerseRowEmptyArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    previewVerseRow(VerseAudioUiState.Empty)
}

@Preview
@Composable
private fun VerseRowEmptyEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    previewVerseRow(VerseAudioUiState.Empty)
}

@Preview
@Composable
private fun VerseRowUploadingArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    previewVerseRow(VerseAudioUiState.Uploading(uploadedBytes = 40_000, totalBytes = 100_000))
}

@Preview
@Composable
private fun VerseRowUploadingEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    previewVerseRow(VerseAudioUiState.Uploading(uploadedBytes = 40_000, totalBytes = 100_000))
}

@Preview
@Composable
private fun VerseRowLoadedArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    previewVerseRow(VerseAudioUiState.Loaded(durationMs = 12_000))
}

@Preview
@Composable
private fun VerseRowLoadedEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    previewVerseRow(VerseAudioUiState.Loaded(durationMs = 12_000))
}

@Preview
@Composable
private fun VerseRowFailedArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    previewVerseRow(VerseAudioUiState.Failed(com.giraffe.matn.domain.error.AudioAttachError.WrongFormat))
}

@Preview
@Composable
private fun VerseRowFailedEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    previewVerseRow(VerseAudioUiState.Failed(com.giraffe.matn.domain.error.AudioAttachError.WrongFormat))
}
