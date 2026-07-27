package com.giraffe.matn.teacher.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.presentation.editor.SaveState
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import com.giraffe.matn.teacher.presentation.strings.messageFor
import java.time.Instant
import java.time.ZoneId

/** Editor + portal shell save-state display (`contracts/teacher-ui-contract.md` §3.4). */
@Composable
fun SaveStateIndicator(state: SaveState, modifier: Modifier = Modifier) {
    val strings = LocalTeacherStrings.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MatnSpacing.unit)) {
        when (state) {
            SaveState.Idle -> Text(strings.saveStateIdle, style = MaterialTheme.typography.labelSmall)
            SaveState.Autosaving, SaveState.Saving -> {
                CircularProgressIndicator(modifier = Modifier.size(MatnSpacing.unit * 2))
                Text(strings.saveStateAutosaving, style = MaterialTheme.typography.labelSmall)
            }
            is SaveState.Saved -> Text(
                text = strings.saveStateSavedAt.replace("%s", formatSavedAt(state.at)),
                style = MaterialTheme.typography.labelSmall,
            )
            is SaveState.Failed -> Text(
                text = "${strings.saveStateFailed}: ${strings.messageFor(state.error)}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun formatSavedAt(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

@Preview
@Composable
private fun SaveStateIdlePreview() = PreviewScaffold(TeacherLanguage.ENGLISH) { SaveStateIndicator(SaveState.Idle) }

@Preview
@Composable
private fun SaveStateAutosavingPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) { SaveStateIndicator(SaveState.Autosaving) }

@Preview
@Composable
private fun SaveStateSavedPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) { SaveStateIndicator(SaveState.Saved(0L)) }

@Preview
@Composable
private fun SaveStateFailedPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) { SaveStateIndicator(SaveState.Failed(RemoteError.Network)) }

@Preview
@Composable
private fun SaveStateIdleArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) { SaveStateIndicator(SaveState.Idle) }
