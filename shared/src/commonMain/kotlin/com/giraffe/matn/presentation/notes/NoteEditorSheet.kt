package com.giraffe.matn.presentation.notes

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.note_editor_delete
import matn.shared.generated.resources.note_editor_hint
import matn.shared.generated.resources.note_editor_save
import org.jetbrains.compose.resources.stringResource

/**
 * Create/edit/delete note bottom sheet (US3; ui-contract.md § NoteEditorSheet), following the
 * [com.giraffe.matn.presentation.player.RepetitionSetupSheet] idiom. Pure function of
 * [verseRef]/[initialText]/[draft] (Principle II) — `canSave = draft.isNotBlank()`;
 * `canDelete = initialText != null` (delete is explicit and only offered for an existing note,
 * FR-019 — dismissing without saving discards the draft, it never deletes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorSheet(
    verseRef: AnnotatedVerseRef,
    initialText: String?,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val canSave = draft.isNotBlank()
    val canDelete = initialText != null
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
                .padding(bottom = MatnSpacing.gutter)
                .navigationBarsPadding(),
        ) {
            VerseContextBlock(ref = verseRef, modifier = Modifier.padding(bottom = MatnSpacing.gutter))
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(Res.string.note_editor_hint)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = MatnSpacing.unit * 2),
                minLines = 3,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(Res.string.note_editor_delete), color = scheme.error)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    shape = MatnShapes.full,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                ) {
                    Text(stringResource(Res.string.note_editor_save), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- Previews

private val previewRef = AnnotatedVerseRef(
    matnId = "m1",
    matnTitle = "الأجرومية",
    verseId = "v1",
    verseNumber = 2,
    verseText = "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ",
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun NoteEditorSheetCreateNewPreview() {
    MatnTheme {
        NoteEditorSheet(
            verseRef = previewRef,
            initialText = null,
            draft = "",
            onDraftChange = {},
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun NoteEditorSheetEditExistingPreview() {
    MatnTheme {
        NoteEditorSheet(
            verseRef = previewRef,
            initialText = "ملاحظة محفوظة سابقاً",
            draft = "ملاحظة محفوظة سابقاً",
            onDraftChange = {},
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}
