package com.giraffe.matn.presentation.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.common.ConfirmRemovalDialog
import com.giraffe.matn.presentation.common.MatnIcons
import com.giraffe.matn.presentation.common.formatBytes
import com.giraffe.matn.presentation.common.ltrIsolated
import com.giraffe.matn.presentation.theme.MatnMotion
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.giraffe.matn.presentation.common.autoIsolated
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.lineHeightSp
import com.giraffe.matn.presentation.theme.toSp
import com.giraffe.matn.presentation.theme.verseFontFamily
import matn.shared.generated.resources.font_accessible
import matn.shared.generated.resources.settings_row_collapsed
import matn.shared.generated.resources.settings_row_expanded
import org.jetbrains.compose.resources.pluralStringResource
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.downloaded_count
import matn.shared.generated.resources.a11y_retry
import matn.shared.generated.resources.content_action_remove
import matn.shared.generated.resources.font_large
import matn.shared.generated.resources.font_medium
import matn.shared.generated.resources.font_small
import matn.shared.generated.resources.font_xlarge
import matn.shared.generated.resources.onboarding_reopen
import matn.shared.generated.resources.permission_denied
import matn.shared.generated.resources.permission_granted
import matn.shared.generated.resources.permission_not_requested
import matn.shared.generated.resources.permission_notifications
import matn.shared.generated.resources.permission_open_settings
import matn.shared.generated.resources.settings_appearance
import matn.shared.generated.resources.settings_downloaded_count
import matn.shared.generated.resources.settings_font_size_label
import matn.shared.generated.resources.settings_group_about
import matn.shared.generated.resources.settings_group_notifications
import matn.shared.generated.resources.settings_group_storage
import matn.shared.generated.resources.settings_notifications_summary
import matn.shared.generated.resources.settings_remove_all
import matn.shared.generated.resources.settings_removal_reclaimed
import matn.shared.generated.resources.settings_storage_free_label
import matn.shared.generated.resources.settings_storage_of_device
import matn.shared.generated.resources.settings_storage_used_label
import matn.shared.generated.resources.settings_storage_zero_message
import matn.shared.generated.resources.settings_storage_zero_title
import matn.shared.generated.resources.settings_theme_label
import matn.shared.generated.resources.settings_title
import matn.shared.generated.resources.theme_dark
import matn.shared.generated.resources.theme_light
import matn.shared.generated.resources.theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful holder (Principle II) for the Settings tab — hoists [SettingsViewModel]'s state and
 * forwards intents to the stateless [SettingsContent].
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onReopenOnboarding: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // T077 (US3): re-read the OS-level permission status on every resume, so a change made
    // outside the app (revoked in device settings) is reflected without restarting the screen.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissionStatus()
        onPauseOrDispose { }
    }
    SettingsContent(
        state = state,
        onRemoveMatn = viewModel::onRemoveMatn,
        onRemoveAll = viewModel::onRemoveAll,
        onConfirmRemoval = viewModel::onConfirmRemoval,
        onDismissRemoval = viewModel::onDismissRemoval,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onFontSizeSelected = viewModel::onFontSizeSelected,
        onOpenNotificationSettings = viewModel::onOpenNotificationSettings,
        onRetryNotificationPermission = viewModel::onRetryNotificationPermission,
        onReopenOnboarding = onReopenOnboarding,
    )
}

/**
 * Stateless Settings screen.
 *
 * ## What changed and why
 *
 * The previous version was a flat [LazyColumn] of bare `Text` headers and uncontained rows, with no
 * icons, no dividers, and no app bar — the storage figures, a maintenance concern, occupied the top
 * of the screen while the preferences a student actually adjusts sat below them. It was also the
 * only screen in the app where the most visually prominent control was the most destructive one.
 *
 * This version groups the same state into four M3 [Card]s under a [CenterAlignedTopAppBar], ordered
 * by how often a student touches them rather than by the order the requirements were written:
 *
 *  1. **Appearance** — theme as a single [SingleChoiceSegmentedButtonRow] instead of three radio
 *     rows, plus reading font size (previously reachable only from the details screen's unlabelled
 *     "أ" affordance).
 *  2. **Notifications** — one row with a real action, not a status line with nothing to press.
 *  3. **Storage** — a proportional used/free bar rather than two naked figures, the per-matn
 *     breakdown, and "remove all" demoted from a full-width filled button to a text button at the
 *     end of the section.
 *  4. **About** — replay the introduction.
 *
 * Both removal paths still confirm through [ConfirmRemovalDialog] — FR-018 requires it, and
 * deletion is immediate with nothing to restore. What changed is the *result*: the reclaimed
 * figure is announced once through a [SnackbarHost] and leaves, instead of sitting in the layout
 * as a permanent "last outcome" line.
 *
 * Storage still satisfies SC-008 — the total is one glance from the top of the section and reachable
 * without scrolling past anything on a phone — but no longer outranks every preference to get there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onRemoveMatn: (String) -> Unit = {},
    onRemoveAll: () -> Unit = {},
    onConfirmRemoval: () -> Unit = {},
    onDismissRemoval: () -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onFontSizeSelected: (ReadingFontSize) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onRetryNotificationPermission: () -> Unit = {},
    onReopenOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Pinned rather than collapsing: the bar stays put and only its container colour changes as
    // content passes beneath it, so the title never moves while a student is reaching for a control.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackbarHostState = remember { SnackbarHostState() }

    // A completed removal announces itself once and leaves, rather than leaving a permanent
    // "last outcome" line wedged into the layout as the previous version did. There is no Undo:
    // removal deletes the files immediately (see RemovalOutcome), so there is nothing to restore.
    //
    // The figure goes through the resource placeholder rather than being appended to the string.
    // Both translations put it in a different place — "%1$s freed." against "تم تحرير %1$s." — so
    // concatenating leaves the sentence in the wrong order in English and orphans the full stop
    // mid-line in Arabic, which is the exact class of bug ltrIsolated exists to prevent.
    val reclaimed = state.lastOutcome as? RemovalOutcome.Reclaimed
    val reclaimedMessage = reclaimed?.let {
        stringResource(Res.string.settings_removal_reclaimed, formatBytes(it.bytes))
    }
    LaunchedEffect(reclaimed) {
        if (reclaimedMessage != null) snackbarHostState.showSnackbar(message = reclaimedMessage)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            // A centred single-line bar, matching Home's centred wordmark. The large/collapsing
            // variant was wrong here twice over: it reserved ~150dp of empty space above a screen
            // that is a list of short rows, and it renders the title at display size in Amiri,
            // which is a text face for Arabic verse — at that scale it overpowered every control
            // beneath it.
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }
        // T086 (US4, FR-028): bounded to the reading measure and centred on wide windows.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MatnSpacing.readingMaxWidth),
            contentPadding = PaddingValues(
                start = MatnSpacing.marginMobile,
                end = MatnSpacing.marginMobile,
                top = innerPadding.calculateTopPadding() + MatnSpacing.unit,
                bottom = innerPadding.calculateBottomPadding() + MatnSpacing.gutter,
            ),
            verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit + 4.dp),
        ) {
            // Matn Design System §05 (*Settings · expandable rows, no sub-routes*): one list, each
            // row stating its current value and expanding its control in place. The group headers
            // went with the groups — across five rows they cost more vertical space than they saved
            // in scanning, and the value on each row already says what that row controls.
            item(key = "settings-rows") {
                SettingsGroup(title = null) {
                    ExpandableSettingRow(
                        icon = MatnIcons.Palette,
                        title = stringResource(Res.string.settings_theme_label),
                        value = stringResource(themeModeLabel(state.themeMode)),
                    ) {
                        ThemeSegmentedControl(selected = state.themeMode, onSelected = onThemeModeSelected)
                    }
                    GroupDivider()
                    ExpandableSettingRow(
                        icon = MatnIcons.FormatSize,
                        title = stringResource(Res.string.settings_font_size_label),
                        value = stringResource(fontSizeLabel(state.fontSize)),
                    ) {
                        FontSizeSegmentedControl(selected = state.fontSize, onSelected = onFontSizeSelected)
                        VersePreview(fontSize = state.fontSize)
                    }
                    GroupDivider()
                    NotificationRow(
                        status = state.notificationStatus,
                        onOpenSettings = onOpenNotificationSettings,
                        onRetry = onRetryNotificationPermission,
                    )
                    GroupDivider()
                    ExpandableSettingRow(
                        icon = MatnIcons.Storage,
                        title = stringResource(Res.string.settings_group_storage),
                        // Isolated as a whole: a count and a size either side of a neutral "·".
                        value = autoIsolated(
                            pluralStringResource(
                                Res.plurals.downloaded_count,
                                state.entries.size,
                                state.entries.size,
                            ) + " · " + formatBytes(state.totalUsedBytes),
                        ),
                    ) {
                        StorageMeter(
                            usedBytes = state.totalUsedBytes,
                            freeBytes = state.freeSpaceBytes,
                        )
                        if (state.isOnDemandEmpty) {
                            StorageZeroState()
                        } else {
                            state.entries.forEach { entry ->
                                StorageEntryRow(entry = entry, onRemove = { onRemoveMatn(entry.matnId) })
                            }
                            // Demoted from a full-width filled button: still reachable, no longer
                            // the loudest thing on the screen.
                            TextButton(onClick = onRemoveAll) {
                                Text(
                                    text = stringResource(Res.string.settings_remove_all),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    GroupDivider()
                    SettingRow(
                        icon = MatnIcons.Replay,
                        title = stringResource(Res.string.onboarding_reopen),
                        onClick = onReopenOnboarding,
                    ) {
                        Icon(
                            imageVector = MatnIcons.ChevronForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    // Every removal confirms first (FR-018), single or bulk — deletion is immediate and there is
    // no undo. The dialog renders whichever target the view model put in `pendingRemoval`.
    val pending = state.pendingRemoval
    if (pending != null) {
        val (title, bytes) = when (pending) {
            is RemovalTarget.SingleMatn -> pending.title to pending.bytes
            is RemovalTarget.AllContent -> stringResource(Res.string.settings_remove_all) to pending.totalBytes
        }
        ConfirmRemovalDialog(
            matnTitle = title,
            bytes = bytes,
            isReleasedPendingSystemReclaim = false,
            onConfirm = onConfirmRemoval,
            onDismiss = onDismissRemoval,
        )
    }
}

// --------------------------------------------------------------------------- Building blocks

/**
 * A settings row that states its current value and opens its own control underneath (Matn Design
 * System §05 — "Sub-pages are expandable rows, not pushed screens").
 *
 * The value on the collapsed row is the point of the pattern: it means the common case — checking
 * what a setting is currently on — costs a glance instead of a navigation, and the control only
 * appears for the rarer case of changing it. A pushed sub-screen inverts that cost.
 *
 * Expansion is local, unsaved state. It describes where the student is looking right now, not
 * anything about them, so it should not survive leaving the screen.
 */
@Composable
private fun ExpandableSettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val stateLabel = stringResource(
        if (expanded) Res.string.settings_row_expanded else Res.string.settings_row_collapsed,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        SettingRow(
            icon = icon,
            title = title,
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics { stateDescription = stateLabel },
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Reduced motion drops the expand/collapse animation but never the disclosure itself —
        // the control still appears, it just does not travel (adaptive-motion-contract.md §B2.1).
        AnimatedVisibility(
            visible = expanded,
            enter = if (LocalReduceMotion.current) fadeIn(tween(0)) else expandVertically() + fadeIn(),
            exit = if (LocalReduceMotion.current) fadeOut(tween(0)) else shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MatnSpacing.unit * 2,
                        end = MatnSpacing.unit * 2,
                        bottom = MatnSpacing.snug,
                    ),
            ) {
                content()
            }
        }
    }
}

/**
 * A real verse, fully diacriticized, at the size currently selected — the design's "live preview".
 *
 * It is not decoration. Fully-vocalised Arabic is the hardest thing this app renders: تَشْكِيل
 * stacks above and below the baseline, and whether a size is comfortable depends entirely on
 * whether that stack stays legible. A lorem-ipsum sample or a bare أ would let a student pick a
 * size that fails on the only text they will actually read.
 *
 * The sample is a literal rather than a string resource: it is Arabic *content*, identical in both
 * locales, so translating it is meaningless and duplicating it into `values-en/` would invite
 * someone to try.
 */
@Composable
private fun VersePreview(fontSize: ReadingFontSize, modifier: Modifier = Modifier) {
    Surface(
        shape = MatnShapes.lg,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MatnSpacing.snug),
    ) {
        Text(
            text = VersePreviewSample,
            fontFamily = verseFontFamily(),
            fontSize = fontSize.toSp(),
            lineHeight = fontSize.lineHeightSp(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(MatnSpacing.cozy),
        )
    }
}

/** The opening line of al-Ājurrūmiyya — short, and carries every diacritic class that matters. */
private const val VersePreviewSample = "الكَلامُ هُوَ اللَّفْظُ المُرَكَّبُ المُفِيدُ بِالوَضْعِ"

/**
 * One titled group of settings, rendered as an M3 card. The title sits *outside* the card, in the
 * label style — the M3 settings idiom — so the card holds only interactive rows.
 */
@Composable
private fun SettingsGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = MatnSpacing.unit + 4.dp,
                    end = MatnSpacing.unit + 4.dp,
                    bottom = MatnSpacing.unit,
                    top = MatnSpacing.unit,
                ),
            )
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

/**
 * Hairline between rows inside a group, inset **equally on both sides** so it reads as centred
 * within the card.
 *
 * It was previously inset only on the start side — the icon-aligned list idiom — which in an RTL
 * layout put the gap on the right and made the rule look like it had slipped toward one edge. The
 * rows here are not uniformly icon-led (the storage meter and entry rows are not), so there is no
 * single leading edge to align to, and a symmetric inset is both calmer and direction-agnostic.
 */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = MatnSpacing.unit * 2),
    )
}

/**
 * A settings row: leading icon, title (plus optional supporting line), and a trailing [control].
 *
 * Passing [onClick] makes the whole row tappable and gives it a ripple — used for rows that open
 * something. Rows whose trailing control is itself interactive leave it null, so the control keeps
 * its own hit target rather than competing with the row's.
 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // 48dp minimum touch target, per the accessibility contract.
            .padding(horizontal = MatnSpacing.unit * 2, vertical = MatnSpacing.unit + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // the adjacent title is the label
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MatnSpacing.unit * 2),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        control()
    }
}

/**
 * A setting whose control is a full-width choice group (segmented buttons).
 *
 * The control gets its own line beneath the label rather than sharing one with it. A
 * [SingleChoiceSegmentedButtonRow] expands to whatever width it is offered, so placing it beside a
 * weighted title column starves the title — in Arabic that collapsed "حجم خط القراءة" into a column
 * one character wide. Stacking is also the M3 settings idiom for multi-option controls, and it lets
 * every segment show its full label instead of truncating.
 */
@Composable
private fun SettingChoiceRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.unit * 2, vertical = MatnSpacing.unit + 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null, // the adjacent title is the label
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = MatnSpacing.unit * 2),
            )
        }
        Spacer(modifier = Modifier.height(MatnSpacing.unit + 2.dp))
        control()
    }
}

/** Theme as one row of segmented buttons, replacing three full-width radio rows. */
@Composable
private fun ThemeSegmentedControl(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    SingleChoiceSegmentedButtonRow {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                icon = {},
                label = {
                    Text(
                        text = stringResource(themeModeLabel(mode)),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/**
 * Font size as segmented buttons. The labels are set at their own step size, so the control
 * previews the effect rather than only naming it.
 */
@Composable
private fun FontSizeSegmentedControl(selected: ReadingFontSize, onSelected: (ReadingFontSize) -> Unit) {
    val sizes = ReadingFontSize.entries
    SingleChoiceSegmentedButtonRow {
        sizes.forEachIndexed { index, size ->
            SegmentedButton(
                selected = selected == size,
                onClick = { onSelected(size) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = sizes.size),
                icon = {},
                label = {
                    Text(
                        text = stringResource(fontSizeLabel(size)),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/**
 * Used vs free as a proportional bar plus one figure, rather than two equally-weighted numbers with
 * no relationship shown between them. The fill animates from zero on entry so the proportion reads
 * as a quantity rather than appearing pre-drawn.
 */
@Composable
private fun StorageMeter(usedBytes: Long, freeBytes: Long, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val total = (usedBytes + freeBytes).coerceAtLeast(1L)
    val fraction = (usedBytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = androidx.compose.animation.core.tween(MatnMotion.durationMedium),
        label = "storageFill",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.unit * 2, vertical = MatnSpacing.unit + 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MatnIcons.Storage,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = MatnSpacing.unit * 2)) {
                Text(
                    text = stringResource(Res.string.settings_storage_used_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = formatBytes(usedBytes),
                    style = MaterialTheme.typography.titleLarge,
                    color = scheme.onSurface,
                )
            }
            Text(
                text = stringResource(Res.string.settings_storage_of_device),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(MatnSpacing.unit + 2.dp))
        LinearProgressIndicator(
            progress = { animated },
            color = scheme.primary,
            trackColor = scheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Spacer(modifier = Modifier.height(MatnSpacing.unit / 2))
        Text(
            text = stringResource(Res.string.settings_storage_free_label) + " " + formatBytes(freeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** One downloaded matn: title, size, and a remove action. */
@Composable
private fun StorageEntryRow(entry: MatnStorageEntry, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = MatnSpacing.unit * 2,
                end = MatnSpacing.unit,
                top = MatnSpacing.unit / 2,
                bottom = MatnSpacing.unit / 2,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = MatnSpacing.unit)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatBytes(entry.bytes),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.IconButton(onClick = onRemove) {
            Icon(
                imageVector = MatnIcons.Delete,
                contentDescription = stringResource(Res.string.content_action_remove),
                tint = scheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** FR-028 zero state: nothing downloaded yet. */
@Composable
private fun StorageZeroState(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.unit * 2, vertical = MatnSpacing.unit + 4.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings_storage_zero_title),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.settings_storage_zero_message),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MatnSpacing.unit / 2),
        )
    }
}

/**
 * The notification permission as one actionable row.
 *
 * `NOT_DETERMINED` still shows no action — it is asked in context, at first playback, never from
 * here (rule 5) — but it now reads as a labelled setting with a status rather than a dead line of
 * text under a section header of its own.
 */
@Composable
private fun NotificationRow(
    status: PermissionStatus,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingRow(
        icon = MatnIcons.Notifications,
        title = stringResource(Res.string.permission_notifications),
        supporting = when (status) {
            PermissionStatus.NOT_DETERMINED -> stringResource(Res.string.settings_notifications_summary)
            PermissionStatus.GRANTED -> stringResource(Res.string.permission_granted)
            PermissionStatus.DENIED, PermissionStatus.PERMANENTLY_DENIED ->
                stringResource(Res.string.permission_denied)
        },
        modifier = modifier,
    ) {
        when (status) {
            PermissionStatus.GRANTED, PermissionStatus.PERMANENTLY_DENIED ->
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(Res.string.permission_open_settings))
                }
            PermissionStatus.DENIED ->
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.a11y_retry)) }
            PermissionStatus.NOT_DETERMINED -> Unit
        }
    }
}

private fun themeModeLabel(mode: ThemeMode): StringResource = when (mode) {
    ThemeMode.SYSTEM -> Res.string.theme_system
    ThemeMode.LIGHT -> Res.string.theme_light
    ThemeMode.DARK -> Res.string.theme_dark
}

private fun fontSizeLabel(size: ReadingFontSize): StringResource = when (size) {
    ReadingFontSize.SMALL -> Res.string.font_small
    ReadingFontSize.MEDIUM -> Res.string.font_medium
    ReadingFontSize.LARGE -> Res.string.font_large
    ReadingFontSize.XLARGE -> Res.string.font_xlarge
    ReadingFontSize.ACCESSIBLE -> Res.string.font_accessible
}

// --------------------------------------------------------------------------- Previews

private val previewEntries = listOf(
    MatnStorageEntry(matnId = "m1", title = "متن الآجرومية مبوب", bytes = 5_000_000),
    MatnStorageEntry(matnId = "m2", title = "الأجرومية المهذبة", bytes = 1_200_000),
    MatnStorageEntry(matnId = "m3", title = "الأجرومية", bytes = 296_000),
)

private fun previewState(
    entries: List<MatnStorageEntry> = previewEntries,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
) = SettingsUiState(
    isLoading = false,
    totalUsedBytes = entries.sumOf { it.bytes },
    freeSpaceBytes = 12_000_000_000L,
    entries = entries,
    themeMode = themeMode,
)

@Preview
@Composable
private fun SettingsContentLoadingPreview() {
    MatnTheme { SettingsContent(state = SettingsUiState(isLoading = true)) }
}

@Preview
@Composable
private fun SettingsContentPopulatedPreview() {
    MatnTheme { SettingsContent(state = previewState()) }
}

/** Nothing downloaded — the storage card collapses to its zero state, no "remove all". */
@Preview
@Composable
private fun SettingsContentZeroPreview() {
    MatnTheme {
        SettingsContent(
            state = SettingsUiState(isLoading = false, freeSpaceBytes = 12_000_000_000L),
        )
    }
}

/** RTL — the Arabic interface language, which is what most students will see. */
@Preview
@Composable
private fun SettingsContentRtlPreview() {
    MatnTheme(layoutDirection = androidx.compose.ui.unit.LayoutDirection.Rtl) {
        SettingsContent(state = previewState())
    }
}

/** T092 (US4): expanded window width — the cards stay bounded and centred, not stretched. */
@Preview(widthDp = 900)
@Composable
private fun SettingsContentWidePreview() {
    MatnTheme { SettingsContent(state = previewState()) }
}

/** T067 (US2, accessibility-contract.md §5/§7): largest reachable font scale, narrowest width. */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun SettingsContentMaxScalePreview() {
    MatnTheme { SettingsContent(state = previewState()) }
}

/** T052 (US2): dark-theme coverage for the populated content state. */
@Preview
@Composable
private fun SettingsContentPopulatedDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
        SettingsContent(state = previewState(themeMode = ThemeMode.DARK))
    }
}

@Preview
@Composable
private fun SettingsContentConfirmationOpenPreview() {
    MatnTheme {
        SettingsContent(
            state = previewState().copy(
                pendingRemoval = RemovalTarget.AllContent(previewEntries.sumOf { it.bytes }),
            ),
        )
    }
}

/** Each permission state, since each renders a different trailing action. */
@Preview
@Composable
private fun SettingsContentPermissionStatesPreview() {
    MatnTheme {
        Column(modifier = Modifier.clearAndSetSemantics { }) {
            PermissionStatus.entries.forEach { status ->
                NotificationRow(status = status, onOpenSettings = {}, onRetry = {})
            }
        }
    }
}
