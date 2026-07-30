package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogRepository

/** FR-016: rejects an oversized or wrong-type image before any upload call is made. */
class UploadCoverImageUseCase(private val repository: CatalogRepository) : UseCase<UploadCoverImageUseCase.Params, String> {
    data class Params(val matnId: String, val bytes: ByteArray, val ext: String)

    override suspend fun invoke(params: Params): Resource<String> {
        if (params.bytes.size > MAX_COVER_BYTES) {
            return Resource.Failure(AppError.Storage("Cover image exceeds the 5 MB limit"))
        }
        if (params.ext.lowercase() !in ALLOWED_EXTENSIONS) {
            return Resource.Failure(AppError.Storage("Cover image must be png, jpeg, or webp"))
        }
        return repository.uploadCover(params.matnId, params.bytes, params.ext)
    }

    companion object {
        const val MAX_COVER_BYTES = 5 * 1024 * 1024
        val ALLOWED_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
    }
}
