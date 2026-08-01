package com.giraffe.matn.audio

import com.giraffe.matn.domain.audio.AudioContentTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AudioContentTagTest {

    @Test
    fun `same bytes give the same tag`() {
        val bytes = "recording".encodeToByteArray()

        assertEquals(AudioContentTag.of(bytes), AudioContentTag.of(bytes.copyOf()))
    }

    @Test
    fun `one flipped byte gives a different tag`() {
        val original = "recording".encodeToByteArray()
        val flipped = original.copyOf().also { it[0] = (it[0] + 1).toByte() }

        assertNotEquals(AudioContentTag.of(original), AudioContentTag.of(flipped))
    }

    @Test
    fun `the tag is always 16 lowercase hex chars`() {
        val tag = AudioContentTag.of(byteArrayOf(1, 2, 3))

        assertEquals(16, tag.length)
        assertTrue(tag.all { it in "0123456789abcdef" })
    }

    @Test
    fun `objectPath matches the grammar`() {
        val path = AudioContentTag.objectPath("m1", "v1", "abc123")

        assertEquals("matns/m1/verses/v1-abc123.mp3", path)
    }
}
