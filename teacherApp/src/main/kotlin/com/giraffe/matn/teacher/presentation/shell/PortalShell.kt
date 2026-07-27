package com.giraffe.matn.teacher.presentation.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

enum class PortalDestination { DASHBOARD, UPLOAD_MATN, LIBRARY_MANAGEMENT, SYSTEM_SETTINGS }

data class PortalShellState(
    val displayName: String,
    val selectedDestination: PortalDestination,
    /** `null` while loading or on failure — informational only, shows nothing rather than an error. */
    val storageUsageText: String?,
    val storageUsageFraction: Float,
)

/** `contracts/teacher-ui-contract.md` §3.2 — the sole owner of the nav; no screen draws its own. */
@Composable
fun PortalShellContent(
    state: PortalShellState,
    onDestinationSelected: (PortalDestination) -> Unit,
    onLanguageToggle: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(modifier = modifier.fillMaxSize()) {
        NavRail(state, onDestinationSelected, onLanguageToggle, onSignOut)
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content()
        }
    }
}

@Composable
private fun NavRail(
    state: PortalShellState,
    onDestinationSelected: (PortalDestination) -> Unit,
    onLanguageToggle: () -> Unit,
    onSignOut: () -> Unit,
) {
    val strings = LocalTeacherStrings.current
    Column(
        modifier = Modifier
            .width(MatnSpacing.unit * 35)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(MatnSpacing.unit * 2),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
            Text(
                text = strings.appName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = MatnSpacing.unit),
            )

            NavItem(strings.navDashboard, state.selectedDestination == PortalDestination.DASHBOARD) {
                onDestinationSelected(PortalDestination.DASHBOARD)
            }
            NavItem(strings.navUploadMatn, state.selectedDestination == PortalDestination.UPLOAD_MATN) {
                onDestinationSelected(PortalDestination.UPLOAD_MATN)
            }
            NavItem(strings.navLibraryManagement, state.selectedDestination == PortalDestination.LIBRARY_MANAGEMENT) {
                onDestinationSelected(PortalDestination.LIBRARY_MANAGEMENT)
            }
            NavItem(strings.navSystemSettings, state.selectedDestination == PortalDestination.SYSTEM_SETTINGS) {
                onDestinationSelected(PortalDestination.SYSTEM_SETTINGS)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
            StorageUsageCard(state)

            Column(verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit / 2)) {
                Text(text = state.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(text = strings.teacherPortalLabel, style = MaterialTheme.typography.labelSmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
                TextButton(onClick = onLanguageToggle) { Text(strings.languageSwitch) }
                TextButton(onClick = onSignOut) { Text(strings.signOut) }
            }
        }
    }
}

@Composable
private fun StorageUsageCard(state: PortalShellState) {
    val strings = LocalTeacherStrings.current
    val usageText = state.storageUsageText ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(MatnSpacing.unit))
            .padding(MatnSpacing.unit * 2),
    ) {
        Text(text = strings.storageUsageLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(
            progress = { state.storageUsageFraction },
            modifier = Modifier.fillMaxWidth().padding(vertical = MatnSpacing.unit),
        )
        Text(text = usageText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MatnSpacing.unit * 6)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(MatnSpacing.unit),
            )
            .padding(horizontal = MatnSpacing.unit * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun PortalShellArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    PortalShellContent(
        state = PortalShellState("أحمد", PortalDestination.UPLOAD_MATN, "4.2 GB / 10 GB", 0.42f),
        onDestinationSelected = {},
        onLanguageToggle = {},
        onSignOut = {},
    ) {
        Text("content")
    }
}

@Preview
@Composable
private fun PortalShellEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    PortalShellContent(
        state = PortalShellState("Ustadh Ahmed", PortalDestination.DASHBOARD, "4.2 GB / 10 GB", 0.42f),
        onDestinationSelected = {},
        onLanguageToggle = {},
        onSignOut = {},
    ) {
        Text("content")
    }
}
