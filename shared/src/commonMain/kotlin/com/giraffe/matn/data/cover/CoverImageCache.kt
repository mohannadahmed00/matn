package com.giraffe.matn.data.cover

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.delivery.ContentFileStore
import com.giraffe.matn.data.remote.storage.StorageRestClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cover images, fetched opportunistically and cached on disk (FR-012).
 *
 * Two rules govern this class, and both are load-bearing:
 *
 * 1. **It never throws and never fails anything else.** A cover that cannot be fetched returns
 *    `null` and the card renders its placeholder with every other field intact (User Story 1
 *    scenario 6). A missing cover is not an error state the student should ever see.
 *
 * 2. **It is browse-time only.** Covers must never be fetched from the reading or playback path —
 *    that would put a network call inside the offline guarantee, breaching Constitution Principle
 *    VI's one binding condition and SC-004's "zero network requests".
 *
 * Cached bytes live at `covers/{matnId}`, deliberately **outside** `downloads/`. That placement is
 * what makes Clarification 3 structural rather than a filter someone has to remember: the removal
 * path deletes `downloads/{matnId}/`, so it cannot touch a cover by accident, and the storage
 * figures measure the same directory, so they cannot count one (FR-030, SC-008, SC-009).
 */
class CoverImageCache(
    private val storageClient: StorageRestClient,
    private val files: ContentFileStore,
) {
    /** Serialises fetches so two cards for the same matn do not both hit the network. */
    private val guard = Mutex()

    /**
     * Cached bytes for [matnId], fetching them once if absent.
     *
     * @param coverImageRef the published storage object path. `null` means the teacher set no
     *   cover, which is not a failure — it simply resolves to the placeholder.
     */
    suspend fun load(matnId: String, coverImageRef: String?): ByteArray? {
        if (coverImageRef.isNullOrBlank()) return null
        val cachePath = ContentFileStore.coverPath(matnId)

        readCached(cachePath)?.let { return it }

        return guard.withLock {
            // Re-check inside the lock: a concurrent caller may have populated it while we waited.
            readCached(cachePath) ?: fetchAndCache(cachePath, coverImageRef)
        }
    }

    /**
     * Bytes already on disk for [matnId], or `null` — **never** a network call.
     *
     * This is what surfaces reachable from the reading path (the details header) use. [load] cannot
     * be used there without breaching rule 2 above: the details screen is shown for a downloaded
     * matn too, so a fetch on that path would put a request inside the offline guarantee. Reaching
     * details always means passing through the library grid first, which is where [load] already
     * populated the cache, so in practice the cover is present.
     */
    suspend fun cached(matnId: String): ByteArray? = readCached(ContentFileStore.coverPath(matnId))

    private suspend fun readCached(cachePath: String): ByteArray? = try {
        files.readFile(cachePath)?.takeIf { it.isNotEmpty() }
    } catch (c: CancellationException) {
        throw c
    } catch (_: Throwable) {
        null
    }

    private suspend fun fetchAndCache(cachePath: String, coverImageRef: String): ByteArray? = try {
        when (val result = storageClient.download(coverImageRef)) {
            is Resource.Success -> result.data.also { bytes ->
                // A write failure is not worth surfacing either — the image still renders this
                // session, it just gets re-fetched next time.
                runCatching { files.writeFile(cachePath, bytes) }
            }
            is Resource.Failure -> null
        }
    } catch (c: CancellationException) {
        throw c
    } catch (_: Throwable) {
        null
    }
}
