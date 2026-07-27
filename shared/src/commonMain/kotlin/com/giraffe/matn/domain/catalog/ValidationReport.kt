package com.giraffe.matn.domain.catalog

import com.giraffe.matn.domain.error.ContentIntegrityError

/** Blocking vs deferred split (FR-027/FR-028). */
data class ValidationReport(
    val blocking: List<ContentIntegrityError>,
    val deferred: List<ContentIntegrityError>,
) {
    val canPublish: Boolean get() = blocking.isEmpty()
    val all: List<ContentIntegrityError> get() = blocking + deferred
}
