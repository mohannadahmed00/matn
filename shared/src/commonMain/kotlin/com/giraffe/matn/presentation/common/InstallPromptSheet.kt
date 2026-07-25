package com.giraffe.matn.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.content_action_install
import matn.shared.generated.resources.install_prompt_dismiss
import matn.shared.generated.resources.install_prompt_message
import org.jetbrains.compose.resources.stringResource

/**
 * The actionable install prompt FR-011 requires — opened wherever a play action targets a matn
 * that is not installed ([storage-ui-contract.md] §3/§5: "never a silent failure"). Stateless bottom
 * sheet: matn title, declared size, an install action, and a dismiss — following the
 * [com.giraffe.matn.presentation.notes.NoteEditorSheet] idiom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallPromptSheet(
    matnTitle: String,
    declaredSizeBytes: Long,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = scheme.surface,
        shape = MatnShapes.xl,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MatnSpacing.gutter)
                .padding(bottom = MatnSpacing.gutter),
        ) {
            Text(
                text = matnTitle,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(bottom = MatnSpacing.unit),
            )
            Text(
                text = stringResource(Res.string.install_prompt_message) + " · " + formatBytes(declaredSizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = MatnSpacing.gutter),
            )
            Button(
                onClick = onInstall,
                shape = MatnShapes.full,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.content_action_install), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
                Text(
                    text = stringResource(Res.string.install_prompt_dismiss),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// --------------------------------------------------------------------------- Previews

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun InstallPromptSheetPreview() {
    MatnTheme {
        InstallPromptSheet(
            matnTitle = "الأجرومية المهذبة",
            declaredSizeBytes = 2_400_000,
            onInstall = {},
            onDismiss = {},
        )
    }
}
