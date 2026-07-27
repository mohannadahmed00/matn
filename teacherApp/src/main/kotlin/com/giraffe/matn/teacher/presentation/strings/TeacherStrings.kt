package com.giraffe.matn.teacher.presentation.strings

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.LayoutDirection
import com.giraffe.matn.domain.error.RemoteError

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
    val proTipHeading: String
    val proTipBody: String
    val structureKindLabel: String
    val structureKindSimple: String
    val structureKindStructured: String
    val chaptersHeading: String
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
    val errorDecodeMessage: String
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
    RemoteError.Decode -> errorDecodeMessage
}

/** `null` when the error offers no action (Forbidden, QuotaExceeded, Decode). */
fun TeacherStrings.actionFor(error: RemoteError): String? = when (error) {
    RemoteError.Network -> errorNetworkAction
    RemoteError.Unauthorized -> errorUnauthorizedAction
    RemoteError.Conflict -> errorConflictAction
    RemoteError.Server -> errorServerAction
    RemoteError.Forbidden, RemoteError.QuotaExceeded, RemoteError.Decode -> null
}

enum class TeacherLanguage {
    ARABIC,
    ENGLISH;

    val layoutDirection: LayoutDirection
        get() = if (this == ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
}

val LocalTeacherStrings = staticCompositionLocalOf<TeacherStrings> { ArabicStrings }
