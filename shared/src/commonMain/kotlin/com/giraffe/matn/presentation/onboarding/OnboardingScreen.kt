package com.giraffe.matn.presentation.onboarding

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.ThemeMode
import com.giraffe.matn.presentation.theme.LocalReduceMotion
import com.giraffe.matn.presentation.theme.MatnMotion
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.theme.verseFontFamily
import matn.shared.generated.resources.Res
import matn.shared.generated.resources.app_title
import matn.shared.generated.resources.onboarding_continue
import matn.shared.generated.resources.onboarding_p1_tagline
import matn.shared.generated.resources.onboarding_p2_body
import matn.shared.generated.resources.onboarding_p2_title
import matn.shared.generated.resources.onboarding_p3_body
import matn.shared.generated.resources.onboarding_p3_title
import matn.shared.generated.resources.onboarding_skip
import matn.shared.generated.resources.onboarding_start
import org.jetbrains.compose.resources.stringResource

/**
 * Stateful holder (Principle II) — first-launch flow (US3, onboarding-permissions-contract.md
 * §2). Reacts to [OnboardingUiState.isCompleted] by calling [onCompleted], which the nav host
 * wires to pop this route off the back stack (contract §2.3) — completing here never itself
 * navigates, keeping the screen a pure function of state (Principle II).
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onCompleted: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) onCompleted()
    }
    OnboardingContent(
        state = state,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip,
    )
}

/**
 * Three panels explaining what Matn is for and, critically, how content download works
 * (FR-017/FR-024) — requests **no permission anywhere** (rule 5) and makes **no network access**:
 * every panel is bundled copy. Skip is available on every panel (FR-018).
 */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onNext: () -> Unit = {},
    onSkip: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(MatnSpacing.gutter)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(Res.string.onboarding_skip), color = scheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            // T099 (US5, adaptive-motion-contract.md §B3): the fetched design's logo-reveal is a
            // real reveal (unlike the rejected fake progress bar, research D9) — no artificial
            // delay, no loading indicator, just a fade-in of the panel that just became current.
            // Suppressed entirely under reduce motion (static, immediate).
            val reduceMotion = LocalReduceMotion.current
            androidx.compose.animation.AnimatedContent(
                targetState = state.panelIndex,
                transitionSpec = {
                    val duration = if (reduceMotion) 0 else MatnMotion.durationLong
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(duration)) togetherWith
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(if (reduceMotion) 0 else MatnMotion.durationShort))
                },
                label = "onboardingPanel",
                modifier = Modifier.fillMaxWidth(),
            ) { panelIndex ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (panelIndex) {
                        0 -> OnboardingPanelOne()
                        1 -> OnboardingPanelTwo()
                        else -> OnboardingPanelThree()
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onNext,
                shape = MatnShapes.full,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.primary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(if (state.isLastPanel) Res.string.onboarding_start else Res.string.onboarding_continue),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Panel 1 — brand mark + tagline, the fetched *Splash Screen* treatment (contract §2.1). */
@Composable
private fun OnboardingPanelOne() {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = stringResource(Res.string.app_title),
        fontFamily = verseFontFamily(),
        style = MaterialTheme.typography.displayMedium,
        color = scheme.primary,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(Res.string.onboarding_p1_tagline),
        style = MaterialTheme.typography.titleMedium,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = MatnSpacing.unit * 2),
    )
}

/** Panel 2 — what the app is for: read, listen, repeat, memorize (contract §2.1). */
@Composable
private fun OnboardingPanelTwo() {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = stringResource(Res.string.onboarding_p2_title),
        style = MaterialTheme.typography.headlineSmall,
        color = scheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(Res.string.onboarding_p2_body),
        style = MaterialTheme.typography.bodyLarge,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = MatnSpacing.unit * 2),
    )
}

/** Panel 3 — the download model: this is the panel that earns US3 its place (contract §2.1). */
@Composable
private fun OnboardingPanelThree() {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = stringResource(Res.string.onboarding_p3_title),
        style = MaterialTheme.typography.headlineSmall,
        color = scheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(Res.string.onboarding_p3_body),
        style = MaterialTheme.typography.bodyLarge,
        color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = MatnSpacing.unit * 2),
    )
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun OnboardingPanel1LightPreview() {
    MatnTheme(themeMode = ThemeMode.LIGHT) { OnboardingContent(state = OnboardingUiState(panelIndex = 0)) }
}

@Preview
@Composable
private fun OnboardingPanel1DarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) { OnboardingContent(state = OnboardingUiState(panelIndex = 0)) }
}

@Preview
@Composable
private fun OnboardingPanel2LightPreview() {
    MatnTheme(themeMode = ThemeMode.LIGHT) { OnboardingContent(state = OnboardingUiState(panelIndex = 1)) }
}

@Preview
@Composable
private fun OnboardingPanel2DarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) { OnboardingContent(state = OnboardingUiState(panelIndex = 1)) }
}

@Preview
@Composable
private fun OnboardingPanel3LightPreview() {
    MatnTheme(themeMode = ThemeMode.LIGHT) { OnboardingContent(state = OnboardingUiState(panelIndex = 2)) }
}

@Preview
@Composable
private fun OnboardingPanel3DarkPreview() {
    MatnTheme(themeMode = ThemeMode.DARK) { OnboardingContent(state = OnboardingUiState(panelIndex = 2)) }
}
