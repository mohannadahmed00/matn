package com.giraffe.matn.teacher.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

enum class ProblemSeverity { BLOCKING, DEFERRED }

/** One validation problem, clickable to scroll to and focus its subject (verse/chapter, FR-028). */
@Composable
fun ProblemRow(message: String, severity: ProblemSeverity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MatnSpacing.unit, horizontal = MatnSpacing.unit * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(MatnSpacing.unit)
                .background(
                    if (severity == ProblemSeverity.BLOCKING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    CircleShape,
                ),
        )
        Text(
            text = message,
            modifier = Modifier.padding(start = MatnSpacing.unit * 2),
            color = if (severity == ProblemSeverity.BLOCKING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun ProblemRowBlockingPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    ProblemRow("Verses 3 and 7 have the same number", ProblemSeverity.BLOCKING, {})
}

@Preview
@Composable
private fun ProblemRowDeferredPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    ProblemRow("Verse 3 has no recording yet", ProblemSeverity.DEFERRED, {})
}
