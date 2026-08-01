package com.giraffe.matn.teacher.presentation.strings

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import com.giraffe.matn.core.AppError
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.error.AudioAttachError
import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.presentation.common.formatBytes

/**
 * The teacher tool's own string table (research D5): Compose Multiplatform 1.11.1 exposes no
 * public runtime locale override, so `:teacherApp` cannot switch language via `stringResource`.
 * Being an interface, a missing translation in [ArabicStrings]/[EnglishStrings] is a compile
 * error, not a silent fallback. Labels start from `stitch-designs/11-Upload-Per-Verse.html`
 * (Principle VIII); [T028a][messageFor] adds the error-message keys and T073 the validation
 * messages — nothing else may defer to "some later task".
 */
interface TeacherStrings {
    // Portal chrome (contracts/teacher-ui-contract.md §3.2)
    val appName: String
    val navDashboard: String
    val navUploadMatn: String
    val navLibraryManagement: String
    val navSystemSettings: String
    val teacherPortalLabel: String
    val storageUsageLabel: String
    val storageUsageFormat: String // "%s of %s used"
    val signOut: String
    val languageSwitch: String
    val secretStoreUnprotectedWarning: String
    val screenNotYetAvailable: String

    // Sign-in (US1)
    val signInTitle: String
    val emailLabel: String
    val passwordLabel: String
    val signInButton: String
    val signingIn: String

    // Library (US5)
    val libraryEmptyTitle: String
    val libraryEmptyAction: String
    val libraryLoading: String
    val libraryRetry: String
    val publicationStatusDraft: String
    val publicationStatusPublished: String
    val audioCompletenessNone: String
    val audioCompletenessPartial: String
    val audioCompletenessPartialCount: String // "%d of %d recorded"
    val audioCompletenessComplete: String

    // Editor — metadata (US2)
    val uploadNewMatnHeading: String
    val uploadNewMatnLead: String
    val generalInformationHeading: String
    val matnTitleLabel: String
    val matnTitlePlaceholder: String
    val authorLabel: String
    val authorPlaceholder: String
    val descriptionLabel: String
    val descriptionPlaceholder: String
    val coverArtLabel: String
    val coverArtHint: String
    val coverArtRemove: String
    val coverUploadFailedError: String
    val coverInvalidFileError: String
    val proTipHeading: String
    val proTipBody: String
    val structureKindLabel: String
    val structureKindSimple: String
    val structureKindStructured: String
    val chaptersHeading: String
    val chapterTitleLabel: String
    val chapterStartsAtVerse: String
    val deleteChapter: String
    val addChapter: String

    // Editor — verses (US3)
    val verseListHeading: String
    val bulkImport: String
    val addVerse: String
    val addNextVerse: String
    val deleteVerse: String
    val moveVerseUp: String
    val moveVerseDown: String
    val dragToReorder: String
    val assignChapter: String
    val clearAllVerses: String
    val clearAllVersesConfirmTitle: String
    val clearAllVersesConfirmBody: String
    val clearAllVersesConfirmAction: String

    // Editor — actions and save state
    val saveAsDraft: String
    val publishMatn: String
    val unpublishMatn: String
    val checkForProblems: String
    val saveStateIdle: String
    val saveStateAutosaving: String
    val saveStateSavedAt: String // "Saved %s"
    val saveStateFailed: String
    val publishedEditingBanner: String

    // Publish / unpublish confirmation (US4)
    val publishConfirmTitle: String
    val publishConfirmBody: String
    val publishConfirmVerseCount: String // "%d verses"
    val publishConfirmAction: String
    val unpublishConfirmTitle: String
    val unpublishConfirmBody: String
    val unpublishConfirmAction: String
    val cancelAction: String

    // Validation panel (US4)
    val validationBlockingHeading: String
    val validationDeferredHeading: String

    // Import preview (US6)
    val importPreviewTitle: String
    val importPreviewLineCount: String // "%d lines"
    val importPreviewProblemLine: String // "Line %d could not be read"
    val importCancel: String
    val importConfirm: String
    val importInvalidEncodingError: String

    // RemoteError messages (T028a, contracts/teacher-ui-contract.md §6). No screen maps a
    // RemoteError itself — every failure display goes through messageFor/actionFor below.
    val errorNetworkMessage: String
    val errorNetworkAction: String
    val errorUnauthorizedMessage: String
    val errorUnauthorizedAction: String
    val errorForbiddenMessage: String
    val errorConflictMessage: String
    val errorConflictAction: String
    val errorQuotaExceededMessage: String
    val errorServerMessage: String
    val errorServerAction: String
    val errorRejectedMessage: String
    val errorDecodeMessage: String

    // Verse audio slot (US1, contracts/teacher-ui-contract.md §1)
    val addRecording: String
    val replaceRecording: String
    val removeRecording: String
    val playRecording: String
    val uploadingAudio: String
    val audioProfileMismatch: String // "this matn's audio is %s; this file is %s"
    val audioTooLarge: String
    val audioWrongFormat: String
    val audioUnreadable: String
    val storageFull: String

    // Split screen (US2, contracts/teacher-ui-contract.md §2)
    val splitFromRecording: String
    val pickRecording: String
    val replaceSourceRecording: String
    val scopeFirstVerse: String
    val scopeLastVerse: String
    val rangeStart: String
    val rangeEnd: String
    val nudgeEarlier: String
    val nudgeLater: String
    val splitAndUpload: String
    /** The empty state's up-front statement of accepted format and limits (FR-004). Deliberately
     * separate from [sourceTooLong]/[sourceTooLarge], which are *rejection* messages — showing
     * those before the teacher has picked anything reads as a failure that has not happened. */
    val splitSourceHint: String
    val splitLoadingSource: String
    val splitUploading: String
    val auditionRange: String
    val stopAudition: String
    val playSource: String
    val pauseSource: String
    val scrubHintIdle: String
    val scrubHintArmed: String // "Dragging sets verse %s %s"
    val splitRangedCount: String // "%d of %d verses ranged"
    val splitProblemsMore: String // "+%d more"
    val sourceTooLong: String
    val sourceTooLarge: String
    val splitProblemMissingRange: String // "verse %s has no range"
    val splitProblemInvertedRange: String // "verse %s's range ends before it starts"
    val splitProblemTooShort: String // "verse %s's range is shorter than 300 ms"
    val splitProblemOutOfBounds: String // "verse %s's range is outside the recording (%s)"
    val splitProblemOverlap: String // "verses %s and %s overlap"
    val splitProblemRangeOutOfScope: String // "verse %s is outside the current scope"
    val splitProblemUncoveredStretch: String // "no verse covers %s to %s"

    // Preview transport (US3, contracts/teacher-ui-contract.md §3)
    val previewMatn: String
    val previewPlaying: String // "Playing verse %s"
    val previewPaused: String // "Paused at verse %s"
    val previewMissingAudio: String // "Verse %s has no recording"
    val previewBuffering: String

    // Validation messages (T073, contracts/validation-contract.md §4). Name the verse number or
    // chapter title — the teacher never sees a UUID.
    val validationInvalidId: String // "Internal problem with %s — report this"
    val validationDuplicateId: String
    val validationDuplicateDisplayNumber: String // "Two verses share the number %s"
    val validationDuplicateChapterOrder: String // "Chapters %s are at the same position"
    val validationMissingAudio: String // "Verse %s has no recording yet"
    val validationDuplicateAudioRef: String // "Verses %s point at the same recording"
    val validationOrphanChapterRef: String // "Verse %s belongs to a chapter that no longer exists"
    val validationEmptyMatn: String
    val validationDocumentTooLarge: String // "This matn is too large to publish — %s of %s allowed"
}

/**
 * Maps a [RemoteError] to its teacher-facing message. No raw HTTP status or exception text ever
 * reaches the teacher (FR-005) — every screen showing a failure uses this, never the error type
 * directly.
 */
fun TeacherStrings.messageFor(error: RemoteError): String = when (error) {
    RemoteError.Network -> errorNetworkMessage
    RemoteError.Unauthorized -> errorUnauthorizedMessage
    RemoteError.Forbidden -> errorForbiddenMessage
    RemoteError.Conflict -> errorConflictMessage
    RemoteError.QuotaExceeded -> errorQuotaExceededMessage
    RemoteError.Server -> errorServerMessage
    RemoteError.Rejected -> errorRejectedMessage
    RemoteError.Decode -> errorDecodeMessage
}

/** `null` when the error offers no action (Forbidden, QuotaExceeded, Rejected, Decode) — retrying
 * an upload the server has already refused on its own terms would just fail identically. */
fun TeacherStrings.actionFor(error: RemoteError): String? = when (error) {
    RemoteError.Network -> errorNetworkAction
    RemoteError.Unauthorized -> errorUnauthorizedAction
    RemoteError.Conflict -> errorConflictAction
    RemoteError.Server -> errorServerAction
    RemoteError.Forbidden, RemoteError.QuotaExceeded, RemoteError.Rejected, RemoteError.Decode -> null
}

/**
 * Maps a [ContentIntegrityError] to its teacher-facing message, resolving ids to what the teacher
 * sees — the verse's [com.giraffe.matn.domain.catalog.DraftVerse.displayNumber] or the chapter's
 * title — from [draft]. Never a UUID (FR-028).
 */
fun TeacherStrings.messageFor(error: ContentIntegrityError, draft: MatnDraft): String = when (error) {
    is ContentIntegrityError.InvalidId -> validationInvalidId.replace("%s", error.detail)
    is ContentIntegrityError.DuplicateId -> validationDuplicateId
    is ContentIntegrityError.DuplicateDisplayNumber ->
        validationDuplicateDisplayNumber.replace("%s", error.number.toString())
    is ContentIntegrityError.DuplicateChapterOrder -> {
        val titles = draft.chapters.filter { it.order == error.order }.joinToString(", ") { it.title }
        validationDuplicateChapterOrder.replace("%s", titles)
    }
    is ContentIntegrityError.MissingAudio -> {
        val number = draft.verses.find { it.id == error.verseId }?.displayNumber
        validationMissingAudio.replace("%s", number?.toString().orEmpty())
    }
    is ContentIntegrityError.DuplicateAudioRef -> {
        val numbers = draft.verses.filter { it.audio?.fileRef == error.fileRef }
            .joinToString(", ") { it.displayNumber.toString() }
        validationDuplicateAudioRef.replace("%s", numbers)
    }
    is ContentIntegrityError.OrphanChapterRef -> {
        val number = draft.verses.find { it.id == error.verseId }?.displayNumber
        validationOrphanChapterRef.replace("%s", number?.toString().orEmpty())
    }
    is ContentIntegrityError.StructureMismatch -> error.detail
    is ContentIntegrityError.EmptyMatn -> validationEmptyMatn
    is ContentIntegrityError.DocumentTooLarge ->
        validationDocumentTooLarge.replaceFirst("%s", formatBytes(error.bytes)).replaceFirst("%s", formatBytes(error.limitBytes))
    is ContentIntegrityError.Aggregate -> error.problems.joinToString("; ") { messageFor(it, draft) }
}

/** Resolves an audio-attach failure to its teacher-facing message (`contracts/teacher-ui-contract.md`
 * §1, T052). `RemoteError.QuotaExceeded` reads as a storage problem specifically for audio, not the
 * generic quota message, per FR-040. */
fun TeacherStrings.messageFor(error: AppError): String = when (error) {
    is AudioAttachError.WrongFormat -> audioWrongFormat
    is AudioAttachError.TooLarge -> audioTooLarge
    is AudioAttachError.SourceTooLarge -> sourceTooLarge
    is AudioAttachError.SourceTooLong -> sourceTooLong
    is AudioAttachError.ProfileMismatch ->
        audioProfileMismatch.replaceFirst("%s", "${error.existing.sampleRate} Hz, ${error.existing.channels}ch")
            .replaceFirst("%s", "${error.incoming.sampleRate} Hz, ${error.incoming.channels}ch")
    RemoteError.QuotaExceeded -> storageFull
    is RemoteError -> messageFor(error)
    is AppError.Storage -> error.message
    AppError.NotFound -> audioUnreadable
    else -> audioUnreadable
}

/** Resolves a [com.giraffe.matn.domain.audio.SplitProblem] to its teacher-facing message, naming
 * the verse(s) it's about by [displayNumberOf] rather than a raw UUID (`split-contract.md` §2). */
fun TeacherStrings.messageFor(problem: com.giraffe.matn.domain.audio.SplitProblem, displayNumberOf: (String) -> Int?): String = when (problem) {
    is com.giraffe.matn.domain.audio.SplitProblem.MissingRange -> splitProblemMissingRange.replace("%s", displayNumberOf(problem.verseId)?.toString().orEmpty())
    is com.giraffe.matn.domain.audio.SplitProblem.InvertedRange -> splitProblemInvertedRange.replace("%s", displayNumberOf(problem.verseId)?.toString().orEmpty())
    is com.giraffe.matn.domain.audio.SplitProblem.TooShort -> splitProblemTooShort.replace("%s", displayNumberOf(problem.verseId)?.toString().orEmpty())
    is com.giraffe.matn.domain.audio.SplitProblem.OutOfBounds ->
        splitProblemOutOfBounds.replaceFirst("%s", displayNumberOf(problem.verseId)?.toString().orEmpty())
            .replaceFirst("%s", "${problem.sourceDurationMs} ms")
    is com.giraffe.matn.domain.audio.SplitProblem.Overlap ->
        splitProblemOverlap.replaceFirst("%s", displayNumberOf(problem.verseIdA)?.toString().orEmpty())
            .replaceFirst("%s", displayNumberOf(problem.verseIdB)?.toString().orEmpty())
    is com.giraffe.matn.domain.audio.SplitProblem.RangeOutOfScope -> splitProblemRangeOutOfScope.replace("%s", displayNumberOf(problem.verseId)?.toString().orEmpty())
    is com.giraffe.matn.domain.audio.SplitProblem.UncoveredStretch ->
        splitProblemUncoveredStretch.replaceFirst("%s", "${problem.startMs} ms").replaceFirst("%s", "${problem.endMs} ms")
}

enum class TeacherLanguage {
    ARABIC,
    ENGLISH;

    val layoutDirection: LayoutDirection
        get() = if (this == ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
}

val LocalTeacherStrings = staticCompositionLocalOf<TeacherStrings> { ArabicStrings }
