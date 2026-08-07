package com.giraffe.matn.presentation.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily

/**
 * The verse-context block: matn title (secondary) + verse number + verse excerpt (primary, Amiri).
 *
 * Used by [NoteEditorSheet] to say which verse is being annotated. The list rows that used to share
 * it moved to [com.giraffe.matn.presentation.saved.SavedScreen], which carries a kind tag and a
 * per-kind footer and so composes its own row rather than reusing this block.
 */
@Composable
fun VerseContextBlock(ref: AnnotatedVerseRef, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "${ref.matnTitle} · ${ref.verseNumber}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = ref.verseText,
            fontFamily = verseFontFamily(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MatnSpacing.unit / 2),
        )
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun VerseContextBlockPreview() {
    MatnTheme {
        VerseContextBlock(
            ref = AnnotatedVerseRef(
                matnId = "m1",
                matnTitle = "الأجرومية",
                verseId = "v1",
                verseNumber = 2,
                verseText = "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ",
            ),
        )
    }
}
