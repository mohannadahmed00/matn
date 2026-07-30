package com.giraffe.matn.domain.catalog

import com.giraffe.matn.core.AppError

/** Thrown by [CatalogRepository.observeAuthored] implementations on a failed fetch —
 * `Flow<List<CatalogEntry>>` has no error channel of its own, so a failure is surfaced as a flow
 * exception a collector can `catch` and map back to an error state, rather than silently
 * degrading to an empty list the way a corrupt local row would (that convention is for data
 * damage, not a reachability failure the teacher can retry). */
class CatalogLoadException(val error: AppError) : Exception()
