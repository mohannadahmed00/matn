package com.giraffe.matn.domain.catalog

import com.giraffe.matn.domain.model.StructureKind

/**
 * The student-visible summary of one published matn (FR-002, data-model §2.1).
 *
 * Carries **no verse text and no audio** — that is what makes FR-003 structural rather than a
 * convention: a catalog sync selects only these columns, so verse content physically cannot travel
 * on one.
 *
 * Its lifetime is deliberately different from the content tables: an overview persists from the
 * first successful sync regardless of download state (FR-005), survives removal (FR-029), and
 * survives withdrawal while the matn is still downloaded (FR-009).
 *
 * Reuses [StructureKind] and [AudioCompleteness] from the teacher-side domain rather than
 * redeclaring them — the same types the teacher publishes are the ones the student reads.
 */
data class CatalogOverview(
    val matnId: String,
    val title: String,
    val author: String,
    val description: String,
    val coverImageRef: String?,
    val structureKind: StructureKind,
    val verseCount: Int,
    /**
     * The teacher's published `declared_size_bytes`. Always present, so a size renders offline and
     * the FR-019 free-space check never waits on a lookup (spec Assumptions).
     */
    val downloadSizeBytes: Long,
    val audioCompleteness: AudioCompleteness,
    /**
     * Phase 11's server-owned monotonic counter, bumped by the `matns_bump_revision` trigger and
     * impossible for a client to forge. Comparing it against `downloaded_matn.revision` is what
     * flags an available update (FR-010).
     */
    val revision: Long,
    /**
     * True when this matn vanished from the source while still downloaded (FR-009). It is excluded
     * from anything offering a new download and from search's catalog half, but keeps rendering for
     * the content the student already has — withdrawal never takes away what someone downloaded.
     */
    val withdrawn: Boolean,
)
