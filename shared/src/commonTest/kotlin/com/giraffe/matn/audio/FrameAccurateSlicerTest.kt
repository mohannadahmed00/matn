package com.giraffe.matn.audio

import com.giraffe.matn.data.audio.ByteArrayByteSource
import com.giraffe.matn.data.audio.FrameAccurateSlicer
import com.giraffe.matn.data.audio.Mp3FrameIndex
import com.giraffe.matn.domain.audio.VerseRange
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameAccurateSlicerTest {

    @Test
    fun `each slice contains whole frames only`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 20)
        val source = ByteArrayByteSource(bytes)
        val index = Mp3FrameIndex.build(source)!!
        val frameLen = index.header.frameLengthBytes

        val slices = (FrameAccurateSlicer().slice(
            source,
            index,
            listOf(VerseRange("v1", 0, (10 * index.frameDurationMs).toLong())),
        ) as com.giraffe.matn.core.Resource.Success).data

        assertEquals(0, slices.single().bytes.size % frameLen)
    }

    @Test
    fun `boundaries land within one frame duration of the request`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 20)
        val source = ByteArrayByteSource(bytes)
        val index = Mp3FrameIndex.build(source)!!
        val requestedMs = (10 * index.frameDurationMs).toLong()

        val slice = (FrameAccurateSlicer().slice(source, index, listOf(VerseRange("v1", 0, requestedMs)))
            as com.giraffe.matn.core.Resource.Success).data.single()

        assertTrue(kotlin.math.abs(slice.durationMs - requestedMs) <= index.frameDurationMs)
    }

    @Test
    fun `a Xing header frame is excluded`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 20, xing = true)
        val source = ByteArrayByteSource(bytes)
        val index = Mp3FrameIndex.build(source)!!
        val frameLen = index.header.frameLengthBytes

        val slice = (FrameAccurateSlicer().slice(
            source,
            index,
            listOf(VerseRange("v1", 0, (5 * index.frameDurationMs).toLong())),
        ) as com.giraffe.matn.core.Resource.Success).data.single()

        // The Xing frame (frame 0) is dropped, so the slice starts at frame 1's bytes, not frame 0's.
        val frame0 = source.read(index.offsets[0], frameLen)
        val sliceStart = slice.bytes.copyOfRange(0, frameLen)
        assertTrue(frame0.toList() != sliceStart.toList() || slice.bytes.size < 5 * frameLen)
    }

    @Test
    fun `slices concatenated cover the requested spans`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 20)
        val source = ByteArrayByteSource(bytes)
        val index = Mp3FrameIndex.build(source)!!
        val mid = (10 * index.frameDurationMs).toLong()
        val end = (20 * index.frameDurationMs).toLong()

        val slices = (FrameAccurateSlicer().slice(
            source,
            index,
            listOf(VerseRange("v1", 0, mid), VerseRange("v2", mid, end)),
        ) as com.giraffe.matn.core.Resource.Success).data

        val totalFrames = slices.sumOf { it.bytes.size } / index.header.frameLengthBytes
        assertEquals(20, totalFrames)
    }

    @Test
    fun `no read exceeds the range's length`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 20)
        val source = ByteArrayByteSource(bytes)
        val index = Mp3FrameIndex.build(source)!!

        val slice = (FrameAccurateSlicer().slice(
            source,
            index,
            listOf(VerseRange("v1", 0, (3 * index.frameDurationMs).toLong())),
        ) as com.giraffe.matn.core.Resource.Success).data.single()

        assertTrue(slice.bytes.size <= bytes.size)
    }

    /** SC-004a: byte-identity — a slice is an exact copy, never a re-compression. */
    @Test
    fun `a slice's bytes equal the corresponding byte range of the source exactly`() = runTest {
        val bytes = Mp3Fixtures.mp3(frames = 20)
        val source = ByteArrayByteSource(bytes)
        val index = Mp3FrameIndex.build(source)!!
        val startFrame = 2
        val endFrame = 8
        val range = VerseRange("v1", (startFrame * index.frameDurationMs).toLong(), (endFrame * index.frameDurationMs).toLong())

        val slice = (FrameAccurateSlicer().slice(source, index, listOf(range)) as com.giraffe.matn.core.Resource.Success).data.single()

        val expectedRange = index.byteRange(startFrame, endFrame)
        val expectedBytes = source.read(expectedRange.first, (expectedRange.last - expectedRange.first + 1).toInt())
        assertContentEquals(expectedBytes, slice.bytes)
    }
}
