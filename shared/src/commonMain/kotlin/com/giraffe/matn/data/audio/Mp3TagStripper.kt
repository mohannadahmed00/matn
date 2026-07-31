package com.giraffe.matn.data.audio

/**
 * Detects a Xing/Info/VBRI header frame so it can be dropped from a slice (`split-contract.md` §4).
 * Such a frame declares the *source's* frame count and duration; copied into a slice it makes every
 * player report the wrong length for that verse (`research.md` D2).
 */
fun isXingOrVbriFrame(bytes: ByteArray, frameOffset: Int, header: Mp3Header): Boolean {
    val sideInfoSize = when {
        header.samplesPerFrame == 1152 && header.channels == 2 -> 32
        header.samplesPerFrame == 1152 && header.channels == 1 -> 17
        header.samplesPerFrame == 576 && header.channels == 2 -> 17
        else -> 9
    }
    if (hasMarkerAt(bytes, frameOffset + 4 + sideInfoSize, "Xing")) return true
    if (hasMarkerAt(bytes, frameOffset + 4 + sideInfoSize, "Info")) return true
    if (hasMarkerAt(bytes, frameOffset + 36, "VBRI")) return true
    return false
}

private fun hasMarkerAt(bytes: ByteArray, offset: Int, marker: String): Boolean {
    if (offset < 0 || offset + marker.length > bytes.size) return false
    for (i in marker.indices) {
        if (bytes[offset + i] != marker[i].code.toByte()) return false
    }
    return true
}
