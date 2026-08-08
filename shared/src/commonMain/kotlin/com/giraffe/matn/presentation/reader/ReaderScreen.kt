package com.giraffe.matn.presentation.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.model.PlaybackMode
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.common.BackGlyph
import com.giraffe.matn.presentation.common.autoIsolated
import com.giraffe.matn.presentation.common.ltrIsolated
import com.giraffe.matn.presentation.details.VerseRow
import com.giraffe.matn.presentation.notes.NoteEditorSheet
import com.giraffe.matn.presentation.player.PlayerBar
import com.giraffe.matn.presentation.player.PlayerBarViewModel
import com.giraffe.matn.presentation.player.ReadingCarousel
import com.giraffe.matn.presentation.player.RepetitionSetupHost
import com.giraffe.matn.presentation.player.windowVersesForCarousel
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.back
import matn.shared.generated.resources.error_matn_not_found
import matn.shared.generated.resources.error_storage
import matn.shared.generated.resources.font_size
import matn.shared.generated.resources.mode_ab_loop
import matn.shared.generated.resources.mode_memorization
import matn.shared.generated.resources.mode_normal
import matn.shared.generated.resources.reader_matn_repeat_short
import matn.shared.generated.resources.reader_mode_edit
import matn.shared.generated.resources.reader_verse_repeat_short
import matn.shared.generated.resources.reader_no_verse
import org.jetbrains.compose.resources.stringResource

/**
 * The reader route (Matn Design System §05, *Reader*) — **stateful** entry.
 *
 * The one screen that earns full-screen status: it owns a scroll position, a back-stack entry, and
 * the deep link Continue Learning resolves through. It used to be a mode of the details screen,
 * entered as a side effect of playback starting, which meant there was no state to come back to
 * when playback stopped — the screen simply became a different screen underneath the student.
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    playerBar: PlayerBarViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReaderContent(
        state = state,
        playerBar = playerBar,
        onBack = onBack,
        onFontSizeChanged = viewModel::onFontSizeChanged,
        onToggleBookmark = viewModel::onToggleBookmark,
        onOpenNoteEditor = viewModel::onOpenNoteEditor,
        onSaveNote = viewModel::onSaveNote,
        onDeleteNote = viewModel::onDeleteNote,
        onDismissNoteEditor = viewModel::onDismissNoteEditor,
        onToggleMemorized = viewModel::onToggleMemorized,
        onSetLoopStart = viewModel::onSetLoopStart,
        onSetLoopEnd = viewModel::onSetLoopEnd,
        onClearLoop = viewModel::onClearLoop,
    )
}

/**
 * Stateless reader: a two-line top bar (matn, then the active verse's chapter), the three-verse
 * focus carousel, and the player bar pinned to the foot. The repetition sheet and the note editor
 * are the only things that ever cover it.
 */
@Composable
fun ReaderContent(
    state: ReaderUiState,
    playerBar: PlayerBarViewModel? = null,
    onBack: () -> Unit = {},
    onFontSizeChanged: (ReadingFontSize) -> Unit = {},
    onToggleBookmark: (String) -> Unit = {},
    onOpenNoteEditor: (String) -> Unit = {},
    onSaveNote: (String) -> Unit = {},
    onDeleteNote: () -> Unit = {},
    onDismissNoteEditor: () -> Unit = {},
    onToggleMemorized: (String) -> Unit = {},
    onSetLoopStart: (String) -> Unit = {},
    onSetLoopEnd: (String) -> Unit = {},
    onClearLoop: () -> Unit = {},
) {
    // T090 (US4, FR-030): rememberSaveable so rotation doesn't silently close an open sheet.
    var repetitionSheetOpen by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )

            state.error != null -> CenteredMessage(errorMessage(state.error))

            else -> Column(modifier = Modifier.fillMaxSize()) {
                ReaderTopBar(
                    title = state.matnTitle,
                    subtitle = state.chapterTitle,
                    fontSize = state.fontSize,
                    onFontSizeChanged = onFontSizeChanged,
                    onBack = onBack,
                )
                // remember: windowVersesForCarousel scans `verses` — this screen's single bundled
                // state recomposes on unrelated changes (an annotation toggle, a position tick), so
                // without this it re-scans on every one of those rather than only when the two
                // actual inputs change.
                val carouselState = remember(state.verses, state.focusedVerseId) {
                    windowVersesForCarousel(verses = state.verses, activeVerseId = state.focusedVerseId)
                }
                if (carouselState != null) {
                    ReadingCarousel(
                        state = carouselState,
                        fontSize = state.fontSize,
                        annotations = state.annotations,
                        memorizedVerseIds = state.memorizedVerseIds,
                        onToggleBookmark = onToggleBookmark,
                        onOpenNoteEditor = onOpenNoteEditor,
                        onToggleMemorized = onToggleMemorized,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // No session and no resolvable requested verse. Reachable only from a stale
                    // deep link, so it says so rather than rendering an empty reading surface that
                    // looks like a failed load.
                    Box(modifier = Modifier.weight(1f)) {
                        CenteredMessage(stringResource(Res.string.reader_no_verse))
                    }
                }
                if (playerBar != null) {
                    ModeStrip(
                        playerBar = playerBar,
                        onEdit = { repetitionSheetOpen = true },
                    )
                    PlayerBar(
                        viewModel = playerBar,
                        onRepeatSettingsClicked = { repetitionSheetOpen = true },
                    )
                }
            }
        }
        if (playerBar != null) {
            RepetitionSetupHost(
                visible = repetitionSheetOpen,
                onDismiss = { repetitionSheetOpen = false },
                verses = state.verses,
                playerBar = playerBar,
                onSetLoopStart = onSetLoopStart,
                onSetLoopEnd = onSetLoopEnd,
                onClearLoop = onClearLoop,
                onStartPlayback = {},
            )
        }
        val noteEditor = state.noteEditor
        if (noteEditor != null) {
            // Local draft state, seeded from the prefill once GetNoteUseCase resolves (Principle II:
            // nothing here is a ViewModel call until the user explicitly saves or deletes).
            // T091 (US4, FR-030): rememberSaveable so typed-but-unsaved text survives a rotation.
            var draft by rememberSaveable(noteEditor.verseId, noteEditor.initialText) {
                mutableStateOf(noteEditor.initialText.orEmpty())
            }
            NoteEditorSheet(
                verseRef = noteEditor.verseRef,
                initialText = noteEditor.initialText,
                draft = draft,
                onDraftChange = { draft = it },
                onSave = { onSaveNote(draft) },
                onDelete = onDeleteNote,
                onDismiss = onDismissNoteEditor,
            )
        }
    }
}

/**
 * Matn on the first line, the active verse's chapter on the second. The chapter line is what makes
 * the immersive surface locatable — three verses with no context could be from anywhere in a
 * 300-verse matn — and it is simply absent for a SIMPLE matn rather than reserving empty space.
 */
@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String?,
    fontSize: ReadingFontSize,
    onFontSizeChanged: (ReadingFontSize) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onBack) {
            BackGlyph(color = scheme.onSurface, contentDescription = stringResource(Res.string.back))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The design puts the "أ" size control in the reader's top bar, and only there among the
        // reading surfaces: this is where a size that is wrong is actually felt, so it is where
        // changing it costs nothing. Settings still owns the same preference for anyone who goes
        // looking for it — both write the same use case, so they cannot disagree.
        ReaderFontSizeControl(fontSize = fontSize, onFontSizeChanged = onFontSizeChanged)
    }
}

/**
 * Cycles the reading size one stop per tap, wrapping at the top. A cycling control rather than a
 * menu because there are four stops and the result is visible behind the tap — reading the change
 * is faster than reading a list of options.
 */
@Composable
private fun ReaderFontSizeControl(fontSize: ReadingFontSize, onFontSizeChanged: (ReadingFontSize) -> Unit) {
    val label = stringResource(Res.string.font_size)
    IconButton(
        onClick = {
            val next = ReadingFontSize.entries[(fontSize.ordinal + 1) % ReadingFontSize.entries.size]
            onFontSizeChanged(next)
        },
        modifier = Modifier.semantics {
            contentDescription = label
            stateDescription = fontSize.name
        },
    ) {
        Text(
            text = "أ",
            fontFamily = verseFontFamily(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The repetition summary the design puts directly above the transport: what mode is running and how
 * far through its counts, plus one way in to change it.
 *
 * It exists because the transport alone cannot say *why* a verse is repeating. A student mid-drill
 * seeing the same verse a fourth time needs to know that is the setting working, not the player
 * stuck — that reassurance is the whole job of this strip.
 */
@Composable
private fun ModeStrip(playerBar: PlayerBarViewModel, onEdit: () -> Unit) {
    val barState by playerBar.state.collectAsStateWithLifecycle()
    if (!barState.visible) return
    val scheme = MaterialTheme.colorScheme
    val mode = stringResource(
        when (barState.mode) {
            PlaybackMode.NORMAL -> Res.string.mode_normal
            PlaybackMode.MEMORIZATION -> Res.string.mode_memorization
            PlaybackMode.A_B_LOOP -> Res.string.mode_ab_loop
        },
    )
    // Isolated as a whole (BidiText.kt § Composites): three counts separated by neutral "·" would
    // otherwise be reordered against each other by the paragraph direction.
    val summary = autoIsolated(
        mode +
            " · " + stringResource(Res.string.reader_verse_repeat_short, ltrIsolated(repeatLabel(barState.verseRepeatTarget))) +
            " · " + stringResource(Res.string.reader_matn_repeat_short, ltrIsolated(repeatLabel(barState.matnRepeatTarget))),
    )
    Surface(color = scheme.surfaceContainerLow) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = MatnSpacing.marginMobile, vertical = MatnSpacing.unit),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.reader_mode_edit),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
            )
        }
    }
}

/** `∞` for an unbounded repeat, the count otherwise — the strip has room for one glyph, not a word. */
private fun repeatLabel(count: RepeatCount): String = when (count) {
    is RepeatCount.Unlimited -> "∞"
    is RepeatCount.Finite -> count.value.toString()
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(MatnSpacing.gutter),
        )
    }
}

@Composable
private fun errorMessage(error: AppError?): String = when (error) {
    is AppError.NotFound -> stringResource(Res.string.error_matn_not_found)
    else -> stringResource(Res.string.error_storage)
}

// --------------------------------------------------------------------------- Previews
//
// Verse previews use the default font family via MatnTheme; the carousel loads Amiri itself.

private fun previewVerse(id: String, number: Int, text: String) =
    VerseRow(id = id, displayNumber = number, arabicText = text, durationMs = 8_000, chapterId = "c1")

private val previewVerses = listOf(
    previewVerse("v1", 1, "الكَلامُ هُوَ اللَّفْظُ المُرَكَّبُ المُفِيدُ بِالوَضْعِ"),
    previewVerse("v2", 2, "وَأَقْسَامُهُ ثَلاثَةٌ: اسْمٌ، وَفِعْلٌ، وَحَرْفٌ جَاءَ لِمَعْنًى"),
    previewVerse("v3", 3, "فَالاسْمُ يُعْرَفُ بِالخَفْضِ، وَالتَّنْوِينِ، وَدُخُولِ الأَلِفِ وَاللَّامِ"),
)

private val previewState = ReaderUiState(
    isLoading = false,
    matnTitle = "الأجرومية",
    chapterTitle = "باب الكلام",
    verses = previewVerses,
    activeVerseId = "v2",
    isPlaying = true,
)

@Preview
@Composable
private fun ReaderContentPreview() {
    MatnTheme { ReaderContent(state = previewState) }
}

@Preview
@Composable
private fun ReaderContentDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) { ReaderContent(state = previewState) }
}

/** A SIMPLE matn — the top bar renders one line rather than reserving space for a chapter. */
@Preview
@Composable
private fun ReaderContentNoChapterPreview() {
    MatnTheme { ReaderContent(state = previewState.copy(chapterTitle = null)) }
}

/** Opened at the first verse: the carousel has no previous neighbour to dim. */
@Preview
@Composable
private fun ReaderContentFirstVersePreview() {
    MatnTheme { ReaderContent(state = previewState.copy(activeVerseId = "v1")) }
}

/** Idle, from the route's `?v=` rather than a live session. */
@Preview
@Composable
private fun ReaderContentRequestedVersePreview() {
    MatnTheme {
        ReaderContent(state = previewState.copy(activeVerseId = null, requestedVerseId = "v3"))
    }
}

/** A stale deep link: nothing to focus, said plainly instead of rendering a blank reading surface. */
@Preview
@Composable
private fun ReaderContentNoVersePreview() {
    MatnTheme {
        ReaderContent(state = previewState.copy(activeVerseId = null, requestedVerseId = null))
    }
}

@Preview
@Composable
private fun ReaderContentLoadingPreview() {
    MatnTheme { ReaderContent(state = ReaderUiState(isLoading = true)) }
}

@Preview
@Composable
private fun ReaderContentErrorPreview() {
    MatnTheme {
        ReaderContent(state = ReaderUiState(isLoading = false, error = AppError.NotFound))
    }
}

/** Largest reachable font scale, narrowest width (accessibility-contract.md §5/§7). */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun ReaderContentMaxScalePreview() {
    MatnTheme { ReaderContent(state = previewState.copy(fontSize = ReadingFontSize.XLARGE)) }
}

/** Expanded window width — the carousel stays bounded and centred (FR-028). */
@Preview(widthDp = 900)
@Composable
private fun ReaderContentWidePreview() {
    MatnTheme { ReaderContent(state = previewState) }
}
