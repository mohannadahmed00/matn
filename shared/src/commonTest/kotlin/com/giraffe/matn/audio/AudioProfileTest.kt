package com.giraffe.matn.audio

import com.giraffe.matn.domain.audio.AudioProfile
import com.giraffe.matn.domain.audio.audioProfile
import com.giraffe.matn.domain.catalog.DraftAudio
import com.giraffe.matn.domain.catalog.DraftVerse
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.PublicationState
import com.giraffe.matn.domain.model.StructureKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun audio(id: String, sampleRate: Int = 44100, channels: Int = 1, sizeBytes: Long = 1000) =
    DraftAudio(id = id, fileRef = "matns/m1/verses/$id-tag.mp3", durationMs = 1000, sizeBytes = sizeBytes, sampleRate = sampleRate, channels = channels)

private fun verse(id: String, audio: DraftAudio? = null) =
    DraftVerse(id = id, chapterId = null, displayNumber = 1, arabicText = "text", audio = audio, durationMs = audio?.durationMs ?: 0L)

private fun matn(verses: List<DraftVerse>) = MatnDraft(
    id = "m1",
    title = "t",
    author = "a",
    description = "d",
    coverImageRef = null,
    structureKind = StructureKind.SIMPLE,
    defaultReciterId = "r1",
    chapters = emptyList(),
    verses = verses,
    publicationState = PublicationState.DRAFT,
    createdAt = 0L,
    updatedAt = 0L,
    remoteRevision = null,
)

class AudioProfileTest {

    @Test
    fun `an empty matn has a null profile`() {
        assertNull(matn(emptyList()).audioProfile())
    }

    @Test
    fun `the first audio-bearing verse establishes the profile`() {
        val draft = matn(listOf(verse("v1"), verse("v2", audio("a2", sampleRate = 48000, channels = 2))))

        assertEquals(AudioProfile(48000, 2), draft.audioProfile())
    }

    @Test
    fun `a second verse with a different profile is detectable as a mismatch`() {
        val draft = matn(
            listOf(
                verse("v1", audio("a1", sampleRate = 44100, channels = 1)),
                verse("v2", audio("a2", sampleRate = 48000, channels = 2)),
            ),
        )

        val profile = draft.audioProfile()
        val secondVerseProfile = AudioProfile(draft.verses[1].audio!!.sampleRate, draft.verses[1].audio!!.channels)
        assertEquals(AudioProfile(44100, 1), profile)
        assert(profile != secondVerseProfile)
    }

    @Test
    fun `declaredSizeBytes grows by the audio's sizeBytes`() {
        val withoutAudio = matn(listOf(verse("v1")))
        val withAudio = matn(listOf(verse("v1", audio("a1", sizeBytes = 5000))))

        assertEquals(withoutAudio.declaredSizeBytes + 5000, withAudio.declaredSizeBytes)
    }

    @Test
    fun `removing all audio clears the matn's profile`() {
        val draft = matn(listOf(verse("v1", audio("a1"))))

        val cleared = draft.copy(verses = draft.verses.map { it.copy(audio = null) })

        assertNull(cleared.audioProfile())
    }
}
