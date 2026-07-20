package com.giraffe.matn.domain.model

/**
 * A derived display label for the active drill configuration (data-model.md §1.4 / FR-008).
 * Never stored, never set by the student, and never an input to any decision — the planner reads
 * counters and range, not mode.
 */
enum class PlaybackMode { NORMAL, MEMORIZATION, A_B_LOOP }
