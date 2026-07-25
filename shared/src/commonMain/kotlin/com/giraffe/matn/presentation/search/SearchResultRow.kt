package com.giraffe.matn.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily

/**
 * One shared, parameterized row family for the three [SearchResult] kinds (T003 design notes;
 * Constitution VIII — one component, not three near-duplicates). Each variant renders the same
 * title-first hierarchy the fetched *Search Matn* design uses, adapted to what each match kind
 * actually carries (matn title only / matn+chapter title / matn+verse-number+excerpt).
 */
@Composable
fun SearchResultRow(result: SearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    when (result) {
        is SearchResult.MatnMatch -> MatnMatchRow(result, onClick, modifier)
        is SearchResult.ChapterMatch -> ChapterMatchRow(result, onClick, modifier)
        is SearchResult.VerseMatch -> VerseMatchRow(result, onClick, modifier)
    }
}

/** T059 (US2): [onClickLabel] names the tap's target — each variant's own text is already merged
 *  into the accessible name via descendant [Text]s, so this only supplies the purpose hint. */
@Composable
private fun RowShell(
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = MatnSpacing.marginMobile, vertical = MatnSpacing.unit * 2),
        content = content,
    )
}

@Composable
private fun MatnMatchRow(result: SearchResult.MatnMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RowShell(onClick, onClickLabel = result.matnTitle, modifier = modifier) {
        Text(
            text = result.matnTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChapterMatchRow(result: SearchResult.ChapterMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RowShell(onClick, onClickLabel = "${result.matnTitle} ${result.chapterTitle}", modifier = modifier) {
        Text(
            text = result.matnTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = result.chapterTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = MatnSpacing.unit / 2),
        )
    }
}

@Composable
private fun VerseMatchRow(result: SearchResult.VerseMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RowShell(onClick, onClickLabel = "${result.ref.matnTitle} ${result.ref.verseNumber}", modifier = modifier) {
        Text(
            text = "${result.ref.matnTitle} · ${result.ref.verseNumber}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = result.ref.verseText,
            fontFamily = verseFontFamily(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MatnSpacing.unit / 2),
        )
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun SearchResultRowVersePreview() {
    MatnTheme {
        SearchResultRow(
            result = SearchResult.VerseMatch(
                AnnotatedVerseRef(
                    matnId = "m1",
                    matnTitle = "الأجرومية",
                    verseId = "v1",
                    verseNumber = 2,
                    verseText = "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ",
                ),
            ),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun SearchResultRowChapterPreview() {
    MatnTheme {
        SearchResultRow(
            result = SearchResult.ChapterMatch(
                matnId = "m1",
                matnTitle = "متن الآجرومية مبوب",
                chapterId = "c1",
                chapterTitle = "باب الكلام",
                firstVerseId = "v1",
            ),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun SearchResultRowMatnPreview() {
    MatnTheme {
        SearchResultRow(
            result = SearchResult.MatnMatch(matnId = "m1", matnTitle = "الأجرومية"),
            onClick = {},
        )
    }
}
