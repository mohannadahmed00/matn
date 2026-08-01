package com.giraffe.matn.domain.audio

/**
 * The stable content tag that makes an audio object's name a function of its bytes
 * (`contracts/audio-artifact-contract.md` §3, research D4). An identity key, not a security
 * primitive: a collision would only make an upload wrongly skipped, which the byte-size check in
 * the resume predicate catches.
 */
object AudioContentTag {

    private const val FNV_OFFSET_BASIS = 0xcbf29ce484222325uL
    private const val FNV_PRIME = 0x100000001b3uL

    /** 16 lowercase hex characters — FNV-1a-64 over [bytes]. */
    fun of(bytes: ByteArray): String {
        var hash = FNV_OFFSET_BASIS
        for (b in bytes) {
            hash = (hash xor (b.toInt() and 0xFF).toULong()) * FNV_PRIME
        }
        return hash.toString(16).padStart(16, '0')
    }

    /** `matns/{matnId}/verses/{verseId}-{tag}.mp3` (`data-model.md` §2). */
    fun objectPath(matnId: String, verseId: String, tag: String): String =
        "matns/$matnId/verses/$verseId-$tag.mp3"
}
