package com.giraffe.matn.presentation.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.content_action_remove
import matn.shared.generated.resources.storage_row_part_of_app
import org.jetbrains.compose.resources.stringResource

/**
 * One Settings breakdown row (T064, storage-ui-contract.md §3): title + [formatBytes], and a
 * remove action — except the starter, which renders "part of the app" and **no** remove action
 * (FR-027). Stateless and parameterized (Principle II); shared between the Settings screen and,
 * per the component table, any future second use.
 */
@Composable
fun StorageUsageRow(
    entry: MatnStorageEntry,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = MatnSpacing.unit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatBytes(entry.bytes),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MatnSpacing.unit),
        )
        if (entry.isStarter) {
            Text(
                text = stringResource(Res.string.storage_row_part_of_app),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        } else {
            IconButton(onClick = onRemove) {
                RemoveContentGlyph(color = scheme.error, contentDescription = stringResource(Res.string.content_action_remove))
            }
        }
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun StorageUsageRowRemovablePreview() {
    MatnTheme {
        StorageUsageRow(
            entry = MatnStorageEntry(matnId = "m1", title = "الأجرومية المهذبة", bytes = 2_400_000, isStarter = false),
            onRemove = {},
        )
    }
}

@Preview
@Composable
private fun StorageUsageRowStarterPreview() {
    MatnTheme {
        StorageUsageRow(
            entry = MatnStorageEntry(matnId = "m2", title = "الأجرومية", bytes = 296_000, isStarter = true),
            onRemove = {},
        )
    }
}
