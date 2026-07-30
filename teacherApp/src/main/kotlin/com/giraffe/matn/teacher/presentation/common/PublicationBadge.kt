package com.giraffe.matn.teacher.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** Library rows + editor header (`contracts/teacher-ui-contract.md` §4). */
@Composable
fun PublicationBadge(state: PublicationState, modifier: Modifier = Modifier) {
    val strings = LocalTeacherStrings.current
    val (label, containerColor, contentColor) = when (state) {
        PublicationState.PUBLISHED ->
            Triple(strings.publicationStatusPublished, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        PublicationState.DRAFT ->
            Triple(strings.publicationStatusDraft, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        text = label,
        color = contentColor,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.background(containerColor, MatnShapes.full).padding(horizontal = MatnSpacing.unit * 1.5f, vertical = MatnSpacing.unit / 2),
    )
}

@Preview
@Composable
private fun PublicationBadgeDraftPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) { PublicationBadge(PublicationState.DRAFT) }

@Preview
@Composable
private fun PublicationBadgePublishedPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) { PublicationBadge(PublicationState.PUBLISHED) }

@Preview
@Composable
private fun PublicationBadgeArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) { PublicationBadge(PublicationState.PUBLISHED) }
