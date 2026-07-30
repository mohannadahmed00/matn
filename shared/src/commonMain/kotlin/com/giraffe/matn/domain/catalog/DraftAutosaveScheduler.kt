package com.giraffe.matn.domain.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Autosave policy (research D12, FR-031a-c): 5 s idle debounce, 60 s hard ceiling since the first
 * unsaved change, one save in flight at a time with the latest state saved on completion. A
 * **no-op for a published matn** (FR-031b) — published edits are explicit-save only.
 */
class DraftAutosaveScheduler(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
    private val save: suspend (MatnDraft) -> Unit,
) {
    private var debounceJob: Job? = null
    private var pendingDraft: MatnDraft? = null
    private var pendingSinceMillis: Long? = null
    private val saveMutex = Mutex()

    fun notifyChanged(draft: MatnDraft) {
        if (draft.publicationState == PublicationState.PUBLISHED) return

        pendingDraft = draft
        val now = nowMillis()
        val since = pendingSinceMillis ?: now.also { pendingSinceMillis = it }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            val ceilingDelay = (since + CEILING_MS - now).coerceAtLeast(0)
            delay(minOf(IDLE_DEBOUNCE_MS, ceilingDelay))
            triggerSave()
        }
    }

    private suspend fun triggerSave() {
        saveMutex.withLock {
            while (true) {
                val draft = pendingDraft ?: return
                pendingDraft = null
                pendingSinceMillis = null
                save(draft)
            }
        }
    }

    companion object {
        private const val IDLE_DEBOUNCE_MS = 5_000L
        private const val CEILING_MS = 60_000L
    }
}
