package com.giraffe.matn.teacher.presentation.common

import com.giraffe.matn.core.AppError

/** The audio slot a [VerseRow] renders (`contracts/teacher-ui-contract.md` §1). Held in the
 * ViewModel, keyed by verse id — never a `remember` inside the row, so a scrolled-away row that
 * resumes still shows its true progress. [Failed] carries the raw [AppError], resolved to text
 * only at render time (`TeacherStrings`) — the same pattern as `SaveState.Failed`/`CoverError`, so
 * the failure reason survives a language switch. */
sealed interface VerseAudioUiState {
    data object Empty : VerseAudioUiState
    data class Uploading(val uploadedBytes: Long, val totalBytes: Long) : VerseAudioUiState
    data class Loaded(val durationMs: Long, val isPlaying: Boolean = false) : VerseAudioUiState
    data class Failed(val error: AppError) : VerseAudioUiState
}
