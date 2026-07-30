package com.giraffe.matn.catalog

import com.giraffe.matn.domain.catalog.DraftAutosaveScheduler
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraftAutosaveSchedulerTest {

    private fun draft(state: PublicationState = PublicationState.DRAFT, title: String = "T") = MatnDraft(
        id = "m1",
        title = title,
        author = "A",
        description = "",
        coverImageRef = null,
        structureKind = StructureKind.SIMPLE,
        defaultReciterId = "r1",
        chapters = emptyList(),
        verses = emptyList(),
        publicationState = state,
        createdAt = 0L,
        updatedAt = 0L,
        remoteRevision = null,
    )

    @Test
    fun `saves after 5 seconds idle`() = runTest {
        val saved = mutableListOf<MatnDraft>()
        val scheduler = DraftAutosaveScheduler(scope = this, nowMillis = { testScheduler.currentTime }) { saved.add(it) }

        scheduler.notifyChanged(draft())
        advanceTimeBy(5_001)

        assertEquals(1, saved.size)
    }

    @Test
    fun `saves at the 60 second ceiling under continuous editing`() = runTest {
        val saved = mutableListOf<MatnDraft>()
        val scheduler = DraftAutosaveScheduler(scope = this, nowMillis = { testScheduler.currentTime }) { saved.add(it) }

        // Every 4s (< the 5s idle window) for 68 virtual seconds of continuous, never-idle editing.
        // If only the idle debounce existed, this would never save until typing "stops" — checking
        // immediately after the loop (no extra advance) proves the ceiling fired independently.
        repeat(17) {
            scheduler.notifyChanged(draft(title = "T$it"))
            advanceTimeBy(4_000)
        }

        assertTrue(saved.isNotEmpty(), "expected the 60s ceiling to force a save during continuous editing")
    }

    @Test
    fun `coalesces concurrent triggers into one save`() = runTest {
        val saved = mutableListOf<MatnDraft>()
        val scheduler = DraftAutosaveScheduler(scope = this, nowMillis = { testScheduler.currentTime }) { saved.add(it) }

        repeat(5) {
            scheduler.notifyChanged(draft(title = "T$it"))
            advanceTimeBy(500)
        }
        advanceTimeBy(5_001)

        assertEquals(1, saved.size)
        assertEquals("T4", saved.single().title)
    }

    @Test
    fun `never fires for a published matn`() = runTest {
        val saved = mutableListOf<MatnDraft>()
        val scheduler = DraftAutosaveScheduler(scope = this, nowMillis = { testScheduler.currentTime }) { saved.add(it) }

        scheduler.notifyChanged(draft(state = PublicationState.PUBLISHED))
        advanceTimeBy(120_000)

        assertTrue(saved.isEmpty())
    }
}
