package com.giraffe.matn.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.giraffe.matn.domain.model.ContinueLearningEntry
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.continue_learning_dismiss
import matn.shared.generated.resources.continue_learning_resume
import matn.shared.generated.resources.continue_learning_title
import matn.shared.generated.resources.continue_learning_verse
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Home-screen "Continue Learning" hero card (Phase 4 / FR-015). A reusable, **stateless,
 * parameterized** composable (constitution Principle VIII): parameters + intent lambdas only —
 * no ViewModel, no DI, no repository inside it. The Home screen renders **nothing at all** when
 * the entry is null (FR-015); this card only exists for the non-null case.
 *
 * Implemented from the **Home / Library** Stitch design's Continue Learning region (screen
 * `618643f891144557b5a4ddf4bbad0c03`, region 4, registered in `docs/DESIGN-SOURCE.md`): a hero
 * card on `primaryContainer` with `onPrimaryContainer` content — a label chip, the matn title
 * large and bold, the verse anchor beneath, and a light pill button with a play glyph. Two
 * deliberate adaptations: the design's progress bar is omitted (Phase 4 produces **no** progress
 * signal — Principle VII / Phase 6 decoupling), and a dismiss affordance is added (FR-017/FR-017a
 * requires one; `null` design regions cannot veto a functional requirement). Styling is drawn
 * entirely from theme tokens — no hard-coded hex colors.
 */
@Composable
fun ContinueLearningCard(
    entry: ContinueLearningEntry,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MatnShapes.xl,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.clickable(onClick = onResume).padding(MatnSpacing.gutter)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Label chip — design: bg-primary/20 pill with on-primary-container text.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = stringResource(Res.string.continue_learning_title),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = MatnSpacing.unit + 4.dp, vertical = MatnSpacing.unit / 2),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val dismissLabel = stringResource(Res.string.continue_learning_dismiss)
                Surface(
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .semantics { contentDescription = dismissLabel },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = "×",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = MatnSpacing.unit, vertical = MatnSpacing.unit / 4),
                    )
                }
            }
            Text(
                text = entry.matnTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MatnSpacing.unit + 4.dp),
            )
            Text(
                text = stringResource(Res.string.continue_learning_verse, entry.verseDisplayNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = MatnSpacing.unit / 4),
            )
            // Resume pill — design: on-primary-container background, primary-container text,
            // rounded-full, filled play glyph leading the label.
            Surface(
                modifier = Modifier.padding(top = MatnSpacing.unit * 2).clickable(onClick = onResume),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                contentColor = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = MatnSpacing.unit * 3, vertical = MatnSpacing.unit + 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit),
                ) {
                    // ▶ play glyph — no Material Icons dependency (rule 6); the manuscript design
                    // language uses text glyphs throughout (cf. CoverImage's "م").
                    Text(text = "▶", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = stringResource(Res.string.continue_learning_resume),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — normal entry, and a very long matn title to prove truncation (blocking review item).
// ---------------------------------------------------------------------------------------------

private fun sampleEntry(title: String) = ContinueLearningEntry(
    matnId = "b3f1e2a4-0000-4000-8000-000000000001",
    matnTitle = title,
    verseDisplayNumber = 3,
    verseId = "b3f1e2a4-0000-4000-8000-0000000000v3",
)

@Preview
@Composable
private fun ContinueLearningCardPreview() {
    MatnTheme {
        Box(modifier = Modifier.padding(MatnSpacing.gutter)) {
            ContinueLearningCard(
                entry = sampleEntry("الأجرومية"),
                onResume = {},
                onDismiss = {},
            )
        }
    }
}

@Preview
@Composable
private fun ContinueLearningCardLongTitlePreview() {
    MatnTheme {
        Box(modifier = Modifier.padding(MatnSpacing.gutter)) {
            ContinueLearningCard(
                entry = sampleEntry(
                    "حاشية الأمير على متن السيد البكري في علم النحو والصرف والمعاني والبيان والبديع",
                ),
                onResume = {},
                onDismiss = {},
            )
        }
    }
}
