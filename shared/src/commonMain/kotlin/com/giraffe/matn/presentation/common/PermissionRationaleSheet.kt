package com.giraffe.matn.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnMotion
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.onboarding_continue
import matn.shared.generated.resources.permission_rationale_body
import matn.shared.generated.resources.permission_rationale_not_now
import matn.shared.generated.resources.permission_rationale_title
import org.jetbrains.compose.resources.stringResource

/**
 * T076 (US3, onboarding-permissions-contract.md §4 step 3) — the in-app rationale shown at first
 * playback, before the system permission prompt. **Playback is never blocked on the answer**: this
 * sheet only decides whether [onContinue] (which requests the system prompt) or [onDismiss] (which
 * declines silently) runs — [com.giraffe.matn.playback.PlaybackController] has already started the
 * session regardless. Follows the [InstallPromptSheet] idiom (Principle VIII, second use).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleSheet(onContinue: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // T098 (US5, adaptive-motion-contract.md §B3): a content-level fade layered on top of
    // ModalBottomSheet's own slide-up (not developer-overridable in this Material3 version).
    val reduceMotion = LocalReduceMotion.current
    val fade = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(MatnMotion.durationMedium)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = scheme.surface,
        shape = MatnShapes.xl,
    ) {
        // FR-029: bounded to MatnSpacing.surfaceMaxWidth and centred on wide windows, matching
        // every other sheet in the app (T088).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MatnSpacing.surfaceMaxWidth)
                .align(Alignment.CenterHorizontally)
                .alpha(fade.value)
                .padding(horizontal = MatnSpacing.gutter)
                .padding(bottom = MatnSpacing.gutter),
        ) {
            Text(
                text = stringResource(Res.string.permission_rationale_title),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(bottom = MatnSpacing.unit),
            )
            Text(
                text = stringResource(Res.string.permission_rationale_body),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = MatnSpacing.gutter),
            )
            Button(
                onClick = onContinue,
                shape = MatnShapes.full,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.onboarding_continue), style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
                Text(
                    text = stringResource(Res.string.permission_rationale_not_now),
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
private fun PermissionRationaleSheetLightPreview() {
    MatnTheme(themeMode = ThemeMode.LIGHT) {
        PermissionRationaleSheet(onContinue = {}, onDismiss = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PermissionRationaleSheetDarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) {
        PermissionRationaleSheet(onContinue = {}, onDismiss = {})
    }
}
