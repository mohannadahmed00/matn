package com.giraffe.matn.presentation.saved

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.Bookmark
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.model.MemorizedEntry
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.NoteEntry
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.common.autoIsolated
import com.giraffe.matn.presentation.common.formatDate
import com.giraffe.matn.presentation.common.ltrIsolated
import com.giraffe.matn.presentation.theme.LocalMatnSemantics
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.nav_saved
import matn.shared.generated.resources.saved_empty_all
import matn.shared.generated.resources.saved_empty_memorized
import matn.shared.generated.resources.saved_empty_notes
import matn.shared.generated.resources.saved_filter_all
import matn.shared.generated.resources.saved_filter_memorized
import matn.shared.generated.resources.saved_filter_notes
import matn.shared.generated.resources.saved_kind_bookmark
import matn.shared.generated.resources.saved_kind_memorized
import matn.shared.generated.resources.saved_kind_note
import matn.shared.generated.resources.saved_memorized_on
import matn.shared.generated.resources.saved_removed
import matn.shared.generated.resources.saved_remove_row
import matn.shared.generated.resources.saved_undo
import org.jetbrains.compose.resources.stringResource

/**
 * The Saved tab (Matn Design System §05 — *Saved · three former screens, one route*) — **stateful**
 * entry. Successor to the Bookmarks & Notes tab: bookmarks, notes and memorized verses are all
 * "verses I have touched", so they are one route filtered in place rather than three destinations.
 */
@Composable
fun SavedScreen(viewModel: SavedViewModel, onNavigateToVerse: (matnId: String, verseId: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { (matnId, verseId) -> onNavigateToVerse(matnId, verseId) }
    }
    SavedContent(
        state = state,
        onFilterSelected = viewModel::onFilterSelected,
        onRowClick = viewModel::onRowClick,
        onRemove = viewModel::onRemove,
        onUndo = viewModel::onUndo,
        onUndoDismissed = viewModel::onUndoDismissed,
    )
}

/**
 * Stateless Saved surface: a title, a three-segment filter that never navigates, and one list of
 * mixed rows. Swiping a row toward the layout's end edge removes it and raises an Undo snackbar
 * (design system: "Swipe-inline-end deletes with a 4s Undo snackbar").
 *
 * Each segment has its own empty state — "nothing saved at all" and "no notes yet" are different
 * situations and the second must not read as the first.
 */
@Composable
fun SavedContent(
    state: SavedUiState,
    onFilterSelected: (SavedFilter) -> Unit = {},
    onRowClick: (SavedRow) -> Unit = {},
    onRemove: (SavedRow) -> Unit = {},
    onUndo: () -> Unit = {},
    onUndoDismissed: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val removedMessage = stringResource(Res.string.saved_removed)
    val undoLabel = stringResource(Res.string.saved_undo)

    // The snackbar is driven by state, not by the swipe callback: `pendingRemoval` is set only
    // once the delete has actually succeeded, so an Undo is never offered for a removal that
    // did not happen.
    LaunchedEffect(state.pendingRemoval) {
        val pending = state.pendingRemoval ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = removedMessage,
            actionLabel = undoLabel,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onUndo()
            SnackbarResult.Dismissed -> onUndoDismissed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Bounded to the reading measure and centred on wide windows (FR-028).
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MatnSpacing.readingMaxWidth),
        ) {
            Text(
                text = stringResource(Res.string.nav_saved),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MatnSpacing.marginMobile)
                    .padding(top = MatnSpacing.unit, bottom = MatnSpacing.cozy),
            )
            SavedFilterRow(
                selected = state.filter,
                counts = { filter -> state.count(filter) },
                onFilterSelected = onFilterSelected,
                modifier = Modifier.padding(horizontal = MatnSpacing.marginMobile),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isEmpty) {
                    SavedEmptyState(filter = state.filter, modifier = Modifier.align(Alignment.TopCenter))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = MatnSpacing.marginMobile,
                            end = MatnSpacing.marginMobile,
                            top = MatnSpacing.cozy,
                            bottom = MatnSpacing.gutter,
                        ),
                        verticalArrangement = Arrangement.spacedBy(MatnSpacing.snug),
                    ) {
                        items(items = state.rows, key = { it.key }) { row ->
                            SwipeableSavedRow(
                                row = row,
                                onClick = { onRowClick(row) },
                                onRemove = { onRemove(row) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The three-segment filter. Hand-built from the token set rather than `SegmentedButton`: the design
 * specifies one fully-round outlined track with hairline dividers and no per-segment corner
 * rounding, which M3's segmented buttons do not produce.
 */
@Composable
private fun SavedFilterRow(
    selected: SavedFilter,
    counts: (SavedFilter) -> Int,
    onFilterSelected: (SavedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MatnShapes.full,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            SavedFilter.entries.forEachIndexed { index, filter ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(scheme.outline),
                    )
                }
                SavedFilterSegment(
                    filter = filter,
                    count = counts(filter),
                    isSelected = filter == selected,
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SavedFilterSegment(
    filter: SavedFilter,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(
        when (filter) {
            SavedFilter.ALL -> Res.string.saved_filter_all
            SavedFilter.NOTES -> Res.string.saved_filter_notes
            SavedFilter.MEMORIZED -> Res.string.saved_filter_memorized
        },
    )
    // The label/count pair is isolated as a whole: the count is a weak-direction run and the space
    // between them is neutral, so without this the pair reorders under an RTL interface.
    val accessibleLabel = autoIsolated("$label ${ltrIsolated(count.toString())}")
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // Fill before click, so the ripple draws over the selected segment's tint rather
            // than under it.
            .background(if (isSelected) scheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = MatnSpacing.unit, vertical = MatnSpacing.snug)
            .semantics {
                contentDescription = accessibleLabel
                selected = isSelected
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = ltrIsolated(count.toString()),
            style = MaterialTheme.typography.labelSmall,
            color = (if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                .copy(alpha = 0.6f),
            maxLines = 1,
            modifier = Modifier.padding(start = MatnSpacing.hairline),
        )
    }
}

/** A row plus its swipe-to-remove wrapper. Only the end direction removes — a start swipe would
 *  need a second, different action to justify itself, and there isn't one. */
@Composable
private fun SwipeableSavedRow(row: SavedRow, onClick: () -> Unit, onRemove: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeRemoveBackground() },
    ) {
        SavedRowCard(row = row, onClick = onClick)
    }
}

@Composable
private fun SwipeRemoveBackground() {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(Res.string.saved_remove_row)
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.errorContainer, MatnShapes.lg)
            .padding(horizontal = MatnSpacing.cozy),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onErrorContainer,
        )
    }
}

/**
 * One saved verse: a kind tag, its matn/verse reference, the verse itself in Amiri, and a
 * kind-specific footer — the note's text, or the date practice confirmed the verse. Memorized rows
 * state *when*, never a percentage or a rank; that is what keeps the surface honest.
 */
@Composable
private fun SavedRowCard(row: SavedRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val semantics = LocalMatnSemantics.current
    val (tagLabel, tagContainer, tagContent) = when (row) {
        is SavedRow.Bookmarked -> Triple(
            stringResource(Res.string.saved_kind_bookmark),
            scheme.secondaryContainer,
            scheme.onSecondaryContainer,
        )

        is SavedRow.Noted -> Triple(
            stringResource(Res.string.saved_kind_note),
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        )

        is SavedRow.Memorized -> Triple(
            stringResource(Res.string.saved_kind_memorized),
            semantics.successContainer,
            semantics.onSuccessContainer,
        )
    }
    val reference = autoIsolated("${row.ref.matnTitle} · ${ltrIsolated(row.ref.verseNumber.toString())}")

    Surface(
        shape = MatnShapes.lg,
        color = scheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = reference, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = MatnSpacing.cozy, vertical = MatnSpacing.snug)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MatnShapes.extraSmall, color = tagContainer) {
                    Text(
                        text = tagLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = tagContent,
                        modifier = Modifier.padding(horizontal = MatnSpacing.unit, vertical = 2.dp),
                    )
                }
                Text(
                    text = reference,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = MatnSpacing.unit),
                )
            }
            Text(
                text = row.ref.verseText,
                fontFamily = verseFontFamily(),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MatnSpacing.unit),
            )
            when (row) {
                is SavedRow.Noted -> Text(
                    text = row.entry.note.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MatnSpacing.unit),
                )

                is SavedRow.Memorized -> Text(
                    text = stringResource(Res.string.saved_memorized_on, formatDate(row.entry.memorizedAtMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantics.success,
                    modifier = Modifier.padding(top = MatnSpacing.unit),
                )

                is SavedRow.Bookmarked -> Unit
            }
        }
    }
}

@Composable
private fun SavedEmptyState(filter: SavedFilter, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(
            when (filter) {
                SavedFilter.ALL -> Res.string.saved_empty_all
                SavedFilter.NOTES -> Res.string.saved_empty_notes
                SavedFilter.MEMORIZED -> Res.string.saved_empty_memorized
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.marginMobile, vertical = MatnSpacing.gutter * 2),
    )
}

// --------------------------------------------------------------------------- Previews

private fun previewRef(number: Int, text: String) = AnnotatedVerseRef(
    matnId = "m1",
    matnTitle = "الأجرومية",
    verseId = "v$number",
    verseNumber = number,
    verseText = text,
)

private val previewState = SavedUiState(
    bookmarks = listOf(
        BookmarkEntry(
            Bookmark("b1", "v3", 3_000),
            previewRef(3, "فَالاسْمُ يُعْرَفُ بِالخَفْضِ، وَالتَّنْوِينِ، وَدُخُولِ الأَلِفِ وَاللَّامِ"),
        ),
    ),
    notes = listOf(
        NoteEntry(
            Note("n1", "v4", "قال الشيخ: احفظ التسعة في نفَس واحد.", 2_000),
            previewRef(4, "وَحُرُوفُ الخَفْضِ وَهِيَ: مِنْ، وَإِلَى، وَعَنْ، وَعَلَى، وَفِي"),
        ),
    ),
    memorized = listOf(
        MemorizedEntry(
            id = "z1",
            memorizedAtMs = 1_000,
            ref = previewRef(1, "الكَلامُ هُوَ اللَّفْظُ المُرَكَّبُ المُفِيدُ بِالوَضْعِ"),
        ),
    ),
)

@Preview
@Composable
private fun SavedAllPreview() {
    MatnTheme { SavedContent(state = previewState) }
}

@Preview
@Composable
private fun SavedAllDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) { SavedContent(state = previewState) }
}

@Preview
@Composable
private fun SavedNotesSegmentPreview() {
    MatnTheme { SavedContent(state = previewState.copy(filter = SavedFilter.NOTES)) }
}

@Preview
@Composable
private fun SavedMemorizedSegmentPreview() {
    MatnTheme { SavedContent(state = previewState.copy(filter = SavedFilter.MEMORIZED)) }
}

@Preview
@Composable
private fun SavedEmptyPreview() {
    MatnTheme { SavedContent(state = SavedUiState()) }
}

/** A populated library with the Notes segment empty — the case the shared empty state used to hide. */
@Preview
@Composable
private fun SavedNotesEmptyPreview() {
    MatnTheme {
        SavedContent(state = previewState.copy(notes = emptyList(), filter = SavedFilter.NOTES))
    }
}

/** Expanded window width — confirms the list stays bounded/centred (FR-028). */
@Preview(widthDp = 900)
@Composable
private fun SavedWidePreview() {
    MatnTheme { SavedContent(state = previewState) }
}

/** Largest reachable font scale, narrowest width (accessibility-contract.md §5/§7). */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun SavedMaxScalePreview() {
    MatnTheme { SavedContent(state = previewState) }
}
