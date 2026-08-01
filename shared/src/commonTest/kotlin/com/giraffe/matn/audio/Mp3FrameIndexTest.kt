package com.giraffe.matn.audio

import com.giraffe.matn.data.audio.ByteArrayByteSource
import com.giraffe.matn.data.audio.Mp3FrameIndex
import kotlinx.coroutines.test.runTest
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Mp3FrameIndexTest {

    @Test
    fun `frame count matches the synthesized frames`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 10)

        val index = Mp3FrameIndex.build(ByteArrayByteSource(bytes))

        assertEquals(10, index?.offsets?.size)
    }

    @Test
    fun `durationMs is frames times frame duration, rounded`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 10, sampleRate = 44100)

        val index = Mp3FrameIndex.build(ByteArrayByteSource(bytes))

        val expectedFrameDurationMs = 1152.0 * 1000 / 44100
        assertEquals((10 * expectedFrameDurationMs).roundToLong(), index?.durationMs)
    }

    @Test
    fun `a 2KB ID3v2 block is skipped`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 5, id3v2Bytes = 2048)

        val index = Mp3FrameIndex.build(ByteArrayByteSource(bytes))

        assertEquals(5, index?.offsets?.size)
        assertEquals(2048L, index?.offsets?.get(0))
    }

    @Test
    fun `frameAtMs snaps to the nearest frame`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 10, sampleRate = 44100)
        val index = Mp3FrameIndex.build(ByteArrayByteSource(bytes))!!
        val frameDurationMs = index.frameDurationMs

        assertEquals(0, index.frameAtMs(0))
        assertEquals(1, index.frameAtMs(frameDurationMs.roundToLong()))
        assertEquals(9, index.frameAtMs(1_000_000L)) // clamps to the last frame
    }

    @Test
    fun `byteRange returns whole frames only`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 10, sampleRate = 44100, bitrateKbps = 128)
        val index = Mp3FrameIndex.build(ByteArrayByteSource(bytes))!!

        val range = index.byteRange(2, 5)

        assertEquals(index.offsets[2], range.first)
        assertEquals(index.offsets[5] - 1, range.last)
    }

    @Test
    fun `a non-sync byte stream yields no index`() = runTest {
        val bytes = ByteArray(16) { 0 }

        val index = Mp3FrameIndex.build(ByteArrayByteSource(bytes))

        assertNull(index)
    }
}
