package com.giraffe.matn.presentation.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.Bookmark
import com.giraffe.matn.domain.model.BookmarkEntry
import com.giraffe.matn.domain.model.Note
import com.giraffe.matn.domain.model.NoteEntry
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily

/**
 * The verse-context block shared by [BookmarkRow] and [NoteRow] (Constitution VIII second-use
 * rule — extracted at first use here since the second, [NoteRow], is already planned for T047):
 * matn title (secondary) + verse number + verse excerpt (primary, Amiri).
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

@Composable
fun BookmarkRow(entry: BookmarkEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // T059 (US2): content row — the verse context is already merged from VerseContextBlock's
    // descendant Text; onClickLabel just names the tap's target.
    val label = "${entry.ref.matnTitle} ${entry.ref.verseNumber}"
    VerseContextBlock(
        ref = entry.ref,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = MatnSpacing.marginMobile, vertical = MatnSpacing.unit * 2),
    )
}

/** Reuses [VerseContextBlock] then appends a truncated (~2-line) note preview (T047; full text
 *  lives in [NoteEntry.note] — only the rendering truncates). */
@Composable
fun NoteRow(entry: NoteEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = "${entry.ref.matnTitle} ${entry.ref.verseNumber}"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = MatnSpacing.marginMobile, vertical = MatnSpacing.unit * 2),
    ) {
        VerseContextBlock(ref = entry.ref)
        Text(
            text = entry.note.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = MatnSpacing.unit),
        )
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

@Preview
@Composable
private fun BookmarkRowPreview() {
    MatnTheme {
        BookmarkRow(
            entry = BookmarkEntry(Bookmark("b1", "v1", 1000), previewRef),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun NoteRowPreview() {
    MatnTheme {
        NoteRow(
            entry = NoteEntry(
                Note("n1", "v1", "هذه ملاحظة شخصية طويلة جداً لاختبار الاقتطاع بعد سطرين من النص الظاهر في القائمة", 1000),
                previewRef,
            ),
            onClick = {},
        )
    }
}
