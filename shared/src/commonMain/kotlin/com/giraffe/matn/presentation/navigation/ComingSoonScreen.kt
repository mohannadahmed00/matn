package com.giraffe.matn.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.coming_soon_back_to_library
import matn.shared.generated.resources.coming_soon_message
import matn.shared.generated.resources.coming_soon_title
import org.jetbrains.compose.resources.stringResource

/**
 * Shared placeholder for not-yet-built top-level destinations (Goals, Notes; Settings unless a
 * minimal screen exists) — specs/010-design-system-adoption User Story 3, FR-007. Stateless, no
 * ViewModel (Principle II): the tab it was reached from only changes its icon/glyph, not its
 * behavior, so [tab] is display-only here.
 */
@Composable
fun ComingSoonScreen(tab: NavigationTab, onBackToLibrary: () -> Unit = {}, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize().padding(MatnSpacing.gutter), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NavTabIcon(tab = tab, color = scheme.primary)
            Text(
                text = stringResource(Res.string.coming_soon_title),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MatnSpacing.unit * 2),
            )
            Text(
                text = stringResource(Res.string.coming_soon_message),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MatnSpacing.unit, bottom = MatnSpacing.gutter),
            )
            Button(onClick = onBackToLibrary, shape = MatnShapes.full) {
                Text(stringResource(Res.string.coming_soon_back_to_library), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun ComingSoonGoalsPreview() {
    MatnTheme { ComingSoonScreen(tab = NavigationTab.GOALS) }
}

@Preview
@Composable
private fun ComingSoonNotesPreview() {
    MatnTheme { ComingSoonScreen(tab = NavigationTab.NOTES) }
}

@Preview
@Composable
private fun ComingSoonSettingsPreview() {
    MatnTheme { ComingSoonScreen(tab = NavigationTab.SETTINGS) }
}
