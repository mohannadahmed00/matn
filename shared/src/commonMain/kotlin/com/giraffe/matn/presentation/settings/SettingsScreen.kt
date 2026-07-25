package com.giraffe.matn.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.domain.model.PermissionStatus
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.common.ConfirmRemovalDialog
import com.giraffe.matn.presentation.common.StorageUsageRow
import com.giraffe.matn.presentation.common.formatBytes
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.MatnMotion
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.a11y_retry
import matn.shared.generated.resources.onboarding_reopen
import matn.shared.generated.resources.permission_denied
import matn.shared.generated.resources.permission_granted
import matn.shared.generated.resources.permission_not_requested
import matn.shared.generated.resources.permission_notifications
import matn.shared.generated.resources.permission_open_settings
import matn.shared.generated.resources.settings_appearance
import matn.shared.generated.resources.settings_permissions
import matn.shared.generated.resources.settings_remove_all
import matn.shared.generated.resources.settings_removal_pending
import matn.shared.generated.resources.settings_removal_reclaimed
import matn.shared.generated.resources.settings_storage_breakdown_title
import matn.shared.generated.resources.settings_storage_free_label
import matn.shared.generated.resources.settings_storage_used_label
import matn.shared.generated.resources.settings_storage_zero_message
import matn.shared.generated.resources.settings_storage_zero_title
import matn.shared.generated.resources.settings_title
import matn.shared.generated.resources.theme_dark
import matn.shared.generated.resources.theme_light
import matn.shared.generated.resources.theme_system
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
        onOpenNotificationSettings = viewModel::onOpenNotificationSettings,
        onRetryNotificationPermission = viewModel::onRetryNotificationPermission,
        onReopenOnboarding = onReopenOnboarding,
    )
}

/**
 * Stateless Settings screen (T067, storage-ui-contract.md §2) — replaces the `ComingSoonScreen`
 * stub on the `settings` route (FR-024), the last tab to get a real screen. Storage section sits
 * at the very top (SC-008): total used + free space as a header pair, then the size-ordered
 * breakdown, then "remove all downloaded content".
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onRemoveMatn: (String) -> Unit = {},
    onRemoveAll: () -> Unit = {},
    onConfirmRemoval: () -> Unit = {},
    onDismissRemoval: () -> Unit = {},
    onThemeModeSelected: (ThemeMode) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onRetryNotificationPermission: () -> Unit = {},
    onReopenOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            // T086 (US4, FR-028): bounded to the reading measure and centred on wide windows.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = MatnSpacing.readingMaxWidth),
                contentPadding = PaddingValues(horizontal = MatnSpacing.marginMobile, vertical = MatnSpacing.gutter),
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.primary,
                        modifier = Modifier.padding(bottom = MatnSpacing.gutter),
                    )
                }
                item(key = "storage-header") {
                    StorageHeaderPair(
                        usedBytes = state.totalUsedBytes,
                        freeBytes = state.freeSpaceBytes,
                        modifier = Modifier.padding(bottom = MatnSpacing.unit),
                    )
                }
                state.lastOutcome?.let { outcome ->
                    item(key = "last-outcome") {
                        Text(
                            text = removalOutcomeMessage(outcome),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = MatnSpacing.unit),
                        )
                    }
                }
                item(key = "breakdown-title") {
                    Text(
                        text = stringResource(Res.string.settings_storage_breakdown_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(top = MatnSpacing.gutter, bottom = MatnSpacing.unit),
                    )
                }
                if (state.isOnDemandEmpty) {
                    item(key = "zero-state") { SettingsZeroState() }
                }
                items(items = state.entries, key = { it.matnId }) { entry ->
                    StorageUsageRow(entry = entry, onRemove = { onRemoveMatn(entry.matnId) })
                }
                if (!state.isOnDemandEmpty) {
                    item(key = "remove-all") {
                        Button(
                            onClick = onRemoveAll,
                            shape = MatnShapes.full,
                            colors = ButtonDefaults.buttonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer),
                            modifier = Modifier.fillMaxWidth().padding(top = MatnSpacing.gutter),
                        ) {
                            Text(stringResource(Res.string.settings_remove_all), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                item(key = "appearance-title") {
                    Text(
                        text = stringResource(Res.string.settings_appearance),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(top = MatnSpacing.gutter, bottom = MatnSpacing.unit),
                    )
                }
                items(items = ThemeMode.entries.toList(), key = { "theme-${it.name}" }) { mode ->
                    ThemeModeRow(
                        mode = mode,
                        selected = state.themeMode == mode,
                        onSelected = { onThemeModeSelected(mode) },
                    )
                }
                item(key = "permissions-title") {
                    Text(
                        text = stringResource(Res.string.settings_permissions),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(top = MatnSpacing.gutter, bottom = MatnSpacing.unit),
                    )
                }
                item(key = "permission-notifications") {
                    NotificationPermissionRow(
                        status = state.notificationStatus,
                        onOpenSettings = onOpenNotificationSettings,
                        onRetry = onRetryNotificationPermission,
                    )
                }
                item(key = "reopen-onboarding") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onReopenOnboarding)
                            .padding(vertical = MatnSpacing.unit + 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.onboarding_reopen),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                        )
                    }
                }
            }
        }
        val pending = state.pendingRemoval
        if (pending != null) {
            val (title, bytes, isPending) = when (pending) {
                is RemovalTarget.SingleMatn -> Triple(pending.title, pending.bytes, false)
                is RemovalTarget.AllContent -> Triple(
                    stringResource(Res.string.settings_remove_all),
                    pending.totalBytes,
                    false,
                )
            }
            ConfirmRemovalDialog(
                matnTitle = title,
                bytes = bytes,
                isReleasedPendingSystemReclaim = isPending,
                onConfirm = onConfirmRemoval,
                onDismiss = onDismissRemoval,
            )
        }
    }
}

/** Total used + device free space, side by side (SC-008 — findable within 10 s). */
@Composable
private fun StorageHeaderPair(usedBytes: Long, freeBytes: Long, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_storage_used_label),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = formatBytes(usedBytes),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_storage_free_label),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = formatBytes(freeBytes),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface,
            )
        }
    }
}

/** FR-028 zero state: nothing on-demand installed yet — the starter row alone never triggers it. */
@Composable
private fun SettingsZeroState(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth().padding(vertical = MatnSpacing.unit)) {
        Text(
            text = stringResource(Res.string.settings_storage_zero_title),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            textAlign = TextAlign.Start,
        )
        Text(
            text = stringResource(Res.string.settings_storage_zero_message),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MatnSpacing.unit / 2),
        )
    }
}

/** T045 (US1): one selectable row per [ThemeMode] — "Appearance" section, below storage (rule 4). */
@Composable
private fun ThemeModeRow(mode: ThemeMode, selected: Boolean, onSelected: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelected, role = Role.RadioButton)
            .padding(vertical = MatnSpacing.unit / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = scheme.primary, unselectedColor = scheme.onSurfaceVariant),
        )
        Text(
            text = stringResource(themeModeLabel(mode)),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            modifier = Modifier.padding(start = MatnSpacing.unit),
        )
    }
}

private fun themeModeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> Res.string.theme_system
    ThemeMode.LIGHT -> Res.string.theme_light
    ThemeMode.DARK -> Res.string.theme_dark
}

/**
 * T077 (US3, onboarding-permissions-contract.md §5) — one row per [PermissionStatus] the
 * notification permission can be in. `NOT_DETERMINED` shows no action (it is asked in context,
 * at first playback, never from here — rule 5). `DENIED` offers an explicit, student-initiated
 * retry. `GRANTED`/`PERMANENTLY_DENIED` point at device settings.
 */
@Composable
private fun NotificationPermissionRow(
    status: PermissionStatus,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = MatnSpacing.unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.permission_notifications),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(
                    when (status) {
                        PermissionStatus.NOT_DETERMINED -> Res.string.permission_not_requested
                        PermissionStatus.GRANTED -> Res.string.permission_granted
                        PermissionStatus.DENIED, PermissionStatus.PERMANENTLY_DENIED -> Res.string.permission_denied
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        when (status) {
            PermissionStatus.GRANTED, PermissionStatus.PERMANENTLY_DENIED -> {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(Res.string.permission_open_settings))
                }
            }
            PermissionStatus.DENIED -> {
                TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.a11y_retry))
                }
            }
            PermissionStatus.NOT_DETERMINED -> Unit
        }
    }
}

// T078 (US3, onboarding-permissions-contract.md §6): one preview per PermissionStatus.
@Preview
@Composable
private fun NotificationPermissionRowNotDeterminedPreview() {
    MatnTheme { NotificationPermissionRow(status = PermissionStatus.NOT_DETERMINED, onOpenSettings = {}, onRetry = {}) }
}

@Preview
@Composable
private fun NotificationPermissionRowGrantedPreview() {
    MatnTheme { NotificationPermissionRow(status = PermissionStatus.GRANTED, onOpenSettings = {}, onRetry = {}) }
}

@Preview
@Composable
private fun NotificationPermissionRowDeniedPreview() {
    MatnTheme { NotificationPermissionRow(status = PermissionStatus.DENIED, onOpenSettings = {}, onRetry = {}) }
}

@Preview
@Composable
private fun NotificationPermissionRowPermanentlyDeniedPreview() {
    MatnTheme { NotificationPermissionRow(status = PermissionStatus.PERMANENTLY_DENIED, onOpenSettings = {}, onRetry = {}) }
}

/** Honest post-removal copy, switching on [RemovalOutcome] semantics (research D2). */
@Composable
private fun removalOutcomeMessage(outcome: RemovalOutcome): String = when (outcome) {
    is RemovalOutcome.Reclaimed -> stringResource(Res.string.settings_removal_reclaimed, formatBytes(outcome.bytes))
    is RemovalOutcome.ReleasedPendingSystemReclaim ->
        stringResource(Res.string.settings_removal_pending, formatBytes(outcome.bytes))
}

// --------------------------------------------------------------------------- Previews

private val previewEntries = listOf(
    MatnStorageEntry(matnId = "m1", title = "متن الآجرومية مبوب", bytes = 5_000_000, isStarter = false),
    MatnStorageEntry(matnId = "m2", title = "الأجرومية المهذبة", bytes = 1_200_000, isStarter = false),
    MatnStorageEntry(matnId = "m3", title = "الأجرومية", bytes = 296_000, isStarter = true),
)

@Preview
@Composable
private fun SettingsContentLoadingPreview() {
    MatnTheme { SettingsContent(state = SettingsUiState(isLoading = true)) }
}

@Preview
@Composable
private fun SettingsContentZeroPreview() {
    MatnTheme {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                totalUsedBytes = 296_000,
                onDemandUsedBytes = 0L,
                freeSpaceBytes = 12_000_000_000L,
                entries = listOf(previewEntries.last()),
            ),
        )
    }
}

@Preview
@Composable
private fun SettingsContentPopulatedPreview() {
    MatnTheme {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                totalUsedBytes = previewEntries.sumOf { it.bytes },
                onDemandUsedBytes = previewEntries.filter { !it.isStarter }.sumOf { it.bytes },
                freeSpaceBytes = 12_000_000_000L,
                entries = previewEntries,
            ),
        )
    }
}

/** T092 (US4): expanded window width — confirms the list is bounded/centred, not stretched. */
@Preview(widthDp = 900)
@Composable
private fun SettingsContentWidePreview() {
    MatnTheme {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                totalUsedBytes = previewEntries.sumOf { it.bytes },
                onDemandUsedBytes = previewEntries.filter { !it.isStarter }.sumOf { it.bytes },
                freeSpaceBytes = 12_000_000_000L,
                entries = previewEntries,
            ),
        )
    }
}

/** T067 (US2, accessibility-contract.md §5/§7): largest reachable font scale, narrowest width. */
@Preview(fontScale = 2.0f, widthDp = 320)
@Composable
private fun SettingsContentMaxScalePreview() {
    MatnTheme {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                totalUsedBytes = previewEntries.sumOf { it.bytes },
                onDemandUsedBytes = previewEntries.filter { !it.isStarter }.sumOf { it.bytes },
                freeSpaceBytes = 12_000_000_000L,
                entries = previewEntries,
            ),
        )
    }
}

/** T052 (US2): dark-theme coverage for the populated content state. */
@Preview
@Composable
private fun SettingsContentPopulatedDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                totalUsedBytes = previewEntries.sumOf { it.bytes },
                onDemandUsedBytes = previewEntries.filter { !it.isStarter }.sumOf { it.bytes },
                freeSpaceBytes = 12_000_000_000L,
                entries = previewEntries,
            ),
        )
    }
}

@Preview
@Composable
private fun SettingsContentConfirmationOpenPreview() {
    MatnTheme {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                totalUsedBytes = previewEntries.sumOf { it.bytes },
                onDemandUsedBytes = previewEntries.filter { !it.isStarter }.sumOf { it.bytes },
                freeSpaceBytes = 12_000_000_000L,
                entries = previewEntries,
                pendingRemoval = RemovalTarget.SingleMatn("m1", "متن الآجرومية مبوب", 5_000_000),
            ),
        )
    }
}

@Preview
@Composable
private fun SettingsContentReleasedPendingReclaimPreview() {
    MatnTheme {
        SettingsContent(
            state = SettingsUiState(
                isLoading = false,
                totalUsedBytes = previewEntries.sumOf { it.bytes },
                onDemandUsedBytes = previewEntries.filter { !it.isStarter }.sumOf { it.bytes },
                freeSpaceBytes = 12_000_000_000L,
                entries = previewEntries,
                lastOutcome = RemovalOutcome.ReleasedPendingSystemReclaim(1_200_000),
            ),
        )
    }
}
