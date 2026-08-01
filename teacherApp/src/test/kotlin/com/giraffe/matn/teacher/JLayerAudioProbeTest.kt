package com.giraffe.matn.teacher

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.audio.ByteArrayByteSource
import com.giraffe.matn.teacher.platform.JLayerAudioProbe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun tinyMp3Bytes(): ByteArray =
    checkNotNull(object {}.javaClass.getResourceAsStream("/tiny.mp3")) { "tiny.mp3 test resource missing" }.readBytes()

/** `tiny.mp3` is a synthesized ~1 s MPEG-1 Layer III mono 44.1 kHz file (`Mp3Fixtures`-style
 * silent frames) — real 4-byte headers, no external encoder available in this environment. It
 * exercises the JLayer decode path end to end; it is not a claim about audible content. */
class JLayerAudioProbeTest {

    @Test
    fun `duration is within 50ms of the real length`() = runTest {
        val source = ByteArrayByteSource(tinyMp3Bytes())
        val probe = JLayerAudioProbe()

        val result = probe.probe(source)

        assertTrue(result is Resource.Success)
        val expectedMs = 40 * 1152.0 * 1000 / 44100 // 40 synthesized frames at 44.1 kHz
        assertTrue(kotlin.math.abs(result.data.durationMs - expectedMs) <= 50)
    }

    @Test
    fun `profile matches the file`() = runTest {
        val source = ByteArrayByteSource(tinyMp3Bytes())
        val probe = JLayerAudioProbe()

        val result = probe.probe(source)

        assertTrue(result is Resource.Success)
        assertEquals(44100, result.data.profile.sampleRate)
        assertEquals(1, result.data.profile.channels)
    }

    @Test
    fun `peaks returns 100 finite values in -1f to 1f`() = runTest {
        val source = ByteArrayByteSource(tinyMp3Bytes())
        val probe = JLayerAudioProbe()

        val result = probe.peaks(source, buckets = 100)

        assertTrue(result is Resource.Success)
        assertEquals(100, result.data.size)
        assertTrue(result.data.all { it.isFinite() && it in -1f..1f })
    }
}
