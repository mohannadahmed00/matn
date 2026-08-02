package com.giraffe.matn.presentation.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.presentation.common.A11yAction
import com.giraffe.matn.presentation.common.ContentActionButton
import com.giraffe.matn.presentation.common.CoverImage
import com.giraffe.matn.presentation.common.IconActionButton
import com.giraffe.matn.presentation.common.MatnProgressBar
import com.giraffe.matn.presentation.common.PlayGlyph
import com.giraffe.matn.presentation.common.formatBytes
import com.giraffe.matn.presentation.common.formatDuration
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.theme.LocalWindowWidthClass
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.toSp
import com.giraffe.matn.presentation.theme.verseFontFamily
import kotlinx.coroutines.launch
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.content_error_cancelled
import matn.shared.generated.resources.content_error_insufficient_storage
import matn.shared.generated.resources.content_error_no_connectivity
import matn.shared.generated.resources.content_error_unknown
import matn.shared.generated.resources.error_matn_not_found
import matn.shared.generated.resources.error_storage
import matn.shared.generated.resources.font_large
import matn.shared.generated.resources.font_medium
import matn.shared.generated.resources.font_small
import matn.shared.generated.resources.font_xlarge
import matn.shared.generated.resources.matn_progress_label
import matn.shared.generated.resources.player_play
import matn.shared.generated.resources.verses_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Reading / details screen (US1/US3/US4) — **stateful** entry. Hoists the ViewModel's state and
 * delegates rendering to the stateless [MatnDetailsContent], which is a pure function of
 * [MatnDetailsUiState] (Principle II) and is previewable without a live ViewModel.
 */
@Composable
fun MatnDetailsScreen(viewModel: MatnDetailsViewModel, playerBar: com.giraffe.matn.presentation.player.PlayerBarViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MatnDetailsContent(
        state = state,
        onFontSizeChanged = viewModel::onFontSizeChanged,
        onVersePlayClicked = viewModel::onVersePlayClicked,
        onGlobalPlayClicked = viewModel::onGlobalPlayClicked,
        onSetLoopStart = viewModel::onSetLoopStart,
        onSetLoopEnd = viewModel::onSetLoopEnd,
        onClearLoop = viewModel::onClearLoop,
        onToggleBookmark = viewModel::onToggleBookmark,
        onOpenNoteEditor = viewModel::onOpenNoteEditor,
        onSaveNote = viewModel::onSaveNote,
        onDeleteNote = viewModel::onDeleteNote,
        onDismissNoteEditor = viewModel::onDismissNoteEditor,
        onToggleMemorized = viewModel::onToggleMemorized,
        onMarkChapterMemorized = viewModel::onMarkChapterMemorized,
        onInstall = viewModel::onInstall,
        onCancelInstall = viewModel::onCancelInstall,
        onRemoveRequested = viewModel::onRemoveRequested,
        onConfirmRemoval = viewModel::onConfirmRemoval,
        onDismissRemoval = viewModel::onDismissRemoval,
        playerBar = playerBar,
    )
}

/**
 * Stateless reading surface. A manuscript **frontispiece** header (cover, title, author, a gold
 * rule, description, totals) is followed by the verse list in matn-global `displayNumber` order
 * (FR-006), keyed by stable verse id (FR-011/SC-003). Each verse opens with the signature gold
 * **rosette** carrying its number (echoing the آية rosette of Arabic manuscripts); the text is
 * set in Amiri, sized from the persisted preference (FR-016), aligned start (RTL via [MatnTheme]),
 * wrapping fully — diacritics intact and never clipped (FR-008/SC-002). For a STRUCTURED matn the
 * table of contents (US3) is the first list item; selecting a chapter scrolls to its first verse
 * (FR-014/SC-004). No network (FR-018/SC-006).
 */
@Composable
fun MatnDetailsContent(
    state: MatnDetailsUiState,
    onFontSizeChanged: (ReadingFontSize) -> Unit = {},
    onVersePlayClicked: (String) -> Unit = {},
    onGlobalPlayClicked: () -> Unit = {},
    onSetLoopStart: (String) -> Unit = {},
    onSetLoopEnd: (String) -> Unit = {},
    onClearLoop: () -> Unit = {},
    onToggleBookmark: (String) -> Unit = {},
    onOpenNoteEditor: (String) -> Unit = {},
    onSaveNote: (String) -> Unit = {},
    onDeleteNote: () -> Unit = {},
    onDismissNoteEditor: () -> Unit = {},
    onToggleMemorized: (String) -> Unit = {},
    onMarkChapterMemorized: (String, Boolean) -> Unit = { _, _ -> },
    onInstall: () -> Unit = {},
    onCancelInstall: () -> Unit = {},
    onRemoveRequested: () -> Unit = {},
    onConfirmRemoval: () -> Unit = {},
    onDismissRemoval: () -> Unit = {},
    playerBar: com.giraffe.matn.presentation.player.PlayerBarViewModel? = null,
) {
    // specs/010-design-system-adoption User Story 2: the repetition-setup sheet's open/closed
    // flag is local UI state (Principle II precedent: GoldScrub's drag state in PlayerBar.kt is
    // the same kind of ephemeral, non-persisted interaction state) — nothing is written to a
    // ViewModel until the sheet's own "start" action fires.
    // T090 (US4, FR-030): rememberSaveable so rotation doesn't silently close an open sheet.
    var repetitionSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Phase 8 (FR-011, SC-007): a play tap against a not-installed matn opens the install prompt
    // instead of silently failing — the per-verse play button is the one play affordance this
    // screen renders that PlaybackController.startSession's gate (T041) cannot pre-empt visibly,
    // since nothing currently surfaces PlaybackState.notice to the user.
    var installPromptOpen by rememberSaveable { mutableStateOf(false) }
    // FR-021: playback is gated on the content actually being present. Phase 13 removed the
    // `isStarter ||` disjunct — no matn is permanently playable any more (FR-040).
    val isPlayable = state.availability is ContentAvailability.Downloaded
    val guardedVersePlayClicked: (String) -> Unit = { verseId ->
        if (isPlayable) onVersePlayClicked(verseId) else installPromptOpen = true
    }
    val guardedGlobalPlayClicked: () -> Unit = {
        if (isPlayable) onGlobalPlayClicked() else installPromptOpen = true
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )

            state.error != null -> Text(
                text = errorMessage(state.error),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(MatnSpacing.gutter),
                textAlign = TextAlign.Center,
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                // specs/010-design-system-adoption User Story 1: while a verse is actively being
                // read/listened to, the focused 3-verse carousel replaces the scrollable browse
                // list. With no active verse (session not started / stopped) the browse list —
                // header, table of contents, full verse list — is unchanged (User Story 4).
                // Phase 6 (research.md D5): a route-supplied focusVerseId centers the carousel on
                // that verse when no playback session is active yet — real playback
                // (activeVerseId non-null) always takes precedence.
                val carouselActiveVerseId = state.activeVerseId ?: state.focusVerseId
                // remember: windowVersesForCarousel does an indexOfFirst scan over `verses` — this
                // screen's single bundled UiState recomposes on unrelated changes (a bookmark
                // toggle, an install-progress tick), so without this it re-scans on every one of
                // those instead of only when the three actual inputs change (perf review).
                val carouselState = remember(state.verses, carouselActiveVerseId) {
                    com.giraffe.matn.presentation.player.windowVersesForCarousel(
                        verses = state.verses,
                        activeVerseId = carouselActiveVerseId,
                    )
                }
                if (carouselState != null) {
                    com.giraffe.matn.presentation.player.ReadingCarousel(
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
                    VerseList(
                        state = state,
                        onFontSizeChanged = onFontSizeChanged,
                        onVersePlayClicked = guardedVersePlayClicked,
                        onGlobalPlayClicked = guardedGlobalPlayClicked,
                        onMarkChapterMemorized = onMarkChapterMemorized,
                        onInstall = onInstall,
                        onCancelInstall = onCancelInstall,
                        onRemoveRequested = onRemoveRequested,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (playerBar != null) {
                    com.giraffe.matn.presentation.player.PlayerBar(
                        viewModel = playerBar,
                        onRepeatSettingsClicked = { repetitionSheetOpen = true },
                    )
                }
            }
        }
        if (playerBar != null) {
            com.giraffe.matn.presentation.player.RepetitionSetupHost(
                visible = repetitionSheetOpen,
                onDismiss = { repetitionSheetOpen = false },
                verses = state.verses,
                playerBar = playerBar,
                onSetLoopStart = onSetLoopStart,
                onSetLoopEnd = onSetLoopEnd,
                onClearLoop = onClearLoop,
                onStartPlayback = onGlobalPlayClicked,
            )
        }
        if (state.pendingRemovalConfirmation && state.header != null) {
            val occupiedBytes = (state.availability as? ContentAvailability.Downloaded)?.occupiedBytes
                ?: state.declaredSizeBytes
            com.giraffe.matn.presentation.common.ConfirmRemovalDialog(
                matnTitle = state.header.title,
                bytes = occupiedBytes,
                // Phase 8 simplification (documented in the PR description per T079): the actual
                // RemovalOutcome variant is only known after removal completes and this contract
                // exposes no platform-capability signal ahead of time, so the confirmation copy
                // uses the immediate-reclaim wording; the honest platform-specific outcome is
                // rendered from state.lastRemovalOutcome after removal (Settings, T066/T067).
                isReleasedPendingSystemReclaim = false,
                onConfirm = onConfirmRemoval,
                onDismiss = onDismissRemoval,
            )
        }
        if (installPromptOpen && state.header != null) {
            com.giraffe.matn.presentation.common.InstallPromptSheet(
                matnTitle = state.header.title,
                declaredSizeBytes = state.declaredSizeBytes,
                onInstall = {
                    installPromptOpen = false
                    onInstall()
                },
                onDismiss = { installPromptOpen = false },
            )
        }
        val noteEditor = state.noteEditor
        if (noteEditor != null) {
            // Local draft state, seeded from the prefill once GetNoteUseCase resolves — same
            // idiom as RepetitionSetupHost's draft (Principle II: nothing here is a ViewModel
            // call until the user explicitly saves/deletes). T091 (US4, FR-030): rememberSaveable
            // so typed-but-unsaved note text survives a rotation instead of vanishing.
            var draft by rememberSaveable(noteEditor.verseId, noteEditor.initialText) {
                mutableStateOf(noteEditor.initialText.orEmpty())
            }
            com.giraffe.matn.presentation.notes.NoteEditorSheet(
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

@Composable
private fun VerseList(
    state: MatnDetailsUiState,
    onFontSizeChanged: (ReadingFontSize) -> Unit = {},
    onVersePlayClicked: (String) -> Unit = {},
    onGlobalPlayClicked: () -> Unit = {},
    onMarkChapterMemorized: (String, Boolean) -> Unit = { _, _ -> },
    onInstall: () -> Unit = {},
    onCancelInstall: () -> Unit = {},
    onRemoveRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val header = state.header
    val verses = state.verses
    val fontSize = state.fontSize.toSp()
    val verseFont = verseFontFamily()
    // T089 (US4, FR-026): header + verse-list horizontal padding follows available width.
    val horizontalMargin = MatnSpacing.horizontalMargin(LocalWindowWidthClass.current)

    // Header occupies item index 0; the TOC panel (when shown) occupies index 1; verses start
    // after that. SC-004 scroll target uses these offsets.
    val tocIndex = if (state.showTableOfContents && state.chapters.isNotEmpty()) 1 else -1
    val firstVerseIndex = if (tocIndex >= 0) tocIndex + 1 else 1

    // US1 FR-003: precomputed once per actual change to chapters/verses/memorized-set, instead of
    // TableOfContents' isChapterMemorized callback re-filtering `verses` per chapter on every
    // recomposition this screen's single bundled UiState triggers (a bookmark toggle, a note edit,
    // an install-progress tick — none of which change memorization) — see android-code-guard
    // performance review. A chapter is "all memorized" only when it has verses AND every one of
    // them is in the memorized set — an empty chapter is never reported as fully memorized.
    val chapterMemorizedById = remember(state.chapters, verses, state.memorizedVerseIds) {
        state.chapters.associate { chapter ->
            val chapterVerseIds = verses.filter { it.chapterId == chapter.id }.map { it.id }
            chapter.id to (chapterVerseIds.isNotEmpty() && chapterVerseIds.all { it in state.memorizedVerseIds })
        }
    }

    // FR-009/SC-003: auto-scroll the active verse into view when it changes.
    LaunchedEffect(state.activeVerseId) {
        val id = state.activeVerseId ?: return@LaunchedEffect
        val verseOffset = verses.indexOfFirst { it.id == id }
        if (verseOffset >= 0) {
            val target = firstVerseIndex + verseOffset
            listState.animateScrollToItem(target)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalMargin,
            end = horizontalMargin,
            top = MatnSpacing.unit,
            bottom = MatnSpacing.gutter + MatnSpacing.unit,
        ),
    ) {
        if (header != null) {
            item(key = "header") {
                Frontispiece(
                    header = header,
                    fontSize = state.fontSize,
                    onFontSizeChanged = onFontSizeChanged,
                    onGlobalPlayClicked = onGlobalPlayClicked,
                    availability = state.availability,
                    declaredSizeBytes = state.declaredSizeBytes,
                    installError = state.installError,
                    onInstall = onInstall,
                    onCancelInstall = onCancelInstall,
                    onRemoveRequested = onRemoveRequested,
                )
            }
        }
        if (tocIndex >= 0) {
            item(key = "toc") {
                TableOfContents(
                    chapters = state.chapters,
                    onChapterSelected = { chapter ->
                        // FR-014/SC-004: scroll the chapter's first verse into view.
                        val verseOffset =
                            verses.indexOfFirst { it.displayNumber == chapter.firstVerseDisplayNumber }
                        if (verseOffset >= 0) {
                            val target = firstVerseIndex + verseOffset
                            coroutineScope.launch { listState.animateScrollToItem(target) }
                        }
                    },
                    isChapterMemorized = { chapter -> chapterMemorizedById[chapter.id] == true },
                    onMarkChapterMemorized = onMarkChapterMemorized,
                )
            }
        }
        items(items = verses, key = { v -> v.id }) { row ->
            VerseRowItem(
                row = row,
                fontSize = fontSize,
                verseFont = verseFont,
                isActive = row.id == state.activeVerseId,
                inLoopRange = row.id in state.loopRangeVerseIds,
                isLoopStart = row.id == state.loopRange?.startVerseId,
                isLoopEnd = row.id == state.loopRange?.endVerseId,
                onPlayClicked = { onVersePlayClicked(row.id) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Manuscript-style title page: cover, title, author, a gold rule, description, and totals. */
@Composable
private fun Frontispiece(
    header: MatnHeader,
    fontSize: ReadingFontSize,
    onFontSizeChanged: (ReadingFontSize) -> Unit,
    onGlobalPlayClicked: () -> Unit = {},
    availability: ContentAvailability? = null,
    declaredSizeBytes: Long = 0L,
    installError: DeliveryError? = null,
    onInstall: () -> Unit = {},
    onCancelInstall: () -> Unit = {},
    onRemoveRequested: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MatnSpacing.unit, bottom = MatnSpacing.gutter - MatnSpacing.unit),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FontSizeChooser(fontSize = fontSize, onFontSizeChanged = onFontSizeChanged)
            Spacer(modifier = Modifier.weight(1f))
            // design-notes.md T049: layers the install/cancel action onto the header's existing
            // Play affordance slot. Installed (or the starter, which is always Installed) keeps
            // the Play button unchanged; otherwise the install/cancel action takes its place —
            // there is nothing to play yet (FR-011).
            if (availability is ContentAvailability.Downloaded) {
                IconButton(onClick = onGlobalPlayClicked) {
                    PlayGlyph(
                        color = MaterialTheme.colorScheme.primary,
                        size = 22.dp,
                        contentDescription = stringResource(Res.string.player_play),
                    )
                }
            } else if (availability != null) {
                ContentActionButton(
                    availability = availability,

                    onInstall = onInstall,
                    onCancel = onCancelInstall,
                    onRemove = onRemoveRequested,
                )
            }
        }
        if (installError is DeliveryError.DeliveryFailed) {
            Text(
                text = installErrorMessage(installError.failure),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.unit / 2),
            )
        }
        if (availability is ContentAvailability.NotDownloaded && declaredSizeBytes > 0L) {
            Text(
                text = formatBytes(declaredSizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MatnSpacing.unit / 2),
            )
        }
        CoverImage(
            coverImageRef = header.coverImageRef,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(0.75f),
        )
        Text(
            text = header.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MatnSpacing.unit * 2),
        )
        Text(
            text = header.author,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MatnSpacing.unit / 2),
        )
        GoldRule(modifier = Modifier.padding(vertical = MatnSpacing.unit * 2))
        if (header.description.isNotBlank()) {
            Text(
                text = header.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        val totals = pluralStringResource(Res.plurals.verses_count, header.verseCount, header.verseCount) +
                "  ·  " + formatDuration(header.totalDurationMs)
        Text(
            text = totals,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MatnSpacing.unit + 4.dp),
        )
        MatnProgressBar(
            fraction = header.progressFraction,
            label = stringResource(Res.string.matn_progress_label),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MatnSpacing.gutter, vertical = MatnSpacing.unit),
        )
    }
}

/** A hairline rule broken by a small central gold rosette — a quiet manuscript ornament. */
@Composable
private fun GoldRule(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.unit * 4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Box(
            modifier = Modifier
                .padding(horizontal = MatnSpacing.unit + 2.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
        )
        Box(
            modifier = Modifier.weight(1f).height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun FontSizeChooser(
    fontSize: ReadingFontSize,
    onFontSizeChanged: (ReadingFontSize) -> Unit,
) {
    // T090 (US4, FR-030): rememberSaveable so rotation doesn't silently close the menu.
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Text(
                text = "أ",
                fontFamily = verseFontFamily(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FontSizeMenuItem(
                label = stringResource(Res.string.font_small),
                isSelected = fontSize == ReadingFontSize.SMALL
            ) {
                onFontSizeChanged(ReadingFontSize.SMALL); expanded = false
            }
            FontSizeMenuItem(
                label = stringResource(Res.string.font_medium),
                isSelected = fontSize == ReadingFontSize.MEDIUM
            ) {
                onFontSizeChanged(ReadingFontSize.MEDIUM); expanded = false
            }
            FontSizeMenuItem(
                label = stringResource(Res.string.font_large),
                isSelected = fontSize == ReadingFontSize.LARGE
            ) {
                onFontSizeChanged(ReadingFontSize.LARGE); expanded = false
            }
            FontSizeMenuItem(
                label = stringResource(Res.string.font_xlarge),
                isSelected = fontSize == ReadingFontSize.XLARGE
            ) {
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

/**
 * Phase 13 (FR-043): delegates to the single exhaustive mapping in
 * [com.giraffe.matn.presentation.common.deliveryFailureCopy], joining the cause and its next action
 * into one line. Kept as a thin wrapper so this screen's call sites are unchanged.
 */
@Composable
private fun installErrorMessage(failure: DeliveryFailure): String {
    val figures = (failure as? DeliveryFailure.InsufficientStorage)?.let {
        formatBytes(it.requiredBytes) + " / " + formatBytes(it.availableBytes)
    }
    val copy = com.giraffe.matn.presentation.common.deliveryFailureCopy(failure, figures)
    return copy.action?.let { "${copy.message} — $it" } ?: copy.message
}

/**
 * One verse row. A–B loop range selection now lives in the User Story 2
 * [com.giraffe.matn.presentation.player.RepetitionSetupSheet] rather than a per-row long-press
 * menu — this row is display-only: in-range rows carry a tinted background, and the A/B boundary
 * rows show a small "A"/"B" marker on the rosette, both still driven by `state.loopRange`.
 */
@Composable
private fun VerseRowItem(
    row: VerseRow,
    fontSize: TextUnit,
    verseFont: FontFamily,
    isActive: Boolean = false,
    inLoopRange: Boolean = false,
    isLoopStart: Boolean = false,
    isLoopEnd: Boolean = false,
    onPlayClicked: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val rowBackground = when {
        isActive -> scheme.secondaryContainer.copy(alpha = 0.4f)
        inLoopRange -> scheme.secondary.copy(alpha = 0.10f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onPlayClicked)
            .padding(vertical = MatnSpacing.unit * 2),
        verticalAlignment = Alignment.Top,
    ) {
        VerseRosette(
            number = row.displayNumber,
            isActive = isActive,
            boundaryMark = when {
                isLoopStart -> "A"
                isLoopEnd -> "B"
                else -> null
            },
        )
        Spacer(modifier = Modifier.width(MatnSpacing.unit + 6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.arabicText,
                fontFamily = verseFont,
                fontSize = fontSize,
                // A ratio of the (already token-driven) reading font size, not an independent
                // magic literal — the constant here is the line-height *multiplier*, matching how
                // MatnSpacing itself is a scale of named multiples rather than one-off values.
                lineHeight = (fontSize.value * 1.7f).sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = formatDuration(row.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MatnSpacing.unit),
            )
        }
        // T058 (US2): the icon-only play affordance — migrated onto IconActionButton so its
        // accessible name comes from the shared catalogue rather than a one-off string. The row's
        // own `Modifier.clickable` above stays a plain clickable: it already carries an accessible
        // name via Compose's default semantics merging of its Text children (the verse number and
        // Arabic text), so it needs no separate label (contract §3 / accessibility-contract.md).
        IconActionButton(action = A11yAction.PLAY, onClick = onPlayClicked) { color ->
            PlayGlyph(
                color = if (isActive) MaterialTheme.colorScheme.primary else color,
                size = 18.dp,
            )
        }
    }
}

/** The signature: the verse number inside a gold-ringed rosette — the آية marker of the متن. */
@Composable
private fun VerseRosette(number: Int, isActive: Boolean = false, boundaryMark: String? = null) {
    val ring = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Box {
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.5.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (boundaryMark != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = boundaryMark,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — every view/component in this file has one (manuscript light theme, RTL via MatnTheme).
// Verse previews use FontFamily.Default so they render without loading the Amiri resource.
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
private fun MatnDetailsActiveVersePreview() {
    MatnTheme {
        MatnDetailsContent(
            state = MatnDetailsUiState(
                isLoading = false,
                header = previewHeader,
                verses = previewVerses,
                activeVerseId = "v2",
                isPlaying = true,
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
private fun VerseRowItemInLoopRangePreview() {
    MatnTheme {
        VerseRowItem(
            row = previewVerses[1],
            fontSize = 22.sp,
            verseFont = FontFamily.Default,
            inLoopRange = true,
            isLoopStart = true,
        )
    }
}

@Preview
@Composable
private fun VerseRosetteBoundaryMarkPreview() {
    MatnTheme {
        Box(modifier = Modifier.padding(MatnSpacing.gutter)) { VerseRosette(number = 5, boundaryMark = "A") }
    }
}

@Preview
@Composable
private fun MatnDetailsNoLoopRangePreview() {
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

/** T092 (US4): expanded window width — confirms the verse list stays bounded/centred. */
@Preview(widthDp = 900)
@Composable
private fun MatnDetailsWidePreview() {
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

/** T067 (US2, accessibility-contract.md §5/§7): largest reachable font scale, narrowest width. */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun MatnDetailsMaxScalePreview() {
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

/** T052 (US2): dark-theme coverage for the populated details content state. */
@Preview
@Composable
private fun MatnDetailsNoLoopRangeDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
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
private fun MatnDetailsWithLoopRangePreview() {
    MatnTheme {
        MatnDetailsContent(
            state = MatnDetailsUiState(
                isLoading = false,
                header = previewHeader,
                verses = previewVerses,
                loopRangeVerseIds = setOf("v2", "v3"),
                loopRange = com.giraffe.matn.domain.model.LoopRange("v2", "v3"),
            ),
        )
    }
}

@Preview
@Composable
private fun VerseRosettePreview() {
    MatnTheme {
        Box(modifier = Modifier.padding(MatnSpacing.gutter)) { VerseRosette(number = 7) }
    }
}

@Preview
@Composable
private fun FrontispiecePreview() {
    MatnTheme {
        Frontispiece(
            header = previewHeader,
            fontSize = ReadingFontSize.MEDIUM,
            onFontSizeChanged = {})
    }
}
