package com.giraffe.matn.audio

import com.giraffe.matn.data.audio.parseHeader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Builds a raw 4-byte Layer III header for direct bit-level control, independent of [Mp3Fixtures]. */
private fun rawHeader(
    versionBits: Int,
    bitrateIndex: Int,
    sampleRateIndex: Int,
    padding: Int = 0,
    channelModeBits: Int = 0b11,
    protection: Int = 1,
): ByteArray {
    val b1 = (0b111 shl 5) or (versionBits shl 3) or (0b01 shl 1) or protection
    val b2 = (bitrateIndex shl 4) or (sampleRateIndex shl 2) or (padding shl 1)
    val b3 = channelModeBits shl 6
    return byteArrayOf(0xFF.toByte(), b1.toByte(), b2.toByte(), b3.toByte())
}

class Mp3HeaderTest {

    @Test
    fun `MPEG1 44_1kHz mono 128kbps gives frameLengthBytes 417`() {
        val header = parseHeader(rawHeader(versionBits = 0b11, bitrateIndex = 9, sampleRateIndex = 0), 0)

        assertEquals(417, header?.frameLengthBytes)
        assertEquals(44100, header?.sampleRate)
        assertEquals(1, header?.channels)
        assertEquals(1152, header?.samplesPerFrame)
    }

    @Test
    fun `padding bit adds one byte`() {
        val header = parseHeader(rawHeader(versionBits = 0b11, bitrateIndex = 9, sampleRateIndex = 0, padding = 1), 0)

        assertEquals(418, header?.frameLengthBytes)
    }

    @Test
    fun `MPEG2 gives 576 samples per frame`() {
        val header = parseHeader(rawHeader(versionBits = 0b10, bitrateIndex = 9, sampleRateIndex = 0), 0)

        assertEquals(576, header?.samplesPerFrame)
        assertEquals(22050, header?.sampleRate)
    }

    @Test
    fun `a non-sync word returns null`() {
        val bytes = byteArrayOf(0x00, 0x00, 0x00, 0x00)

        assertNull(parseHeader(bytes, 0))
    }

    @Test
    fun `a reserved sample-rate index returns null`() {
        val header = parseHeader(rawHeader(versionBits = 0b11, bitrateIndex = 9, sampleRateIndex = 3), 0)

        assertNull(header)
    }

    @Test
    fun `stereo channel mode reports two channels`() {
        val header = parseHeader(rawHeader(versionBits = 0b11, bitrateIndex = 9, sampleRateIndex = 0, channelModeBits = 0b00), 0)

        assertEquals(2, header?.channels)
    }
}
