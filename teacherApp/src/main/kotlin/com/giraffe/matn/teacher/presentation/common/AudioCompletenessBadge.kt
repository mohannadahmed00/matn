package com.giraffe.matn.teacher.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.catalog.AudioCompleteness
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** Library rows, editor header, publish dialog (`contracts/teacher-ui-contract.md` §4). */
@Composable
fun AudioCompletenessBadge(state: AudioCompleteness, modifier: Modifier = Modifier) {
    val strings = LocalTeacherStrings.current
    val label = when (state) {
        AudioCompleteness.NONE -> strings.audioCompletenessNone
        AudioCompleteness.PARTIAL -> strings.audioCompletenessPartial
        AudioCompleteness.COMPLETE -> strings.audioCompletenessComplete
    }
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MatnShapes.full)
            .padding(horizontal = MatnSpacing.unit * 1.5f, vertical = MatnSpacing.unit / 2),
    )
}

@Preview
@Composable
private fun AudioCompletenessBadgeNonePreview() = PreviewScaffold(TeacherLanguage.ENGLISH) { AudioCompletenessBadge(AudioCompleteness.NONE) }

@Preview
@Composable
private fun AudioCompletenessBadgePartialPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) { AudioCompletenessBadge(AudioCompleteness.PARTIAL) }

@Preview
@Composable
private fun AudioCompletenessBadgeCompletePreview() = PreviewScaffold(TeacherLanguage.ARABIC) { AudioCompletenessBadge(AudioCompleteness.COMPLETE) }
