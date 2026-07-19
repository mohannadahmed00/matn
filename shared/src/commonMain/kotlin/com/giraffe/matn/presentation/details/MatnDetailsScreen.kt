package com.giraffe.matn.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.presentation.common.CoverImage
import com.giraffe.matn.presentation.common.formatDuration
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.toSp
import com.giraffe.matn.presentation.theme.verseFontFamily
import kotlinx.coroutines.launch
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.error_matn_not_found
import matn.shared.generated.resources.error_storage
import matn.shared.generated.resources.font_large
import matn.shared.generated.resources.font_medium
import matn.shared.generated.resources.font_small
import matn.shared.generated.resources.font_xlarge
import matn.shared.generated.resources.verses_count
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Reading / details screen (US1/US3/US4) — **stateful** entry. Hoists the ViewModel's state and
 * delegates rendering to the stateless [MatnDetailsContent], which is a pure function of
 * [MatnDetailsUiState] (Principle II) and is previewable without a live ViewModel.
 */
@Composable
fun MatnDetailsScreen(viewModel: MatnDetailsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MatnDetailsContent(state = state, onFontSizeChanged = viewModel::onFontSizeChanged)
}

/**
 * Stateless reading surface. Renders the header (cover + title/author/description + totals)
 * followed by the verse list in matn-global `displayNumber` order (FR-006), keyed by stable
 * verse id (FR-011/SC-003). Verse text uses the bundled Amiri `FontFamily`, sized from the
 * persisted font-size preference (FR-016), aligned start (RTL via [MatnTheme]), wrapping fully —
 * diacritics intact and never clipped/normalized (FR-008/SC-002). For a STRUCTURED matn the
 * table of contents (US3) is the first list item; selecting a chapter scrolls to its first verse
 * (FR-014/SC-004). No network (FR-018/SC-006).
 */
@Composable
fun MatnDetailsContent(
    state: MatnDetailsUiState,
    onFontSizeChanged: (ReadingFontSize) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
            state.error != null -> Text(
                text = errorMessage(state.error),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                textAlign = TextAlign.Center,
            )
            else -> VerseList(state, onFontSizeChanged = onFontSizeChanged)
        }
    }
}

@Composable
private fun VerseList(state: MatnDetailsUiState, onFontSizeChanged: (ReadingFontSize) -> Unit = {}) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val header = state.header
    val verses = state.verses
    val fontSize = state.fontSize.toSp()
    val verseFont = verseFontFamily()

    // Header occupies item index 0; the TOC panel (when shown) occupies index 1; verses start
    // after that. SC-004 scroll target uses these offsets.
    val tocIndex = if (state.showTableOfContents && state.chapters.isNotEmpty()) 1 else -1
    val firstVerseIndex = if (tocIndex >= 0) tocIndex + 1 else 1

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 24.dp,
        ),
    ) {
        if (header != null) {
            item(key = "header") { Header(header, state.fontSize, onFontSizeChanged) }
        }
        if (tocIndex >= 0) {
            item(key = "toc") {
                TableOfContents(
                    chapters = state.chapters,
                    onChapterSelected = { chapter ->
                        // FR-014/SC-004: scroll the chapter's first verse into view.
                        val verseOffset = verses.indexOfFirst { it.displayNumber == chapter.firstVerseDisplayNumber }
                        if (verseOffset >= 0) {
                            val target = firstVerseIndex + verseOffset
                            coroutineScope.launch { listState.animateScrollToItem(target) }
                        }
                    },
                )
            }
        }
        items(items = verses, key = { v -> v.id }) { row ->
            VerseRowItem(row = row, fontSize = fontSize, verseFont = verseFont)
            HorizontalDivider()
        }
    }
}

@Composable
private fun Header(
    header: MatnHeader,
    fontSize: ReadingFontSize,
    onFontSizeChanged: (ReadingFontSize) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        CoverImage(
            coverImageRef = header.coverImageRef,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FontSizeChooser(fontSize = fontSize, onFontSizeChanged = onFontSizeChanged)
        }
        Text(
            text = header.author,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (header.description.isNotBlank()) {
            Text(
                text = header.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        val totals = stringResource(Res.string.verses_count, header.verseCount) +
            " · " + formatDuration(header.totalDurationMs)
        Text(
            text = totals,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun FontSizeChooser(
    fontSize: ReadingFontSize,
    onFontSizeChanged: (ReadingFontSize) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Text(
                text = "A",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FontSizeMenuItem(label = stringResource(Res.string.font_small), isSelected = fontSize == ReadingFontSize.SMALL) {
                onFontSizeChanged(ReadingFontSize.SMALL); expanded = false
            }
            FontSizeMenuItem(label = stringResource(Res.string.font_medium), isSelected = fontSize == ReadingFontSize.MEDIUM) {
                onFontSizeChanged(ReadingFontSize.MEDIUM); expanded = false
            }
            FontSizeMenuItem(label = stringResource(Res.string.font_large), isSelected = fontSize == ReadingFontSize.LARGE) {
                onFontSizeChanged(ReadingFontSize.LARGE); expanded = false
            }
            FontSizeMenuItem(label = stringResource(Res.string.font_xlarge), isSelected = fontSize == ReadingFontSize.XLARGE) {
                onFontSizeChanged(ReadingFontSize.XLARGE); expanded = false
            }
        }
    }
}

@Composable
private fun FontSizeMenuItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val marker = if (isSelected) " ✓" else ""
    DropdownMenuItem(
        text = { Text(label + marker) },
        onClick = onClick,
    )
}

@Composable
private fun errorMessage(error: AppError?): String = when (error) {
    AppError.NotFound -> stringResource(Res.string.error_matn_not_found)
    is AppError.Storage -> stringResource(Res.string.error_storage)
    null -> ""
    else -> stringResource(Res.string.error_storage)
}

@Composable
private fun VerseRowItem(
    row: VerseRow,
    fontSize: TextUnit,
    verseFont: FontFamily,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "${row.displayNumber}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = row.arabicText,
            fontFamily = verseFont,
            fontSize = fontSize,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
        Text(
            text = formatDuration(row.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — every view/component in this file has one (light Material 3, RTL via MatnTheme).
// The verse previews use FontFamily.Default so they render without loading the Amiri resource.
// ---------------------------------------------------------------------------------------------

private val previewHeader = MatnHeader(
    coverImageRef = null,
    title = "الأجرومية",
    author = "ابن آجُرُّوم",
    description = "متن مختصر في علم النحو",
    verseCount = 3,
    totalDurationMs = 22_500,
)

private val previewVerses = listOf(
    VerseRow("v1", 1, "الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ", 8200, null),
    VerseRow("v2", 2, "وَأَقسامُهُ ثَلاثَةٌ: اِسمٌ، وَفِعلٌ، وَحَرفٌ جاءَ لِمَعنىً", 7400, null),
    VerseRow("v3", 3, "فَالاسمُ يُعرَفُ بِالخَفضِ وَالتَنوينِ", 6900, null),
)

private val previewChapters = listOf(
    ChapterRow("c1", "باب الكلام", 1, 1),
    ChapterRow("c2", "باب الإعراب", 2, 3),
)

@Preview
@Composable
private fun MatnDetailsSimplePreview() {
    MatnTheme {
        MatnDetailsContent(
            state = MatnDetailsUiState(
                isLoading = false,
                header = previewHeader,
                verses = previewVerses,
            ),
        )
    }
}

@Preview
@Composable
private fun MatnDetailsStructuredPreview() {
    MatnTheme {
        MatnDetailsContent(
            state = MatnDetailsUiState(
                isLoading = false,
                header = previewHeader.copy(title = "متن الآجرومية مبوب"),
                verses = previewVerses,
                chapters = previewChapters,
                showTableOfContents = true,
                fontSize = ReadingFontSize.LARGE,
            ),
        )
    }
}

@Preview
@Composable
private fun MatnDetailsErrorPreview() {
    MatnTheme {
        MatnDetailsContent(state = MatnDetailsUiState(isLoading = false, error = AppError.NotFound))
    }
}

@Preview
@Composable
private fun VerseRowItemPreview() {
    MatnTheme {
        VerseRowItem(row = previewVerses.first(), fontSize = 22.sp, verseFont = FontFamily.Default)
    }
}

@Preview
@Composable
private fun HeaderPreview() {
    MatnTheme {
        Header(header = previewHeader, fontSize = ReadingFontSize.MEDIUM, onFontSizeChanged = {})
    }
}
