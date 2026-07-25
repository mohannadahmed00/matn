package com.giraffe.matn.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.giraffe.matn.presentation.common.MemorizedGlyph
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.mark_chapter_memorized
import matn.shared.generated.resources.toc_header
import matn.shared.generated.resources.unmark_chapter_memorized
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Table of contents panel (US3). Lists [ChapterRow]s in their authored order under the localized
 * `toc_header`, each led by a small gold marker echoing the reading screen's rosette. Selecting a
 * chapter invokes [onChapterSelected]; the owning screen scrolls the verse list to that chapter's
 * [ChapterRow.firstVerseDisplayNumber] (FR-014/SC-004). Rendered **only** for a STRUCTURED matn
 * with chapters (FR-015) — the calling screen gates on `MatnDetailsUiState.showTableOfContents`.
 *
 * Phase 7 (FR-003): each row also carries a "mark entire chapter" action — [isChapterMemorized]
 * reports whether every verse in the chapter is already memorized, and tapping the glyph invokes
 * [onMarkChapterMemorized] with the inverse of that state.
 */
@Composable
fun TableOfContents(
    chapters: List<ChapterRow>,
    onChapterSelected: (ChapterRow) -> Unit,
    modifier: Modifier = Modifier,
    isChapterMemorized: (ChapterRow) -> Boolean = { false },
    onMarkChapterMemorized: (String, Boolean) -> Unit = { _, _ -> },
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = MatnSpacing.unit + 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(Res.string.toc_header),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = MatnSpacing.unit / 2),
        )
        chapters.forEach { chapter ->
            val memorized = isChapterMemorized(chapter)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // T059 (US2): content row — chapter.title is already merged from the
                    // descendant Text; onClickLabel just names the tap's purpose.
                    .clickable(onClickLabel = chapter.title) { onChapterSelected(chapter) }
                    .padding(vertical = MatnSpacing.unit + 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape),
                )
                Spacer(modifier = Modifier.width(MatnSpacing.unit + 4.dp))
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMarkChapterMemorized(chapter.id, !memorized) }) {
                    MemorizedGlyph(
                        color = if (memorized) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        filled = memorized,
                        contentDescription = stringResource(
                            if (memorized) Res.string.unmark_chapter_memorized else Res.string.mark_chapter_memorized,
                        ),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            isChapterMemorized = { it.id == "c1" },
        )
    }
}
