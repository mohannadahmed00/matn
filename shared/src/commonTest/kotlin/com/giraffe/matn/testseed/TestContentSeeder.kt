package com.giraffe.matn.testseed

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.catalog.ContentIntegrityValidator
import com.giraffe.matn.domain.error.ContentIntegrityError
import com.giraffe.matn.domain.model.Matn
import com.giraffe.matn.domain.model.StructureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * **Test-only** content fixture, recovered from the production `data/seed/` package that Phase 13
 * deleted (FR-038: nothing ships in the binary any more).
 *
 * The production seeding path is genuinely gone — the library now fills only from what the teacher
 * published. But the 20-odd repository, search, progress and playback tests written across Phases
 * 1–8 all needed *some* way to put matn/chapter/verse/audio rows into a test database, and the seed
 * loader was doubling as that builder. Reinstating it here keeps those tests meaningful without
 * putting a single byte of content back into a shipped artifact.
 *
 * The only behavioural change from the original: it no longer writes a `content_pack` row, because
 * that table is gone (research D6).
 */
class TestContentSeeder(private val db: ContentDatabase) : ContentSeedLoader {

    override suspend fun load(payload: SeedMatn): Resource<Matn> {
        val problems = validate(payload)
        if (problems.isNotEmpty()) {
            return Resource.Failure(ContentIntegrityError.Aggregate(problems))
        }
        return withContext(Dispatchers.Default) {
            try {
                db.transaction {
                    val q = db.contentQueries
                    q.deleteAudioByMatn(payload.id)
                    q.deleteVersesByMatn(payload.id)
                    q.deleteChaptersByMatn(payload.id)

                    q.upsertMatn(
                        id = payload.id,
                        title = payload.title,
                        author = payload.author,
                        description = payload.description,
                        cover_image_ref = payload.coverImageRef,
                        structure_kind = payload.structureKind,
                    )


                    payload.chapters.forEach { chapter ->
                        q.upsertChapter(
                            id = chapter.id,
                            matn_id = payload.id,
                            title = chapter.title,
                            display_order = chapter.order.toLong(),
                        )
                    }

                    payload.verses.forEach { verse ->
                        q.upsertVerse(
                            id = verse.id,
                            matn_id = payload.id,
                            chapter_id = verse.chapterId,
                            display_number = verse.displayNumber.toLong(),
                            arabic_text = verse.arabicText,
                            duration_ms = verse.durationMs,
                        )
                        val audio = verse.audio
                        if (audio != null) {
                            q.upsertAudioAsset(
                                id = audio.id,
                                verse_id = verse.id,
                                reciter_id = payload.defaultReciterId,
                                file_ref = audio.fileRef,
                                duration_ms = audio.durationMs,
                            )
                        }
                    }
                }
                Resource.Success(payload.toMatnDomain())
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Resource.Failure(AppError.Storage(t.message ?: "Failed to load matn ${payload.id}"))
            }
        }
    }

    private fun validate(payload: SeedMatn): List<ContentIntegrityError> {
        val draft = payload.toDraft()
        val report = ContentIntegrityValidator.validate(draft)
        // The loader treats every problem as blocking, exactly as before the extraction —
        // the teacher tool's blocking/deferred split (ValidationReport) is that caller's policy,
        // not the validator's. EmptyMatn/DocumentTooLarge (V8/V9) are the teacher's publish-time
        // policy (FR-030, research D2 guard); the bundled-content loader never enforced them and
        // keeps not doing so, so ingesting seed content is unchanged by the extraction.
        return (report.blocking + report.deferred).filterNot {
            it is ContentIntegrityError.EmptyMatn || it is ContentIntegrityError.DocumentTooLarge
        }
    }

    private fun SeedMatn.toMatnDomain(): Matn = Matn(
        id = id,
        title = title,
        author = author,
        description = description,
        coverImageRef = coverImageRef,
        structureKind = StructureKind.valueOf(structureKind),
    )
}