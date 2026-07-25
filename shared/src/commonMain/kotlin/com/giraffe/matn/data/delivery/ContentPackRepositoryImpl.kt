package com.giraffe.matn.data.delivery

import com.giraffe.matn.core.Resource
import com.giraffe.matn.db.ContentDatabase
import com.giraffe.matn.domain.delivery.ContentDeliveryEngine
import com.giraffe.matn.domain.delivery.DeviceStorage
import com.giraffe.matn.domain.model.ContentAvailability
import com.giraffe.matn.domain.error.DeliveryError
import com.giraffe.matn.domain.model.DeliveryFailure
import com.giraffe.matn.domain.model.MatnStorageEntry
import com.giraffe.matn.domain.model.RemovalOutcome
import com.giraffe.matn.domain.model.StorageUsage
import com.giraffe.matn.domain.repository.ContentPackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SQLDelight + engine + [DeviceStorage] composition over [ContentPackRepository]
 * (content-delivery-contract.md §3 + data-model §2.1). Owns the `matnId ↔ packId` translation so
 * no layer above ever sees a pack id (FR-032). Availability is computed per read from the engine
 * plus the `content_pack` row and is **never persisted** (research D5). Per-pack [Mutex]es
 * enforce the concurrency rules in contract §3: duplicate install is a no-op, duplicate remove is
 * a no-op returning `Reclaimed(0)`, and a remove racing an in-flight install of the same matn
 * **wins** (cancel first, then remove).
 */
@org.koin.core.annotation.Single(binds = [ContentPackRepository::class])
class ContentPackRepositoryImpl(
    private val db: ContentDatabase,
    private val engine: ContentDeliveryEngine,
    private val storage: DeviceStorage,
) : ContentPackRepository {

    /** Per-pack lock map; the same mutex is reused across install/cancel/remove for a pack. */
    private val packLocks: MutableMap<String, Mutex> = mutableMapOf()

    /**
     * Packs the repo asked the engine to install but the engine has not yet reported as Installed.
     * `deriveAvailability` checks this set so an in-flight install renders as `Installing` — Play's
     * `AssetPackStatus` route would otherwise race `isInstalled` (which reports false until the
     * transfer fully completes). Cleared on cancel/remove/eviction.
     */
    private val activeInstalls: MutableSet<String> = mutableSetOf()

    private fun lockFor(packId: String): Mutex =
        packLocks.getOrPut(packId) { Mutex() }

    // ------------------------------------------------------------------ catalog reads

    private data class CatalogRow(
        val matnId: String,
        val packId: String,
        val declaredSizeBytes: Long,
        val isStarter: Boolean,
        val title: String,
    )

    private fun loadCatalogRow(matnId: String): CatalogRow? {
        val packRow = db.contentQueries.selectContentPackByMatn(matnId).executeAsOneOrNull()
            ?: return null
        val matnRow = db.contentQueries.selectMatnById(matnId).executeAsOneOrNull()
            ?: return null
        return CatalogRow(
            matnId = matnId,
            packId = packRow.pack_id,
            declaredSizeBytes = packRow.declared_size_bytes,
            isStarter = packRow.is_starter != 0L,
            title = matnRow.title,
        )
    }

    private fun loadAllCatalog(): List<CatalogRow> {
        val packRows = db.contentQueries.selectAllContentPacks().executeAsList()
        return packRows.mapNotNull { packRow ->
            val matn = db.contentQueries.selectMatnById(packRow.matn_id).executeAsOneOrNull()
                ?: return@mapNotNull null
            CatalogRow(
                matnId = packRow.matn_id,
                packId = packRow.pack_id,
                declaredSizeBytes = packRow.declared_size_bytes,
                isStarter = packRow.is_starter != 0L,
                title = matn.title,
            )
        }
    }

    // ---------------------------------------------------------- availability derivation

    /**
     * Builds the current [ContentAvailability] for [row] by reading through to the engine (D5).
     * The starter matn is always `Installed(declaredSizeBytes)` — its audio lives inside the app
     * binary, so there is no pack directory to measure (data-model §2.1 rule 1).
     */
    private suspend fun deriveAvailability(row: CatalogRow): ContentAvailability {
        if (row.isStarter) return ContentAvailability.Installed(row.declaredSizeBytes)
        val packId = row.packId
        if (engine.isInstalled(packId)) {
            val path = engine.locate(packId)
            if (path == null) {
                // Platform claims installed but locate returns null — impossible state → treat as
                // evicted, never Installed(0) (data-model §2.1 rule 3).
                return ContentAvailability.NotInstalled(DeliveryFailure.Evicted)
            }
            val bytes = storage.sizeOfDirectory(path)
            // A previously active install has completed — drop it from the active set.
            activeInstalls.remove(packId)
            return ContentAvailability.Installed(bytes)
        }
        // Not installed via the platform's completeness flag. If we asked the engine to install
        // this pack (and it has not yet completed/failed/cancelled), the in-flight transfer is
        // Installing; surface the engine's latest progress snapshot.
        if (packId in activeInstalls) {
            val progress = engine.observe(packId).first()
            return ContentAvailability.Installing(progress)
        }
        return ContentAvailability.NotInstalled(null)
    }

    override fun observeAvailability(matnId: String): Flow<ContentAvailability> = flow {
        val row = loadCatalogRow(matnId) ?: run {
            emit(ContentAvailability.NotInstalled(null))
            return@flow
        }
        if (row.isStarter) {
            // The starter is permanently Installed with its measured declared size — no transfer
            // ever applies (data-model §2.1 rule 1).
            emit(ContentAvailability.Installed(row.declaredSizeBytes))
            return@flow
        }
        // Initial snapshot by polling the engine (D5: availability is computed per read). Then re-
        // derive on every engine state-change signal — each progress event is a cue that something
        // may have changed (transferring, completion, eviction). distinctUntilChanged below
        // collapses redundant re-emissions.
        emit(deriveAvailability(row))
        engine.observe(row.packId).collect { _ ->
            emit(deriveAvailability(row))
        }
    }.catch { emit(ContentAvailability.NotInstalled(null)) }
        .distinctUntilChanged()

    override fun observeLibraryAvailability(): Flow<Map<String, ContentAvailability>> = flow {
        val catalog = loadAllCatalog()
        if (catalog.isEmpty()) {
            emit(emptyMap())
            return@flow
        }
        val flows = catalog.map { row -> observeAvailability(row.matnId) }
        emitAll(
            combine(flows) { arr ->
                catalog.zip(arr.toList()).associate { (row, avail) -> row.matnId to avail }
            },
        )
    }.catch { emit(emptyMap()) }.distinctUntilChanged()

    override fun observeStorageUsage(): Flow<StorageUsage> = flow {
        val catalog = loadAllCatalog()
        val installedEntries = mutableListOf<MatnStorageEntry>()
        var totalUsed = 0L
        var onDemandUsed = 0L
        for (row in catalog) {
            val availability = deriveAvailability(row)
            if (availability is ContentAvailability.Installed) {
                val entry = MatnStorageEntry(
                    matnId = row.matnId,
                    title = row.title,
                    bytes = availability.occupiedBytes,
                    isStarter = row.isStarter,
                )
                installedEntries.add(entry)
                totalUsed += availability.occupiedBytes
                if (!row.isStarter) onDemandUsed += availability.occupiedBytes
            }
        }
        installedEntries.sortByDescending { it.bytes }
        val free = storage.freeSpaceBytes()
        emit(
            StorageUsage(
                entries = installedEntries,
                totalUsedBytes = totalUsed,
                onDemandUsedBytes = onDemandUsed,
                freeSpaceBytes = free,
            ),
        )
    }.catch {
        emit(StorageUsage(emptyList(), 0L, 0L, 0L))
    }.distinctUntilChanged()

    // ----------------------------------------------------- size queries

    override suspend fun declaredSize(matnId: String): Long =
        loadCatalogRow(matnId)?.declaredSizeBytes ?: 0L

    override suspend fun bestKnownSize(matnId: String): Long {
        val row = loadCatalogRow(matnId) ?: return 0L
        return engine.querySize(row.packId) ?: row.declaredSizeBytes
    }

    override suspend fun isStarterMatn(matnId: String): Boolean =
        loadCatalogRow(matnId)?.isStarter == true

    override suspend fun packRootFor(matnId: String): String? {
        val row = loadCatalogRow(matnId) ?: return null
        if (row.isStarter) return null // starter has no pack directory; resolves via Compose resources
        return engine.locate(row.packId)
    }

    // ----------------------------------------------------- install / cancel / remove

    override suspend fun install(matnId: String): Resource<Unit> {
        val row = loadCatalogRow(matnId) ?: return Resource.Failure(
            com.giraffe.matn.core.AppError.NotFound,
        )
        if (row.isStarter) return Resource.Success(Unit) // starter is never an install target
        return lockFor(row.packId).withLock {
            val current = deriveAvailability(row)
            if (current is ContentAvailability.Installing || current is ContentAvailability.Installed) {
                return@withLock Resource.Success(Unit) // duplicate request is a no-op
            }
            val out = engine.install(row.packId)
            when (out) {
                is Resource.Success -> {
                    activeInstalls.add(row.packId)
                    Resource.Success(Unit)
                }
                is Resource.Failure -> {
                    // Engine could not start the transfer (no connectivity / refused / etc.).
                    // Drop the pack from the active set so deriveAvailability reports
                    // NotInstalled on the next read rather than a phantom Installing state.
                    activeInstalls.remove(row.packId)
                    mapEngineFailure(out.error)
                }
            }
        }
    }

    override suspend fun cancel(matnId: String) {
        val row = loadCatalogRow(matnId) ?: return
        if (row.isStarter) return
        lockFor(row.packId).withLock {
            activeInstalls.remove(row.packId)
            engine.cancel(row.packId)
        }
    }

    override suspend fun remove(matnId: String): Resource<RemovalOutcome> {
        val row = loadCatalogRow(matnId) ?: return Resource.Failure(
            com.giraffe.matn.core.AppError.NotFound,
        )
        if (row.isStarter) return Resource.Success(RemovalOutcome.Reclaimed(0L))
        return lockFor(row.packId).withLock {
            val current = deriveAvailability(row)
            if (current is ContentAvailability.NotInstalled) {
                return@withLock Resource.Success(RemovalOutcome.Reclaimed(0L)) // duplicate remove
            }
            // A remove racing an in-flight install of the same matn WINS: cancel first, then
            // remove (contract §3 / spec edge case). Same-pack lock serialises them.
            if (current is ContentAvailability.Installing) {
                engine.cancel(row.packId)
            }
            activeInstalls.remove(row.packId)
            engine.remove(row.packId)
        }
    }

    /**
     * Wrap any [AppError] the engine returns as a [DeliveryError.DeliveryFailed]. The repository —
     * not the engine — owns the contract's delivery-error taxonomy, so any engine-supplied reason
     * (a generic `Storage` failure, a fake's `DeliveryFailure` wrapper, or a Play exception code)
     * is wrapped here so callers above see a typed [DeliveryError].
     */
    private fun mapEngineFailure(error: com.giraffe.matn.core.AppError): Resource<Nothing> {
        val failure = when (error) {
            is DeliveryError.DeliveryFailed -> error.failure
            else -> DeliveryFailure.Unknown(-1)
        }
        return Resource.Failure(DeliveryError.DeliveryFailed(failure))
    }

    override suspend fun removeAll(): Resource<List<RemovalOutcome>> {
        val catalog = loadAllCatalog()
        val outcomes = mutableListOf<RemovalOutcome>()
        for (row in catalog) {
            if (row.isStarter) continue // "remove all" spares the starter (FR-029)
            // Only target packs that are currently installing or installed — a no-op remove on a
            // pack that is already NotInstalled only clutters the outcome list with `Reclaimed(0)`
            // entries the UI would have to filter anyway.
            val current = deriveAvailability(row)
            if (current is ContentAvailability.NotInstalled) continue
            val outcome = remove(row.matnId)
            if (outcome is Resource.Success) {
                outcomes.add(outcome.data)
            }
        }
        return Resource.Success(outcomes)
    }
}