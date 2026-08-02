package com.giraffe.matn.data.delivery

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.postgrest.MATN_FULL_COLUMNS
import com.giraffe.matn.data.remote.postgrest.MatnRow
import com.giraffe.matn.data.remote.postgrest.PostgrestClient
import com.giraffe.matn.data.remote.postgrest.PostgrestFilter
import com.giraffe.matn.data.remote.postgrest.VerseRow
import com.giraffe.matn.data.remote.postgrest.toMatnRow
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.delivery.DeviceStorage
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.DeliveryPhase
import com.giraffe.matn.domain.model.DeliveryProgress
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.repository.AudioAssetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one content-acquisition mechanism, shared by all three student clients (FR-041, research D3).
 *
 * Replaces `PlayAssetDeliveryEngine` (androidMain), `OnDemandResourcesEngine` (iosMain) and
 * `DesktopContentDeliveryEngine` (jvmMain). Nothing about fetching over HTTP is platform-specific —
 * Ktor's CIO engine is published for JVM, Android and every iOS variant — so Principle IV puts it
 * here, once.
 *
 * Reads are **anonymous**: no bearer token, `apikey` only (FR-027, student-read-contract §1). The
 * server already permits exactly this via Phase 11's `matns_read` and `matn_content_published_read`
 * policies, so an unpublished matn is unreachable from a student build no matter what this class
 * asks for (FR-004).
 *
 * This class holds **no policy**: free-space and connectivity preconditions live in
 * `DownloadMatnUseCase`, and queue ordering lives in `DownloadedContentRepositoryImpl`.
 */
class RemoteContentDeliveryEngine(
    private val db: ContentDatabase,
    private val postgrest: PostgrestClient,
    private val storageClient: StorageRestClient,
    private val files: ContentFileStore,
    private val deviceStorage: DeviceStorage,
    private val nowMillis: () -> Long,
) : ContentDeliveryEngine {

    /** Live progress per matn, published as bytes land so the UI bar reflects real transfer. */
    private val progress: MutableMap<String, MutableStateFlow<DeliveryProgress>> = mutableMapOf()
    private val progressGuard = Mutex()

    /** Matns whose transfer has been asked to stop. Checked between verses so a cancel lands
     *  promptly without needing to tear down the HTTP call mid-body. */
    private val cancelled: MutableSet<String> = mutableSetOf()

    private suspend fun progressFlow(matnId: String): MutableStateFlow<DeliveryProgress> =
        progressGuard.withLock {
            progress.getOrPut(matnId) {
                MutableStateFlow(DeliveryProgress(0L, 0L, DeliveryPhase.PENDING))
            }
        }

    override fun observe(matnId: String): Flow<DeliveryProgress> {
        val existing = progress[matnId]
        if (existing != null) return existing.asStateFlow()
        return MutableStateFlow(DeliveryProgress(0L, 0L, DeliveryPhase.PENDING)).asStateFlow()
    }

    override suspend fun cancel(matnId: String) {
        progressGuard.withLock { cancelled.add(matnId) }
        files.deleteTree(ContentFileStore.stagingDir(matnId))
    }

    override suspend fun contentRootFor(matnId: String): String? {
        val relative = ContentFileStore.downloadDir(matnId)
        if (!files.exists(relative)) return null
        return "${deviceStorage.contentRootPath()}/$relative"
    }

    override suspend fun isDownloaded(matnId: String): Boolean =
        files.exists(ContentFileStore.downloadDir(matnId))

    /**
     * Deletes the matn's **files** and reports the bytes reclaimed. Deleting its database rows is
     * deliberately *not* done here: the engine is the transport and filesystem edge, and the
     * repository owns local state (delivery-contract.md §2, "the engine holds no policy"). Keeping
     * the split honest is also what lets `FakeContentDeliveryEngine` stand in for this class without
     * needing a database handle.
     */
    override suspend fun remove(matnId: String): Resource<RemovalOutcome> {
        val relative = ContentFileStore.downloadDir(matnId)
        val bytes = files.sizeOfTree(relative)
        files.deleteTree(relative)
        files.deleteTree(ContentFileStore.stagingDir(matnId))
        return Resource.Success(RemovalOutcome.Reclaimed(bytes))
    }

    /**
     * The nine-step sequence in delivery-contract.md §4. Ordering is load-bearing: **files first,
     * database last**. A crash between them leaves an orphan directory with no rows, which reads as
     * not-downloaded and is harmless; the reverse would leave rows claiming playable audio that is
     * not there (research D7).
     */
    override suspend fun download(matnId: String): Resource<Unit> {
        progressGuard.withLock { cancelled.remove(matnId) }
        val flow = progressFlow(matnId)
        val staging = ContentFileStore.stagingDir(matnId)

        // Start from a clean slate — a previous aborted attempt must never contribute bytes.
        files.deleteTree(staging)

        try {
            // Step 5: the full row, including the verses/chapters jsonb.
            val row = when (val fetched = fetchMatnRow(matnId)) {
                is Resource.Success -> fetched.data
                is Resource.Failure -> return abort(matnId, staging, fetched.error)
            }

            val versesWithAudio = row.verses.filter { it.audio != null }
            val totalBytes = versesWithAudio.sumOf { it.audio?.sizeBytes ?: 0L }
            flow.value = DeliveryProgress(0L, totalBytes, DeliveryPhase.TRANSFERRING)

            // Step 6: one self-contained file per verse (FR-024 — never an offset into a shared
            // recording). A 404 or a null `audio` means "published without a recitation for this
            // verse": readable, marked, and explicitly NOT a download failure (FR-023).
            var transferred = 0L
            val playable = mutableSetOf<String>()
            for (verse in versesWithAudio) {
                if (isCancelled(matnId)) {
                    return abort(matnId, staging, DeliveryError.DeliveryFailed(DeliveryFailure.Cancelled))
                }
                val audio = verse.audio ?: continue
                when (val bytes = storageClient.download(audio.fileRef)) {
                    is Resource.Success -> {
                        files.writeFile(
                            ContentFileStore.stagedAudioPath(matnId, audio.fileRef),
                            bytes.data,
                        )
                        playable.add(verse.id)
                        transferred += audio.sizeBytes.takeIf { it > 0 } ?: bytes.data.size.toLong()
                        flow.value = DeliveryProgress(transferred, totalBytes, DeliveryPhase.TRANSFERRING)
                    }
                    is Resource.Failure -> {
                        // FR-023: a missing object for one verse must not fail the whole matn.
                        if (bytes.error is RemoteError.Server) continue
                        return abort(matnId, staging, bytes.error)
                    }
                }
            }

            if (isCancelled(matnId)) {
                return abort(matnId, staging, DeliveryError.DeliveryFailed(DeliveryFailure.Cancelled))
            }

            // Step 7 then 8. Never the other way round.
            files.moveTree(staging, ContentFileStore.downloadDir(matnId))
            commit(row, playable)

            flow.value = DeliveryProgress(totalBytes, totalBytes, DeliveryPhase.TRANSFERRING)
            return Resource.Success(Unit)
        } catch (c: CancellationException) {
            files.deleteTree(staging)
            throw c
        } catch (t: Throwable) {
            return abort(matnId, staging, AppError.Storage(t.message ?: "download failed"))
        }
    }

    // ------------------------------------------------------------------ internals

    private suspend fun isCancelled(matnId: String): Boolean =
        progressGuard.withLock { matnId in cancelled }

    /**
     * Every abort path funnels through here, so FR-017's two obligations — resolve to
     * not-downloaded and **release the space consumed** — can never be forgotten on one branch.
     */
    private suspend fun abort(matnId: String, staging: String, error: AppError): Resource<Unit> {
        files.deleteTree(staging)
        progressFlow(matnId).value = DeliveryProgress(0L, 0L, DeliveryPhase.PENDING)
        return Resource.Failure(error)
    }

    private suspend fun fetchMatnRow(matnId: String): Resource<MatnRow> {
        val result = postgrest.select(
            table = "matns",
            columns = MATN_FULL_COLUMNS,
            filters = listOf(PostgrestFilter("id", "eq.$matnId")),
        )
        return when (result) {
            is Resource.Failure -> Resource.Failure(
                DeliveryError.DeliveryFailed(mapRemote(result.error)),
            )
            is Resource.Success -> {
                // An empty array means the matn stopped being published between browsing and
                // downloading — RLS simply stops returning the row (spec edge case).
                val first = result.data.firstOrNull()
                    ?: return Resource.Failure(
                        DeliveryError.DeliveryFailed(DeliveryFailure.SourceUnavailable),
                    )
                Resource.Success(first.toMatnRow())
            }
        }
    }

    private fun mapRemote(error: AppError): DeliveryFailure = when (error) {
        is RemoteError.Network -> DeliveryFailure.NoConnectivity
        is RemoteError -> DeliveryFailure.Remote(error)
        else -> DeliveryFailure.SourceUnavailable
    }

    /**
     * Step 8: one transaction, after every byte is already on disk. [playable] holds the verses
     * whose audio actually landed — a verse absent from it is readable and marked as having no
     * recitation, which is the whole of FR-023.
     *
     * Audio rows are written under [AudioAssetRepository.DEFAULT_RECITER], **not** the published
     * `default_reciter_id`. On the student device `reciter_id` exists to tell competing recitations
     * of the same verse apart, and a published matn has exactly one: `matns.default_reciter_id` is a
     * scalar and each verse carries a single `audio` object. That id is also unreachable from the
     * student side by any other route — the overview projection does not fetch it and no local table
     * stores it — so writing it here made every read miss. `BuildPlaybackQueueUseCase` and
     * `getAudioForVerse` both look up the constant, which is the invariant the rest of the student
     * app has always assumed.
     */
    private fun commit(row: MatnRow, playable: Set<String>) {
        db.transaction {
            db.contentQueries.upsertMatn(
                row.id,
                row.title,
                row.author,
                row.description,
                row.coverImageRef,
                row.structureKind,
            )
            row.chapters.forEach { chapter ->
                db.contentQueries.upsertChapter(chapter.id, row.id, chapter.title, chapter.order.toLong())
            }
            row.verses.forEach { verse: VerseRow ->
                db.contentQueries.upsertVerse(
                    verse.id,
                    row.id,
                    verse.chapterId,
                    verse.displayNumber.toLong(),
                    verse.arabicText,
                    verse.durationMs,
                )
                val audio = verse.audio
                if (audio != null && verse.id in playable) {
                    db.contentQueries.upsertAudioAsset(
                        audio.id,
                        verse.id,
                        AudioAssetRepository.DEFAULT_RECITER,
                        audio.fileRef.substringAfterLast('/'),
                        audio.durationMs,
                    )
                }
            }
            db.contentQueries.insertDownloadedMatn(row.id, row.revision ?: 0L, nowMillis())
        }
    }
}
