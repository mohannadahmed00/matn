package com.giraffe.matn.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.giraffe.matn.domain.model.VerseAnnotations
import com.giraffe.matn.presentation.common.BookmarkGlyph
import com.giraffe.matn.presentation.common.MemorizedGlyph
import com.giraffe.matn.presentation.common.NoteGlyph
import com.giraffe.matn.presentation.details.VerseRow
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.arabicLabelSmall
import com.giraffe.matn.presentation.theme.toSp
import com.giraffe.matn.presentation.theme.verseFontFamily
import com.giraffe.matn.domain.model.ReadingFontSize
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.bookmarked_indicator
import matn.shared.generated.resources.has_note_indicator
import matn.shared.generated.resources.memorized_indicator
import matn.shared.generated.resources.player_now_playing
import matn.shared.generated.resources.toggle_bookmark
import matn.shared.generated.resources.toggle_memorized
import matn.shared.generated.resources.toggle_note
import org.jetbrains.compose.resources.stringResource

/**
 * The focused reading carousel (specs/010-design-system-adoption, User Story 1) — replaces the
 * scrollable verse list while a playback session is active: the just-finished verse (muted,
 * above), the active verse (large, highlighted, centered), and the upcoming verse (muted, below).
 * Pure function of [ReadingCarouselUiState] (Principle II) — no ViewModel, no navigation.
 *
 * [previousVerse]/[nextVerse] slots render empty rather than erroring at the matn's first/last
 * verse (spec Edge Cases) — [Box] simply has nothing to lay out for a `null` neighbor.
 */
@Composable
fun ReadingCarousel(
    state: ReadingCarouselUiState,
    fontSize: ReadingFontSize = ReadingFontSize.MEDIUM,
    annotations: Map<String, VerseAnnotations> = emptyMap(),
    memorizedVerseIds: Set<String> = emptySet(),
    onToggleBookmark: (String) -> Unit = {},
    onOpenNoteEditor: (String) -> Unit = {},
    onToggleMemorized: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val verseFont = verseFontFamily()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MatnSpacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeighborVerse(
            verse = state.previousVerse,
            fontSize = fontSize,
            verseFont = verseFont,
            annotations = annotations,
            memorizedVerseIds = memorizedVerseIds,
        )
        Box(modifier = Modifier.height(MatnSpacing.unit * 6))
        ActiveVerseCard(
            verse = state.activeVerse,
            fontSize = fontSize,
            verseFont = verseFont,
            annotations = annotations,
            memorizedVerseIds = memorizedVerseIds,
            onToggleBookmark = onToggleBookmark,
            onOpenNoteEditor = onOpenNoteEditor,
            onToggleMemorized = onToggleMemorized,
        )
        Box(modifier = Modifier.height(MatnSpacing.unit * 6))
        NeighborVerse(
            verse = state.nextVerse,
            fontSize = fontSize,
            verseFont = verseFont,
            annotations = annotations,
            memorizedVerseIds = memorizedVerseIds,
        )
    }
}

/**
 * A muted, slightly scaled-down neighbor verse — or nothing, at a matn boundary. Sized as a
 * fraction of the active verse's [ReadingFontSize] (never a bare literal) so it shrinks/grows
 * alongside the user's font-size preference instead of drifting out of proportion at the extremes.
 */
@Composable
private fun NeighborVerse(
    verse: VerseRow?,
    fontSize: ReadingFontSize,
    verseFont: androidx.compose.ui.text.font.FontFamily,
    annotations: Map<String, VerseAnnotations> = emptyMap(),
    memorizedVerseIds: Set<String> = emptySet(),
) {
    if (verse == null) return
    val scheme = MaterialTheme.colorScheme
    val neighborSize = fontSize.toSp() * 0.8f
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = verse.arabicText,
            fontFamily = verseFont,
            fontSize = neighborSize,
            lineHeight = neighborSize * 1.6f,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(alpha = 0.3f, scaleX = 0.95f, scaleY = 0.95f),
        )
        val neighborAnnotation = annotations[verse.id]
        Row(modifier = Modifier.align(Alignment.TopEnd)) {
            if (neighborAnnotation?.hasNote == true) {
                NoteGlyph(
                    color = scheme.secondary,
                    filled = true,
                    size = 14.dp,
                    contentDescription = stringResource(Res.string.has_note_indicator),
                )
            }
            if (neighborAnnotation?.isBookmarked == true) {
                BookmarkGlyph(
                    color = scheme.secondary,
                    filled = true,
                    size = 14.dp,
                    contentDescription = stringResource(Res.string.bookmarked_indicator),
                )
            }
            if (verse.id in memorizedVerseIds) {
                MemorizedGlyph(
                    color = scheme.secondary,
                    filled = true,
                    size = 14.dp,
                    contentDescription = stringResource(Res.string.memorized_indicator),
                )
            }
        }
    }
}

/** The highlighted active verse: a raised card, a leading accent bar, and a verse-number chip. */
@Composable
private fun ActiveVerseCard(
    verse: VerseRow,
    fontSize: ReadingFontSize,
    verseFont: androidx.compose.ui.text.font.FontFamily,
    annotations: Map<String, VerseAnnotations> = emptyMap(),
    memorizedVerseIds: Set<String> = emptySet(),
    onToggleBookmark: (String) -> Unit = {},
    onOpenNoteEditor: (String) -> Unit = {},
    onToggleMemorized: (String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val annotation = annotations[verse.id]
    val isBookmarked = annotation?.isBookmarked == true
    val hasNote = annotation?.hasNote == true
    val isMemorized = verse.id in memorizedVerseIds
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MatnShapes.xl)
            .background(scheme.surfaceContainerLow)
            .border(width = 1.dp, color = scheme.outlineVariant.copy(alpha = 0.3f), shape = MatnShapes.xl)
            .padding(horizontal = MatnSpacing.gutter, vertical = MatnSpacing.unit * 5),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(48.dp)
                .background(scheme.primary),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // In-flow action row (not an overlay) — reserves its own height so long verse text
            // never renders underneath the icons, regardless of line count (fixed after an
            // on-device check showed text colliding with an absolutely-positioned TopEnd row).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onOpenNoteEditor(verse.id) }) {
                    NoteGlyph(
                        color = if (hasNote) scheme.secondary else scheme.onSurfaceVariant,
                        filled = hasNote,
                        contentDescription = stringResource(Res.string.toggle_note),
                    )
                }
                IconButton(onClick = { onToggleBookmark(verse.id) }) {
                    BookmarkGlyph(
                        color = if (isBookmarked) scheme.secondary else scheme.onSurfaceVariant,
                        filled = isBookmarked,
                        contentDescription = stringResource(Res.string.toggle_bookmark),
                    )
                }
                IconButton(onClick = { onToggleMemorized(verse.id) }) {
                    MemorizedGlyph(
                        color = if (isMemorized) scheme.secondary else scheme.onSurfaceVariant,
                        filled = isMemorized,
                        contentDescription = stringResource(Res.string.toggle_memorized),
                    )
                }
            }
            Text(
                text = verse.arabicText,
                fontFamily = verseFont,
                fontSize = fontSize.toSp(),
                lineHeight = fontSize.toSp() * 1.6f,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.height(MatnSpacing.unit * 3))
            VerseMetaRow(displayNumber = verse.displayNumber)
        }
    }
}

/** "— البيت ٢ —": a hairline rule broken by the verse-number label, per the Stitch carousel design. */
@Composable
private fun VerseMetaRow(displayNumber: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(32.dp).height(1.dp).background(scheme.outlineVariant))
        Text(
            text = "${stringResource(Res.string.player_now_playing)} $displayNumber",
            style = arabicLabelSmall(),
            color = scheme.outline,
            modifier = Modifier.padding(horizontal = MatnSpacing.unit),
        )
        Box(modifier = Modifier.width(32.dp).height(1.dp).background(scheme.outlineVariant))
    }
}

// --------------------------------------------------------------------------- Previews

private fun previewVerse(id: String, number: Int, text: String) =
    VerseRow(id = id, displayNumber = number, arabicText = text, durationMs = 4000, chapterId = null)

@Preview
@Composable
private fun ReadingCarouselMidMatnPreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = previewVerse("v1", 1, "يَقُولُ رَاجِي عَفْوِ رَبٍّ سَامِعِ مُحَمَّدُ بْنُ الْجَزَرِيِّ الشَّافِعِي"),
                activeVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
                nextVerse = previewVerse("v3", 3, "مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَمُقْرِئِ الْقُرْآنِ مَعْ مُحِبِّهِ"),
            ),
        )
    }
}

@Preview
@Composable
private fun ReadingCarouselFirstVersePreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = null,
                activeVerse = previewVerse("v1", 1, "يَقُولُ رَاجِي عَفْوِ رَبٍّ سَامِعِ مُحَمَّدُ بْنُ الْجَزَرِيِّ الشَّافِعِي"),
                nextVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
            ),
        )
    }
}

@Preview
@Composable
private fun ReadingCarouselLastVersePreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
                activeVerse = previewVerse("v3", 3, "مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَمُقْرِئِ الْقُرْآنِ مَعْ مُحِبِّهِ"),
                nextVerse = null,
            ),
        )
    }
}

@Preview
@Composable
private fun ReadingCarouselBookmarkedActiveVersePreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = previewVerse("v1", 1, "يَقُولُ رَاجِي عَفْوِ رَبٍّ سَامِعِ مُحَمَّدُ بْنُ الْجَزَرِيِّ الشَّافِعِي"),
                activeVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
                nextVerse = previewVerse("v3", 3, "مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَمُقْرِئِ الْقُرْآنِ مَعْ مُحِبِّهِ"),
            ),
            annotations = mapOf("v2" to com.giraffe.matn.domain.model.VerseAnnotations("v2", isBookmarked = true, hasNote = false)),
        )
    }
}

@Preview
@Composable
private fun ReadingCarouselNotedActiveVersePreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = previewVerse("v1", 1, "يَقُولُ رَاجِي عَفْوِ رَبٍّ سَامِعِ مُحَمَّدُ بْنُ الْجَزَرِيِّ الشَّافِعِي"),
                activeVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
                nextVerse = previewVerse("v3", 3, "مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَمُقْرِئِ الْقُرْآنِ مَعْ مُحِبِّهِ"),
            ),
            annotations = mapOf("v2" to com.giraffe.matn.domain.model.VerseAnnotations("v2", isBookmarked = false, hasNote = true)),
        )
    }
}

@Preview
@Composable
private fun ReadingCarouselBookmarkedAndNotedActiveVersePreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = previewVerse("v1", 1, "يَقُولُ رَاجِي عَفْوِ رَبٍّ سَامِعِ مُحَمَّدُ بْنُ الْجَزَرِيِّ الشَّافِعِي"),
                activeVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
                nextVerse = previewVerse("v3", 3, "مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَمُقْرِئِ الْقُرْآنِ مَعْ مُحِبِّهِ"),
            ),
            annotations = mapOf("v2" to com.giraffe.matn.domain.model.VerseAnnotations("v2", isBookmarked = true, hasNote = true)),
        )
    }
}

@Preview
@Composable
private fun ReadingCarouselMemorizedActiveVersePreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = previewVerse("v1", 1, "يَقُولُ رَاجِي عَفْوِ رَبٍّ سَامِعِ مُحَمَّدُ بْنُ الْجَزَرِيِّ الشَّافِعِي"),
                activeVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
                nextVerse = previewVerse("v3", 3, "مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَمُقْرِئِ الْقُرْآنِ مَعْ مُحِبِّهِ"),
            ),
            memorizedVerseIds = setOf("v2"),
        )
    }
}

@Preview
@Composable
private fun ReadingCarouselMemorizedBookmarkedAndNotedActiveVersePreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = previewVerse("v1", 1, "يَقُولُ رَاجِي عَفْوِ رَبٍّ سَامِعِ مُحَمَّدُ بْنُ الْجَزَرِيِّ الشَّافِعِي"),
                activeVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
                nextVerse = previewVerse("v3", 3, "مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَمُقْرِئِ الْقُرْآنِ مَعْ مُحِبِّهِ"),
            ),
            annotations = mapOf("v2" to com.giraffe.matn.domain.model.VerseAnnotations("v2", isBookmarked = true, hasNote = true)),
            memorizedVerseIds = setOf("v2"),
        )
    }
}

@Preview
@Composable
private fun ReadingCarouselUnbookmarkedActiveVersePreview() {
    MatnTheme {
        ReadingCarousel(
            state = ReadingCarouselUiState(
                previousVerse = previewVerse("v1", 1, "يَقُولُ رَاجِي عَفْوِ رَبٍّ سَامِعِ مُحَمَّدُ بْنُ الْجَزَرِيِّ الشَّافِعِي"),
                activeVerse = previewVerse("v2", 2, "الْحَمْدُ لِلَّهِ وَصَلَّى اللَّهُ عَلَى نَبِيِّهِ وَمُصْطَفَاهُ"),
                nextVerse = previewVerse("v3", 3, "مُحَمَّدٍ وَآلِهِ وَصَحْبِهِ وَمُقْرِئِ الْقُرْآنِ مَعْ مُحِبِّهِ"),
            ),
        )
    }
}
