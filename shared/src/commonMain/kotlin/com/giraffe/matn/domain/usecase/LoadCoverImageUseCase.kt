package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.CatalogRepository

/** Fetches a stored cover's bytes so the editor can show the image rather than its object path.
 * The counterpart to [UploadCoverImageUseCase]; the bucket is private, so there is no URL to point
 * an image at. */
class LoadCoverImageUseCase(private val repository: CatalogRepository) : UseCase<String, ByteArray> {
    override suspend fun invoke(params: String): Resource<ByteArray> = repository.downloadCover(params)
}
