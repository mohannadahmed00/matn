package com.giraffe.matn.presentation.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.giraffe.matn.domain.model.RepeatCount
import com.giraffe.matn.domain.model.RepetitionSettings
import com.giraffe.matn.presentation.theme.MatnTheme

/**
 * Stateful holder (Principle II): collects [PlayerBarViewModel.state] and forwards the drill
 * intents to the stateless [DrillPanelContent]. Hosted alongside the player bar on the reading
 * screen.
 */
@Composable
fun DrillPanel(viewModel: PlayerBarViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.visible) {
        DrillPanelContent(
            settings = state.settings,
            onVerseRepeatChange = viewModel::onVerseRepeatSelected,
            onMatnRepeatChange = viewModel::onMatnRepeatSelected,
        )
    }
}

/**
 * Stateless drill configuration — a verse-repeat stepper and a matn-repeat (pass) stepper, each
 * 1–99 plus an explicit ∞ toggle. Every value a counter can hold is directly selectable, including
 * ∞ (FR-016 acceptance scenario 4) — never rendered as blank, `0`, or a missing setting.
 */
@Composable
fun DrillPanelContent(
    settings: RepetitionSettings,
    onVerseRepeatChange: (RepeatCount) -> Unit = {},
    onMatnRepeatChange: (RepeatCount) -> Unit = {},
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Vr",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RepeatCountStepper(
            value = settings.verseRepeat,
            onValueChange = onVerseRepeatChange,
            modifier = Modifier.padding(start = 8.dp, end = 20.dp),
        )
        Text(
            text = "Mr",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RepeatCountStepper(
            value = settings.matnRepeat,
            onValueChange = onMatnRepeatChange,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A −/value/+ stepper over [RepeatCount], with ∞ as an explicit, always-selectable step. */
@Composable
fun RepeatCountStepper(
    value: RepeatCount,
    onValueChange: (RepeatCount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                onValueChange(
                    when (value) {
                        RepeatCount.Unlimited -> RepeatCount.of(RepeatCount.MAX)
                        is RepeatCount.Finite -> RepeatCount.of(value.value - 1)
                    },
                )
            },
        ) {
            Text("−", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
        Surface(
            color = scheme.secondaryContainer,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = if (value.isUnlimited) "∞" else (value as RepeatCount.Finite).value.toString(),
                style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
                color = scheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .size(width = 20.dp, height = 20.dp),
            )
        }
        IconButton(
            onClick = {
                onValueChange(
                    when (value) {
                        RepeatCount.Unlimited -> RepeatCount.Unlimited
                        is RepeatCount.Finite ->
                            if (value.value >= RepeatCount.MAX) RepeatCount.Unlimited
                            else RepeatCount.of(value.value + 1)
                    },
                )
            },
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        }
    }
}

// --------------------------------------------------------------------------- Previews

@Preview
@Composable
private fun DrillPanelDefaultPreview() {
    MatnTheme {
        DrillPanelContent(settings = RepetitionSettings())
    }
}

@Preview
@Composable
private fun DrillPanelFiniteSevenPreview() {
    MatnTheme {
        DrillPanelContent(settings = RepetitionSettings(verseRepeat = RepeatCount.of(7)))
    }
}

@Preview
@Composable
private fun DrillPanelUnlimitedPreview() {
    MatnTheme {
        DrillPanelContent(settings = RepetitionSettings(verseRepeat = RepeatCount.Unlimited))
    }
}

@Preview
@Composable
private fun DrillPanelVr5Mr3Preview() {
    MatnTheme {
        DrillPanelContent(
            settings = RepetitionSettings(verseRepeat = RepeatCount.of(5), matnRepeat = RepeatCount.of(3)),
        )
    }
}

@Preview
@Composable
private fun DrillPanelMrUnlimitedPreview() {
    MatnTheme {
        DrillPanelContent(settings = RepetitionSettings(matnRepeat = RepeatCount.Unlimited))
    }
}
