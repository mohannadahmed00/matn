package com.giraffe.matn.presentation.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.toc_header
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Table of contents panel (US3). Lists [ChapterRow]s in their authored order under the
 * localized `toc_header` title. Selecting a chapter invokes [onChapterSelected]; the owning
 * screen is responsible for scrolling the verse list to that chapter's [ChapterRow.firstVerseDisplayNumber]
 * (FR-014/SC-004). Rendered **only** for a STRUCTURED matn with chapters (FR-015) — the
 * calling screen gates visibility on `MatnDetailsUiState.showTableOfContents`.
 */
@Composable
fun TableOfContents(
    chapters: List<ChapterRow>,
    onChapterSelected: (ChapterRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(Res.string.toc_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        chapters.forEach { chapter ->
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChapterSelected(chapter) }
                    .padding(vertical = 10.dp),
            )
            HorizontalDivider()
        }
    }
}

@Preview
@Composable
private fun TableOfContentsPreview() {
    MatnTheme {
        TableOfContents(
            chapters = listOf(
                ChapterRow("c1", "باب الكلام", 1, 1),
                ChapterRow("c2", "باب الإعراب", 2, 3),
            ),
            onChapterSelected = {},
        )
    }
}