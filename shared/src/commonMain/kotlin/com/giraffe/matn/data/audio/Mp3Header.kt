package com.giraffe.matn.data.audio

/**
 * A parsed MPEG-1/2/2.5 Layer III frame header (`tasks.md` Ground Rules — frame header reference).
 * Pure byte math, no decoding — duration, sample rate, and channel count are all derivable from
 * the 4-byte header alone.
 */
data class Mp3Header(
    val sampleRate: Int,
    val channels: Int,
    val frameLengthBytes: Int,
    val samplesPerFrame: Int,
)

private val SAMPLE_RATES_MPEG1 = intArrayOf(44100, 48000, 32000)
private val SAMPLE_RATES_MPEG2 = intArrayOf(22050, 24000, 16000)
private val SAMPLE_RATES_MPEG2_5 = intArrayOf(11025, 12000, 8000)

private val BITRATES_MPEG1_LAYER3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
private val BITRATES_MPEG2_LAYER3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

/**
 * Parses the 4 big-endian bytes at [offset] in [bytes] as a Layer III frame header. Returns `null`
 * when the sync word, layer, or sample-rate index is not a valid Layer III frame — the caller
 * (`Mp3FrameIndex`) treats that as "not a frame here", never as an exception.
 */
fun parseHeader(bytes: ByteArray, offset: Int): Mp3Header? {
    if (offset < 0 || offset + 4 > bytes.size) return null

    val b0 = bytes[offset].toInt() and 0xFF
    val b1 = bytes[offset + 1].toInt() and 0xFF
    val b2 = bytes[offset + 2].toInt() and 0xFF
    val b3 = bytes[offset + 3].toInt() and 0xFF

    // Sync word: byte0 all 1s, top 3 bits of byte1 all 1s.
    if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null

    val versionBits = (b1 shr 3) and 0x03
    val layerBits = (b1 shr 1) and 0x03
    if (layerBits != 0b01) return null // only Layer III is supported

    val samplesPerFrame: Int
    val sampleRates: IntArray
    val bitrateTable: IntArray
    when (versionBits) {
        0b11 -> { // MPEG1
            samplesPerFrame = 1152
            sampleRates = SAMPLE_RATES_MPEG1
            bitrateTable = BITRATES_MPEG1_LAYER3
        }
        0b10 -> { // MPEG2
            samplesPerFrame = 576
            sampleRates = SAMPLE_RATES_MPEG2
            bitrateTable = BITRATES_MPEG2_LAYER3
        }
        0b00 -> { // MPEG2.5
            samplesPerFrame = 576
            sampleRates = SAMPLE_RATES_MPEG2_5
            bitrateTable = BITRATES_MPEG2_LAYER3
        }
        else -> return null // 0b01 reserved
    }

    val bitrateIndex = (b2 shr 4) and 0x0F
    if (bitrateIndex == 0 || bitrateIndex == 15) return null // free or invalid

    val sampleRateIndex = (b2 shr 2) and 0x03
    if (sampleRateIndex == 3) return null // reserved
    val sampleRate = sampleRates[sampleRateIndex]

    val padding = (b2 shr 1) and 0x01
    val bitrateBps = bitrateTable[bitrateIndex] * 1000

    val bytesPerFrameFactor = if (versionBits == 0b11) 144 else 72
    val frameLengthBytes = (bytesPerFrameFactor * bitrateBps) / sampleRate + padding

    val channelModeBits = (b3 shr 6) and 0x03
    val channels = if (channelModeBits == 0b11) 1 else 2

    return Mp3Header(
        sampleRate = sampleRate,
        channels = channels,
        frameLengthBytes = frameLengthBytes,
        samplesPerFrame = samplesPerFrame,
    )
}
