package com.giraffe.matn.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.MatnSummary
import com.giraffe.matn.domain.model.StructureKind
import com.giraffe.matn.presentation.common.CoverImage
import com.giraffe.matn.presentation.common.formatDuration
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.library_empty
import matn.shared.generated.resources.verses_count
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * Home / library screen (US2) — **stateful** entry. Hoists the [HomeViewModel]'s state and
 * delegates rendering to the stateless [HomeContent], so the render layer stays a pure function
 * of [HomeUiState] (Principle II) and is previewable/screenshot-testable without a ViewModel.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenMatn: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state = state, onOpenMatn = onOpenMatn)
}

/**
 * Stateless library grid. A 2-column [LazyVerticalGrid] of matn cards **keyed by stable
 * `matn.id`** (FR-011/SC-003). Each card shows cover (or placeholder), title, author, verse
 * count, and total duration. Tapping a card invokes [onOpenMatn] (FR-003/SC-001). When the store
 * is empty a centered localized empty state is shown instead of the grid (FR-004/SC-008). RTL
 * throughout (provided by [MatnTheme]). No network (FR-018/SC-006).
 */
@Composable
fun HomeContent(state: HomeUiState, onOpenMatn: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
            state.isEmpty -> Text(
                text = stringResource(Res.string.library_empty),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = state.items, key = { it.matn.id }) { summary ->
                    MatnCard(summary = summary, onClick = { onOpenMatn(summary.matn.id) })
                }
            }
        }
    }
}

@Composable
private fun MatnCard(summary: MatnSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        CoverImage(
            coverImageRef = summary.matn.coverImageRef,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f),
        )
        Text(
            text = summary.matn.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = summary.matn.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        val totals = stringResource(Res.string.verses_count, summary.verseCount) +
            " · " + formatDuration(summary.totalDurationMs)
        Text(
            text = totals,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Previews — every view/component in this file has one (light Material 3, RTL via MatnTheme).
// ---------------------------------------------------------------------------------------------

private fun previewSummary(id: String, title: String, count: Int, total: Long) = MatnSummary(
    matn = Matn(
        id = id,
        title = title,
        author = "ابن آجُرُّوم",
        description = "",
        coverImageRef = null,
        structureKind = StructureKind.SIMPLE,
    ),
    verseCount = count,
    totalDurationMs = total,
)

@Preview
@Composable
private fun HomeContentPopulatedPreview() {
    MatnTheme {
        HomeContent(
            state = HomeUiState(
                isLoading = false,
                items = listOf(
                    previewSummary("m1", "الأجرومية", 4, 31_300),
                    previewSummary("m2", "متن الآجرومية مبوب", 5, 39_900),
                ),
            ),
            onOpenMatn = {},
        )
    }
}

@Preview
@Composable
private fun HomeContentEmptyPreview() {
    MatnTheme {
        HomeContent(state = HomeUiState(isLoading = false, isEmpty = true), onOpenMatn = {})
    }
}

@Preview
@Composable
private fun HomeContentLoadingPreview() {
    MatnTheme {
        HomeContent(state = HomeUiState(isLoading = true), onOpenMatn = {})
    }
}

@Preview
@Composable
private fun MatnCardPreview() {
    MatnTheme {
        Box(modifier = Modifier.width(180.dp).padding(8.dp)) {
            MatnCard(summary = previewSummary("m1", "الأجرومية", 4, 31_300), onClick = {})
        }
    }
}
