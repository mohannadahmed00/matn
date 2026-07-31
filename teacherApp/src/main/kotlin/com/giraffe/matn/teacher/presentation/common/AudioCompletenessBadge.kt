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

/** Library rows, editor header, publish dialog (`contracts/teacher-ui-contract.md` §4). When
 * [recordedCount]/[totalCount] are known, `PARTIAL` reads "12 of 109 recorded" (FR-031) instead of
 * the plain label — the library row's overview projection doesn't carry per-verse audio state
 * (FR-012/FR-035 keep it off that read), so it passes neither and falls back to the plain label;
 * the editor header and publish dialog have the full draft and pass both. */
@Composable
fun AudioCompletenessBadge(state: AudioCompleteness, recordedCount: Int? = null, totalCount: Int? = null, modifier: Modifier = Modifier) {
    val strings = LocalTeacherStrings.current
    val label = if (state == AudioCompleteness.PARTIAL && recordedCount != null && totalCount != null) {
        strings.audioCompletenessPartialCount.replaceFirst("%d", recordedCount.toString()).replaceFirst("%d", totalCount.toString())
    } else {
        when (state) {
            AudioCompleteness.NONE -> strings.audioCompletenessNone
            AudioCompleteness.PARTIAL -> strings.audioCompletenessPartial
            AudioCompleteness.COMPLETE -> strings.audioCompletenessComplete
        }
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
private fun AudioCompletenessBadgePartialCountArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    AudioCompletenessBadge(AudioCompleteness.PARTIAL, recordedCount = 12, totalCount = 109)
}

@Preview
@Composable
private fun AudioCompletenessBadgePartialCountEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    AudioCompletenessBadge(AudioCompleteness.PARTIAL, recordedCount = 12, totalCount = 109)
}

@Preview
@Composable
private fun AudioCompletenessBadgeCompletePreview() = PreviewScaffold(TeacherLanguage.ARABIC) { AudioCompletenessBadge(AudioCompleteness.COMPLETE) }
