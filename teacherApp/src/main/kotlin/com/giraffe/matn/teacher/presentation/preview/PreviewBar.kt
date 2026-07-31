package com.giraffe.matn.teacher.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.audio.PreviewState
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** `contracts/teacher-ui-contract.md` §3 — the five preview-transport states. Stateless: [state]
 * is the ViewModel's, playback is gapless by construction (`research.md` D9) so this bar must not
 * insert its own delay between verses. */
@Composable
fun PreviewBar(
    state: PreviewState,
    onPlayFromStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalTeacherStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MatnShapes.lg)
            .padding(MatnSpacing.unit * 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MatnSpacing.unit),
    ) {
        when (state) {
            is PreviewState.Idle -> {
                TextButton(onClick = onPlayFromStart) { Text(strings.previewMatn) }
            }
            is PreviewState.Buffering -> {
                Text(strings.previewBuffering, style = MaterialTheme.typography.labelSmall)
            }
            is PreviewState.Playing -> {
                TextButton(onClick = onPause) { Text("⏸") }
                TextButton(onClick = onStop) { Text(strings.cancelAction) }
                Text(strings.previewPlaying.replace("%s", state.verseNumber.toString()), style = MaterialTheme.typography.labelSmall)
            }
            is PreviewState.Paused -> {
                TextButton(onClick = onResume) { Text("▶") }
                TextButton(onClick = onStop) { Text(strings.cancelAction) }
                Text(strings.previewPaused.replace("%s", state.verseNumber.toString()), style = MaterialTheme.typography.labelSmall)
            }
            is PreviewState.MissingAudio -> {
                Text(strings.previewMissingAudio.replace("%s", state.verseNumber.toString()), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onStop) { Text(strings.cancelAction) }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewBarIdleArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    PreviewBar(PreviewState.Idle, {}, {}, {}, {})
}

@Preview
@Composable
private fun PreviewBarIdleEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    PreviewBar(PreviewState.Idle, {}, {}, {}, {})
}

@Preview
@Composable
private fun PreviewBarPlayingArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    PreviewBar(PreviewState.Playing(3, 12_000), {}, {}, {}, {})
}

@Preview
@Composable
private fun PreviewBarPlayingEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    PreviewBar(PreviewState.Playing(3, 12_000), {}, {}, {}, {})
}

@Preview
@Composable
private fun PreviewBarMissingAudioArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    PreviewBar(PreviewState.MissingAudio(5), {}, {}, {}, {})
}

@Preview
@Composable
private fun PreviewBarMissingAudioEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    PreviewBar(PreviewState.MissingAudio(5), {}, {}, {}, {})
}
