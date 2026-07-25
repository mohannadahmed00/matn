package com.giraffe.matn.presentation.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.presentation.common.ConfirmRemovalDialog
import com.giraffe.matn.presentation.common.StorageUsageRow
import com.giraffe.matn.presentation.common.formatBytes
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.settings_remove_all
import matn.shared.generated.resources.settings_removal_pending
import matn.shared.generated.resources.settings_removal_reclaimed
import matn.shared.generated.resources.settings_storage_breakdown_title
import matn.shared.generated.resources.settings_storage_free_label
import matn.shared.generated.resources.settings_storage_used_label
import matn.shared.generated.resources.settings_storage_zero_message
import matn.shared.generated.resources.settings_storage_zero_title
import matn.shared.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful holder (Principle II) for the Settings tab — hoists [SettingsViewModel]'s state and
 * forwards intents to the stateless [SettingsContent].
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onRemoveMatn = viewModel::onRemoveMatn,
        onRemoveAll = viewModel::onRemoveAll,
        onConfirmRemoval = viewModel::onConfirmRemoval,
        onDismissRemoval = viewModel::onDismissRemoval,
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
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
