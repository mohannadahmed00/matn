package com.giraffe.matn.domain.error

import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.audio.AudioProfile

/** Rejections `AttachVerseAudioUseCase`/`LoadSplitSourceUseCase` raise before any upload is
 * attempted — distinct from [com.giraffe.matn.domain.error.RemoteError], which is a backend failure. */
sealed interface AudioAttachError : AppError {
    /** `Mp3FrameIndex.build` found no valid first frame (FR-003) — the chooser's extension filter
     * is not the boundary; this is. Also used for the chooser's own extension rejection (a file
     * merely named `.mp3` that isn't one either way). */
    data object WrongFormat : AudioAttachError

    /** FR-005b: every audio-bearing verse in a matn must share one profile. Names both so the
     * teacher-facing message can state "this matn's audio is X; this file is Y". */
    data class ProfileMismatch(val existing: AudioProfile, val incoming: AudioProfile) : AudioAttachError

    /** FR-004/FR-004a: the chooser's 10 MB per-verse ceiling, rejected before any read. */
    data object TooLarge : AudioAttachError

    /** FR-004: the 300 MB ceiling on a *split source*, which is a different (much larger) limit
     * than [TooLarge]'s per-verse one — a continuous recording covering a whole matn is expected
     * to be far bigger than any single verse. */
    data object SourceTooLarge : AudioAttachError

    /** FR-004: the 4-hour ceiling on a split source. */
    data object SourceTooLong : AudioAttachError
}
