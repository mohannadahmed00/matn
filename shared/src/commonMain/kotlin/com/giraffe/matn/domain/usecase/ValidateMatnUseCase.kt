package com.giraffe.matn.domain.usecase

import com.giraffe.matn.core.Resource
import com.giraffe.matn.core.usecase.UseCase
import com.giraffe.matn.domain.catalog.ContentIntegrityValidator
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.ValidationReport

/** The teacher tool's entry point into [ContentIntegrityValidator] (`contracts/validation-contract.md`). */
class ValidateMatnUseCase : UseCase<MatnDraft, ValidationReport> {
    override suspend fun invoke(params: MatnDraft): Resource<ValidationReport> =
        Resource.Success(ContentIntegrityValidator.validate(params))
}
