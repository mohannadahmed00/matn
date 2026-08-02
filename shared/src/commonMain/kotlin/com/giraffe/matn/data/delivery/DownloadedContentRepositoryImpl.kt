package com.giraffe.matn.data.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.delivery.DeviceStorage
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StorageUsage
import com.giraffe.matn.domain.repository.DownloadedContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SQLDelight + engine + [DeviceStorage] composition over [DownloadedContentRepository]
 * (delivery-contract.md §3).
 *
 * Owns the **one-at-a-time download queue** (Clarification 4, research D10). Queue membership is
 * in-memory only: a killed process resolves every queued and in-flight matn to not-downloaded,
 * which is already what FR-017 prescribes, and persisting it would resurrect transfers the student
 * cannot see pending — which FR-022 forbids trusting over what is on disk.
 *
 * The pump runs on [scope], an application-scoped `SupervisorJob` supplied by DI, so a transfer and
 * the queue behind it keep advancing while the app is backgrounded (FR-018, US2 scenario 6). A
 * ViewModel scope would die with the screen and silently restart the transfer on return.
 */
class DownloadedContentRepositoryImpl(
    private val db: ContentDatabase,
    private val engine: ContentDeliveryEngine,
    private val storage: DeviceStorage,
    private val files: ContentFileStore,
    private val scope: CoroutineScope,
) : DownloadedContentRepository {

    private val stateGuard = Mutex()

    /** Requested, not yet started, in request order (FR-015). */
    private val queue: ArrayDeque<String> = ArrayDeque()

    /** The single matn currently transferring, or null. */
    private var active: String? = null

    /** Whether the pump coroutine is alive; guards against starting a second one. */
    private var pumping: Boolean = false

    /** Last failure per matn, so `NotDownloaded` can carry an actionable reason (FR-017, FR-043). */
    private val failures: MutableMap<String, DeliveryFailure> = mutableMapOf()

    /**
     * Bumped on every queue/active/failure change. Availability flows combine on this, so a state
     * change re-derives from disk rather than from a cached snapshot (FR-022).
     */
    private val revision = MutableStateFlow(0L)

    private suspend fun bump() {
        revision.value = revision.value + 1
    }

    // ------------------------------------------------------------------ availability

    /**
     * The single source of truth for "what state is this matn in", asked fresh every time.
     * Order matters: in-memory queue/active state first (the filesystem cannot express "queued"),
     * then the filesystem (FR-022), then the remembered failure.
     */
    private suspend fun derive(matnId: String): ContentAvailability {
        val (isActive, isQueued, failure) = stateGuard.withLock {
            Triple(active == matnId, matnId in queue, failures[matnId])
        }
        if (isActive) {
            return ContentAvailability.Downloading(engine.observe(matnId).first())
        }
        if (isQueued) return ContentAvailability.Queued
        if (engine.isDownloaded(matnId)) {
            val root = ContentFileStore.downloadDir(matnId)
            return ContentAvailability.Downloaded(files.sizeOfTree(root))
        }
        // FR-045: a `downloaded_matn` row whose directory has vanished is not an error the student
        // caused — it reports as plain not-downloaded and the row is reaped.
        if (db.contentQueries.selectDownloadedMatn(matnId).executeAsOneOrNull() != null) {
            db.contentQueries.deleteDownloadedMatn(matnId)
        }
        return ContentAvailability.NotDownloaded(failure)
    }

    override fun observeAvailability(matnId: String): Flow<ContentAvailability> =
        combine(revision, engine.observe(matnId)) { _, _ -> matnId }
            .map { derive(it) }
            .catch { emit(ContentAvailability.NotDownloaded(null)) }
            .distinctUntilChanged()

    override fun observeLibraryAvailability(): Flow<Map<String, ContentAvailability>> = flow {
        emitAll(
            revision.map {
                db.contentQueries.selectAllCatalogOverviews().executeAsList()
                    .associate { row -> row.matn_id to derive(row.matn_id) }
            },
        )
    }.catch { emit(emptyMap()) }.distinctUntilChanged()

    override fun observeStorageUsage(): Flow<StorageUsage> = flow {
        emitAll(
            revision.map {
                val entries = mutableListOf<MatnStorageEntry>()
                var total = 0L
                db.contentQueries.selectAllCatalogOverviews().executeAsList().forEach { row ->
                    val availability = derive(row.matn_id)
                    if (availability is ContentAvailability.Downloaded) {
                        entries += MatnStorageEntry(
                            matnId = row.matn_id,
                            title = row.title,
                            bytes = availability.occupiedBytes,
                        )
                        total += availability.occupiedBytes
                    }
                }
                entries.sortByDescending { it.bytes }
                // FR-012 / Clarification 3: `covers/` is deliberately not measured. It lives outside
                // `downloads/`, so removal cannot reclaim it and counting it would break SC-009.
                StorageUsage(
                    entries = entries,
                    totalUsedBytes = total,
                    freeSpaceBytes = storage.freeSpaceBytes(),
                )
            },
        )
    }.catch { emit(StorageUsage(emptyList(), 0L, 0L)) }.distinctUntilChanged()

    override suspend fun contentRootFor(matnId: String): String? = engine.contentRootFor(matnId)

    // ------------------------------------------------------------------ queue

    override suspend fun download(matnId: String): Resource<Unit> {
        if (engine.isDownloaded(matnId)) return Resource.Success(Unit)
        val accepted = stateGuard.withLock {
            if (active == matnId || matnId in queue) return@withLock false // duplicate is a no-op
            failures.remove(matnId)
            queue.addLast(matnId)
            true
        }
        if (accepted) {
            bump()
            startPump()
        }
        return Resource.Success(Unit)
    }

    private suspend fun startPump() {
        val shouldStart = stateGuard.withLock {
            if (pumping) false else { pumping = true; true }
        }
        if (!shouldStart) return
        scope.launch { pump() }
    }

    /**
     * Drains the queue, one transfer at a time. Runs on the application scope so it outlives any
     * screen (FR-018).
     */
    private suspend fun pump() {
        while (true) {
            val next = stateGuard.withLock {
                val head = queue.removeFirstOrNull()
                if (head == null) {
                    pumping = false
                } else {
                    active = head
                }
                head
            } ?: return
            bump()

            val outcome = engine.download(next)

            stateGuard.withLock {
                active = null
                if (outcome is Resource.Failure) {
                    failures[next] = (outcome.error as? DeliveryError.DeliveryFailed)?.failure
                        ?: DeliveryFailure.SourceUnavailable
                }
            }
            bump()
        }
    }

    override suspend fun cancel(matnId: String) {
        val wasActive = stateGuard.withLock {
            queue.remove(matnId) // queued-but-not-started: nothing was ever transferred (FR-032)
            failures[matnId] = DeliveryFailure.Cancelled
            active == matnId
        }
        if (wasActive) engine.cancel(matnId)
        bump()
    }

    override suspend fun remove(matnId: String): Resource<RemovalOutcome> {
        // A removal racing an in-flight or queued download of the same matn WINS.
        val wasActive = stateGuard.withLock {
            queue.remove(matnId)
            failures.remove(matnId)
            active == matnId
        }
        if (wasActive) engine.cancel(matnId)
        val outcome = engine.remove(matnId)

        // Files first, then rows — the same ordering as the download commit, for the same reason:
        // rows without files claim playable audio that is not there, files without rows are simply
        // invisible and harmless (research D7).
        //
        // These deletes CANNOT reach a bookmark, note, memorized mark, practice record or saved
        // session: `5.sqm` dropped the `ON DELETE CASCADE` that used to connect them (research D5).
        // `RemovalPreservesUserDataTest` is the guard.
        db.transaction {
            db.contentQueries.deleteAudioByMatn(matnId)
            db.contentQueries.deleteVersesByMatn(matnId)
            db.contentQueries.deleteChaptersByMatn(matnId)
            db.contentQueries.deleteMatnById(matnId)
            db.contentQueries.deleteDownloadedMatn(matnId)
        }
        bump()
        return outcome
    }

    override suspend fun removeAll(): Resource<List<RemovalOutcome>> {
        val outcomes = mutableListOf<RemovalOutcome>()
        // FR-031: nothing is spared. Every catalogued matn is a removal target.
        db.contentQueries.selectAllCatalogOverviews().executeAsList().forEach { row ->
            if (derive(row.matn_id) is ContentAvailability.NotDownloaded) return@forEach
            val outcome = remove(row.matn_id)
            if (outcome is Resource.Success) outcomes += outcome.data
        }
        return Resource.Success(outcomes)
    }
}
