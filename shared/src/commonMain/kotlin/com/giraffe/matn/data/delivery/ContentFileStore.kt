package com.giraffe.matn.data.delivery

import com.giraffe.matn.domain.delivery.DeviceStorage
import kotlinx.coroutines.CancellationException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.write

/**
 * All on-device content file I/O, in `commonMain` (research D4).
 *
 * Writing the four operations below three times across `androidMain`/`iosMain`/`jvmMain` is exactly
 * what Constitution Principle IV prohibits ("if both need it, it belongs in `commonMain`"), so the
 * platform seam [DeviceStorage] keeps only the one genuinely platform-specific fact — *where* the
 * root directory is — and everything under it is composed here.
 *
 * Layout (delivery-contract.md §1), all relative to [DeviceStorage.contentRootPath]:
 * ```
 * downloads/.tmp-{matnId}/audio/{fileRef}   staging; existence implies nothing
 * downloads/{matnId}/audio/{fileRef}        existence IS "downloaded" (FR-022)
 * covers/{matnId}                           never counted, never removed (FR-012)
 * ```
 *
 * The invariant this class exists to protect: **there is never a partial directory under
 * `downloads/`**. A transfer stages under `.tmp-` and is moved into place only on full success, so
 * a half-written download is invisible to availability by construction and there is no partial
 * state to mis-report (FR-016, SC-006).
 */
class ContentFileStore(private val storage: DeviceStorage) {

    private suspend fun absolute(relativePath: String): Path =
        Path(storage.contentRootPath(), relativePath)

    /** Creates [relativePath]'s parent chain and writes [bytes], replacing any existing file. */
    suspend fun writeFile(relativePath: String, bytes: ByteArray) {
        val target = absolute(relativePath)
        target.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(target).buffered().use { sink -> sink.write(bytes) }
    }

    suspend fun readFile(relativePath: String): ByteArray? {
        val target = absolute(relativePath)
        if (SystemFileSystem.metadataOrNull(target) == null) return null
        return SystemFileSystem.source(target).buffered().use { source -> source.readByteArray() }
    }

    suspend fun exists(relativePath: String): Boolean =
        SystemFileSystem.metadataOrNull(absolute(relativePath)) != null

    suspend fun createDirectories(relativePath: String) {
        SystemFileSystem.createDirectories(absolute(relativePath))
    }

    /**
     * Deletes a file or a whole directory tree. Absent paths are a no-op, so a duplicate removal
     * and a removal of something a device cleaner already took are both harmless (FR-045).
     */
    suspend fun deleteTree(relativePath: String) {
        deleteRecursively(absolute(relativePath))
    }

    /**
     * Moves [from] onto [to], replacing whatever was at [to]. This is the commit step of a download
     * (delivery-contract.md §4 step 7) — the moment a staged transfer becomes "downloaded".
     */
    suspend fun moveTree(from: String, to: String) {
        val source = absolute(from)
        val target = absolute(to)
        if (SystemFileSystem.metadataOrNull(source) == null) return
        deleteRecursively(target)
        target.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.atomicMove(source, target)
    }

    /** Total bytes under [relativePath]; 0 when absent. Backs the Settings figures (SC-008). */
    suspend fun sizeOfTree(relativePath: String): Long = sizeOf(absolute(relativePath))

    // ------------------------------------------------------------------ internals

    private fun deleteRecursively(path: Path) {
        val metadata = SystemFileSystem.metadataOrNull(path) ?: return
        if (metadata.isDirectory) {
            SystemFileSystem.list(path).forEach { child -> deleteRecursively(child) }
        }
        try {
            SystemFileSystem.delete(path, mustExist = false)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            // Best-effort: a file vanishing underneath us is the outcome we wanted anyway.
        }
    }

    private fun sizeOf(path: Path): Long {
        val metadata = SystemFileSystem.metadataOrNull(path) ?: return 0L
        if (!metadata.isDirectory) return metadata.size
        return SystemFileSystem.list(path).sumOf { child -> sizeOf(child) }
    }

    companion object {
        const val DOWNLOADS = "downloads"
        const val COVERS = "covers"

        fun downloadDir(matnId: String): String = "$DOWNLOADS/$matnId"

        /** The staging directory. Deliberately dot-prefixed and *outside* the committed name, so
         *  nothing that scans `downloads/{matnId}/` can ever see a partial transfer. */
        fun stagingDir(matnId: String): String = "$DOWNLOADS/.tmp-$matnId"

        fun audioPath(matnId: String, fileRef: String): String =
            "${downloadDir(matnId)}/audio/${fileRef.substringAfterLast('/')}"

        fun stagedAudioPath(matnId: String, fileRef: String): String =
            "${stagingDir(matnId)}/audio/${fileRef.substringAfterLast('/')}"

        fun coverPath(matnId: String): String = "$COVERS/$matnId"
    }
}
