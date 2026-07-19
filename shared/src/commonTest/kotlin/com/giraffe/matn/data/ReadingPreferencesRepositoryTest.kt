package com.giraffe.matn.data

import com.giraffe.matn.data.repository.ReadingPreferencesRepositoryImpl
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.model.ReadingFontSize
import com.giraffe.matn.newTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingPreferencesRepositoryTest {

    @Test
    fun `default is MEDIUM when unset`() = runTest {
        val repo = ReadingPreferencesRepositoryImpl(newTestDatabase())
        assertEquals(ReadingFontSize.MEDIUM, repo.observeFontSize().first())
    }

    @Test
    fun `setFontSize then observe emits the new size`() = runTest {
        val repo = ReadingPreferencesRepositoryImpl(newTestDatabase())
        repo.setFontSize(ReadingFontSize.LARGE)
        assertEquals(ReadingFontSize.LARGE, repo.observeFontSize().first())
    }

    @Test
    fun `fresh repository over the same DB reads the persisted size`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        // Persist via one repository instance.
        ReadingPreferencesRepositoryImpl(db).setFontSize(ReadingFontSize.XLARGE)
        // A brand-new instance over the same driver reads the persisted value back (SC-007).
        val freshRepo = ReadingPreferencesRepositoryImpl(db)
        assertEquals(ReadingFontSize.XLARGE, freshRepo.observeFontSize().first())
    }

    @Test
    fun `unrecognized stored value falls back to MEDIUM`() = runTest {
        val db: ContentDatabase = newTestDatabase()
        // Write a bogus value directly to the settings table, bypassing the repository enum mapping.
        db.contentQueries.upsertSetting("reading_font_size", "BOGUS_SIZE")
        val repo = ReadingPreferencesRepositoryImpl(db)
        assertEquals(ReadingFontSize.MEDIUM, repo.observeFontSize().first())
    }
}