package com.giraffe.matn.audio

/**
 * Synthesizes valid MPEG-1/2/2.5 Layer III byte streams for `commonTest` — real 4-byte frame
 * headers per `tasks.md`'s Ground Rules table, with silent (zero) frame bodies. Deliberately
 * independent of `data.audio.Mp3Header`/`Mp3FrameIndex`, the production parsers under test, so a
 * shared bug in both cannot make a broken test pass.
 */
object Mp3Fixtures {

    private val SAMPLE_RATES_MPEG1 = intArrayOf(44100, 48000, 32000)
    private val SAMPLE_RATES_MPEG2 = intArrayOf(22050, 24000, 16000)
    private val SAMPLE_RATES_MPEG2_5 = intArrayOf(11025, 12000, 8000)

    private val BITRATES_MPEG1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val BITRATES_MPEG2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

    /**
     * Builds [frames] consecutive Layer III frames. [id3v2Bytes] optionally prepends an ID3v2 block
     * of that total size (header + content). [xing] optionally writes a fake "Xing" marker into the
     * first frame at the standard side-info offset, as a real VBR-tagging encoder would.
     */
    fun mp3(
        frames: Int,
        sampleRate: Int = 44100,
        bitrateKbps: Int = 128,
        mono: Boolean = true,
        id3v2Bytes: Int = 0,
        xing: Boolean = false,
        id3v1: Boolean = false,
    ): ByteArray {
        val versionBits = when (sampleRate) {
            in SAMPLE_RATES_MPEG1 -> 0b11
            in SAMPLE_RATES_MPEG2 -> 0b10
            in SAMPLE_RATES_MPEG2_5 -> 0b00
            else -> error("unsupported sample rate $sampleRate")
        }
        val isMpeg1 = versionBits == 0b11
        val bitrateTable = if (isMpeg1) BITRATES_MPEG1 else BITRATES_MPEG2
        val bitrateIndex = bitrateTable.indexOf(bitrateKbps)
        require(bitrateIndex > 0) { "unsupported bitrate $bitrateKbps for this version" }
        val sampleRateTable = when (versionBits) {
            0b11 -> SAMPLE_RATES_MPEG1
            0b10 -> SAMPLE_RATES_MPEG2
            else -> SAMPLE_RATES_MPEG2_5
        }
        val sampleRateIndex = sampleRateTable.indexOf(sampleRate)
        val channelModeBits = if (mono) 0b11 else 0b00
        val bytesPerFrameFactor = if (isMpeg1) 144 else 72
        val frameLengthBytes = (bytesPerFrameFactor * bitrateKbps * 1000) / sampleRate

        val header = byteArrayOf(
            0xFF.toByte(),
            ((0b111 shl 5) or (versionBits shl 3) or (0b01 shl 1) or 1).toByte(),
            ((bitrateIndex shl 4) or (sampleRateIndex shl 2)).toByte(),
            (channelModeBits shl 6).toByte(),
        )

        val sideInfoSize = when {
            isMpeg1 && !mono -> 32
            isMpeg1 && mono -> 17
            !isMpeg1 && !mono -> 17
            else -> 9
        }

        val out = ArrayList<Byte>(id3v2Bytes + frames * frameLengthBytes + if (id3v1) 128 else 0)

        if (id3v2Bytes > 0) {
            val contentSize = id3v2Bytes - 10
            out.add('I'.code.toByte())
            out.add('D'.code.toByte())
            out.add('3'.code.toByte())
            out.add(0x03)
            out.add(0x00)
            out.add(0x00) // flags — no footer
            out.add(((contentSize shr 21) and 0x7F).toByte())
            out.add(((contentSize shr 14) and 0x7F).toByte())
            out.add(((contentSize shr 7) and 0x7F).toByte())
            out.add((contentSize and 0x7F).toByte())
            repeat(contentSize) { out.add(0) }
        }

        repeat(frames) { frameIndex ->
            header.forEach { out.add(it) }
            val bodySize = frameLengthBytes - 4
            val body = ByteArray(bodySize)
            if (xing && frameIndex == 0 && bodySize >= sideInfoSize + 4) {
                body[sideInfoSize] = 'X'.code.toByte()
                body[sideInfoSize + 1] = 'i'.code.toByte()
                body[sideInfoSize + 2] = 'n'.code.toByte()
                body[sideInfoSize + 3] = 'g'.code.toByte()
            }
            body.forEach { out.add(it) }
        }

        if (id3v1) {
            out.add('T'.code.toByte())
            out.add('A'.code.toByte())
            out.add('G'.code.toByte())
            repeat(125) { out.add(0) }
        }

        return out.toByteArray()
    }
}
