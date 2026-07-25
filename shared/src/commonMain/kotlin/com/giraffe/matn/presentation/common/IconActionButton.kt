package com.giraffe.matn.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription as stateDescriptionSemantics
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource

/**
 * T057 (US2, accessibility-contract.md §3) — the structural guarantee behind FR-009/FR-008: [action]
 * is required and non-nullable, so an icon-only control that skips a label does not compile. Applies
 * [Modifier.minimumInteractiveComponentSize] so the 48dp touch-target floor (FR-008) comes for free
 * regardless of [glyph]'s intrinsic size.
 */
@Composable
fun IconActionButton(
    action: A11yAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    stateDescription: String? = null,
    enabled: Boolean = true,
    glyph: @Composable (Color) -> Unit,
) {
    val label = stringResource(A11yLabels.getValue(action))
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false),
                enabled = enabled,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                contentDescription = label
                if (stateDescription != null) stateDescriptionSemantics = stateDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        glyph(LocalContentColor.current)
    }
}

@Preview
@Composable
private fun IconActionButtonPreview() {
    com.giraffe.matn.presentation.theme.MatnTheme {
        IconActionButton(action = A11yAction.CLOSE, onClick = {}) { color ->
            androidx.compose.material3.Text(text = "×", color = color, style = MaterialTheme.typography.titleMedium)
        }
    }
}
