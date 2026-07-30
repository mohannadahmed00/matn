package com.giraffe.matn.domain.error

import com.giraffe.matn.core.AppError

sealed interface ContentIntegrityError : AppError {
    data class DuplicateId(val id: String) : ContentIntegrityError
    data class DuplicateDisplayNumber(val matnId: String, val number: Int) : ContentIntegrityError
    data class DuplicateChapterOrder(val matnId: String, val order: Int) : ContentIntegrityError
    data class MissingAudio(val verseId: String) : ContentIntegrityError
    data class DuplicateAudioRef(val fileRef: String) : ContentIntegrityError
    data class OrphanChapterRef(val verseId: String) : ContentIntegrityError
    data class StructureMismatch(val matnId: String, val detail: String) : ContentIntegrityError
    data class InvalidId(val detail: String) : ContentIntegrityError
    data class Aggregate(val problems: List<ContentIntegrityError>) : ContentIntegrityError
    data class EmptyMatn(val matnId: String) : ContentIntegrityError
    data class DocumentTooLarge(val matnId: String, val bytes: Long, val limitBytes: Long) : ContentIntegrityError
}