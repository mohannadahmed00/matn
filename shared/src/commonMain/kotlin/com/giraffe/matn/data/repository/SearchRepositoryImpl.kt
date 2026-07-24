package com.giraffe.matn.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.giraffe.matn.db.Chapter as ChapterRow
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.db.Matn as MatnRow
import com.giraffe.matn.db.Verse as VerseRow
import com.giraffe.matn.domain.model.AnnotatedVerseRef
import com.giraffe.matn.domain.model.SearchResult
import com.giraffe.matn.domain.repository.SearchRepository
import com.giraffe.matn.domain.search.ArabicNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-memory-filtered search over the reactive library corpus (research.md D1/D2;
 * contracts/search-contract.md). No FTS, no schema change — SQLDelight's reactive queries supply
 * the corpus, [ArabicNormalizer] does the matching, and results are assembled in the exact
 * deterministic order the contract fixes (FR-006).
 */
class SearchRepositoryImpl(private val db: ContentDatabase) : SearchRepository {

    override fun search(query: String): Flow<List<SearchResult>> {
        val matnFlow = db.contentQueries.selectAllMatn().asFlow().mapToList(Dispatchers.Default)
        val chapterFlow = db.contentQueries.selectAllChapters().asFlow().mapToList(Dispatchers.Default)
        val verseFlow = db.contentQueries.selectAllVerses().asFlow().mapToList(Dispatchers.Default)

        return combine(matnFlow, chapterFlow, verseFlow) { matnRows, chapterRows, verseRows ->
            assembleResults(query, matnRows, chapterRows, verseRows)
        }
    }

    private fun assembleResults(
        query: String,
        matnRows: List<MatnRow>,
        chapterRows: List<ChapterRow>,
        verseRows: List<VerseRow>,
    ): List<SearchResult> {
        val normalizedQuery = ArabicNormalizer.normalize(query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val chaptersByMatn = chapterRows.groupBy { it.matn_id }
        val versesByMatn = verseRows.groupBy { it.matn_id }
        val versesByChapter = verseRows.filter { it.chapter_id != null }.groupBy { it.chapter_id!! }

        val results = mutableListOf<SearchResult>()
        // matnRows arrives in `selectAllMatn`'s own order (title-sorted) — that IS library order
        // (FR-006); within a matn: title match, then chapters by display_order, then verses by
        // display_number.
        for (matn in matnRows) {
            if (ArabicNormalizer.normalize(matn.title).contains(normalizedQuery)) {
                results += SearchResult.MatnMatch(matnId = matn.id, matnTitle = matn.title)
            }

            val chaptersOfMatn = chaptersByMatn[matn.id].orEmpty().sortedBy { it.display_order }
            for (chapter in chaptersOfMatn) {
                if (ArabicNormalizer.normalize(chapter.title).contains(normalizedQuery)) {
                    val firstVerseId = versesByChapter[chapter.id]
                        ?.minByOrNull { it.display_number }
                        ?.id
                    results += SearchResult.ChapterMatch(
                        matnId = matn.id,
                        matnTitle = matn.title,
                        chapterId = chapter.id,
                        chapterTitle = chapter.title,
                        firstVerseId = firstVerseId,
                    )
                }
            }

            val versesOfMatn = versesByMatn[matn.id].orEmpty().sortedBy { it.display_number }
            for (verse in versesOfMatn) {
                val textMatches = ArabicNormalizer.normalize(verse.arabic_text).contains(normalizedQuery)
                val numberMatches = verse.display_number.toString().contains(normalizedQuery)
                if (textMatches || numberMatches) {
                    results += SearchResult.VerseMatch(
                        AnnotatedVerseRef(
                            matnId = matn.id,
                            matnTitle = matn.title,
                            verseId = verse.id,
                            verseNumber = verse.display_number.toInt(),
                            verseText = verse.arabic_text,
                        ),
                    )
                }
            }
        }
        return results
    }
}
