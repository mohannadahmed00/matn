package com.giraffe.matn.presentation.common

import matn.shared.generated.resources.Res
import matn.shared.generated.resources.a11y_close
import matn.shared.generated.resources.a11y_retry
import matn.shared.generated.resources.back
import matn.shared.generated.resources.content_action_cancel
import matn.shared.generated.resources.content_action_install
import matn.shared.generated.resources.content_action_remove
import matn.shared.generated.resources.font_size
import matn.shared.generated.resources.goals_open
import matn.shared.generated.resources.mark_chapter_memorized
import matn.shared.generated.resources.nav_library
import matn.shared.generated.resources.nav_saved
import matn.shared.generated.resources.nav_settings
import matn.shared.generated.resources.note_editor_delete
import matn.shared.generated.resources.note_editor_save
import matn.shared.generated.resources.onboarding_continue
import matn.shared.generated.resources.onboarding_skip
import matn.shared.generated.resources.player_next
import matn.shared.generated.resources.player_pause
import matn.shared.generated.resources.player_play
import matn.shared.generated.resources.player_previous
import matn.shared.generated.resources.player_speed
import matn.shared.generated.resources.player_stop
import matn.shared.generated.resources.repetition_setup_open
import matn.shared.generated.resources.search_clear
import matn.shared.generated.resources.search_open
import matn.shared.generated.resources.settings_remove_all
import matn.shared.generated.resources.toggle_bookmark
import matn.shared.generated.resources.toggle_memorized
import matn.shared.generated.resources.toggle_note
import matn.shared.generated.resources.unmark_chapter_memorized
import org.jetbrains.compose.resources.StringResource

/**
 * T055 (US2, accessibility-contract.md §2.1) — every icon-only control's accessible name resolves
 * from here; no inline literal, no `null`. Transcribed exactly from the contract's table.
 *
 * §2.1a: [TOGGLE_BOOKMARK], [TOGGLE_NOTE], and [TOGGLE_MEMORIZED] are single toggle actions, not
 * ADD/REMOVE pairs — the control's *name* never changes under a screen reader; only its
 * `stateDescription` does (applied at each call site per contract §4).
 */
enum class A11yAction {
    PLAY, PAUSE, STOP, NEXT_VERSE, PREVIOUS_VERSE, SPEED, REPETITION_SETUP,
    TOGGLE_BOOKMARK, TOGGLE_NOTE, TOGGLE_MEMORIZED,
    NOTE_SAVE, NOTE_DELETE,
    MARK_CHAPTER_MEMORIZED, UNMARK_CHAPTER_MEMORIZED,
    INSTALL, INSTALL_CANCEL, REMOVE, REMOVE_ALL,
    SEARCH, SEARCH_CLEAR, BACK,
    TAB_LIBRARY, TAB_SAVED, TAB_SETTINGS, DAILY_GOAL,
    FONT_SIZE,
    CLOSE, RETRY, SKIP, CONTINUE,
}

/** Total over [A11yAction] — [A11yLabelCatalogTest] in `commonTest` enforces that totality. */
val A11yLabels: Map<A11yAction, StringResource> = mapOf(
    A11yAction.PLAY to Res.string.player_play,
    A11yAction.PAUSE to Res.string.player_pause,
    A11yAction.STOP to Res.string.player_stop,
    A11yAction.NEXT_VERSE to Res.string.player_next,
    A11yAction.PREVIOUS_VERSE to Res.string.player_previous,
    A11yAction.SPEED to Res.string.player_speed,
    A11yAction.REPETITION_SETUP to Res.string.repetition_setup_open,
    A11yAction.TOGGLE_BOOKMARK to Res.string.toggle_bookmark,
    A11yAction.TOGGLE_NOTE to Res.string.toggle_note,
    A11yAction.TOGGLE_MEMORIZED to Res.string.toggle_memorized,
    A11yAction.NOTE_SAVE to Res.string.note_editor_save,
    A11yAction.NOTE_DELETE to Res.string.note_editor_delete,
    A11yAction.MARK_CHAPTER_MEMORIZED to Res.string.mark_chapter_memorized,
    A11yAction.UNMARK_CHAPTER_MEMORIZED to Res.string.unmark_chapter_memorized,
    A11yAction.INSTALL to Res.string.content_action_install,
    A11yAction.INSTALL_CANCEL to Res.string.content_action_cancel,
    A11yAction.REMOVE to Res.string.content_action_remove,
    A11yAction.REMOVE_ALL to Res.string.settings_remove_all,
    A11yAction.SEARCH to Res.string.search_open,
    A11yAction.SEARCH_CLEAR to Res.string.search_clear,
    A11yAction.BACK to Res.string.back,
    A11yAction.TAB_LIBRARY to Res.string.nav_library,
    A11yAction.TAB_SAVED to Res.string.nav_saved,
    A11yAction.TAB_SETTINGS to Res.string.nav_settings,
    A11yAction.DAILY_GOAL to Res.string.goals_open,
    A11yAction.FONT_SIZE to Res.string.font_size,
    A11yAction.CLOSE to Res.string.a11y_close,
    A11yAction.RETRY to Res.string.a11y_retry,
    A11yAction.SKIP to Res.string.onboarding_skip,
    A11yAction.CONTINUE to Res.string.onboarding_continue,
)
