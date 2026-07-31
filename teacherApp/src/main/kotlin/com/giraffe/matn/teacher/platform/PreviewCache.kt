package com.giraffe.matn.teacher.platform

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.storage.StorageRestClient
import java.io.File

/** Downloads a verse object once via [StorageRestClient.download], keyed by object name — because
 * names are content-tagged (immutable), a cached file is never stale and needs no invalidation
 * (`research.md` D9). */
class PreviewCache(
    private val storageClient: StorageRestClient,
    private val cacheDir: File = File(AppDataDir.path, "preview-cache"),
) {
    init {
        cacheDir.mkdirs()
    }

    suspend fun getOrDownload(objectPath: String): Resource<File> {
        val file = File(cacheDir, objectPath.replace('/', '_'))
        if (file.exists()) return Resource.Success(file)
        return when (val result = storageClient.download(objectPath)) {
            is Resource.Success -> {
                file.writeBytes(result.data)
                Resource.Success(file)
            }
            is Resource.Failure -> result
        }
    }
}
