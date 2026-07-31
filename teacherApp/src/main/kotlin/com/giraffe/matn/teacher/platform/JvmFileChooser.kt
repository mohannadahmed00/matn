package com.giraffe.matn.teacher.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Distinguishes "user closed the dialog" from "picked something invalid" so the caller can show
 * the teacher a reason instead of silently doing nothing. */
sealed interface ImagePickResult {
    data class Picked(val bytes: ByteArray, val extension: String) : ImagePickResult
    data object Cancelled : ImagePickResult
    data object Rejected : ImagePickResult
}

/** A picked audio file's path and size, **not copied** (FR-018 for the split source; convenience
 * for per-verse attach too — the caller reads bytes only once it needs them). */
data class PickedFile(val path: String, val sizeBytes: Long)

/** [TooLarge] is split out from [Rejected] so the caller can name the actual limit rather than
 * showing one generic "invalid file" for two different problems. */
sealed interface AudioPickResult {
    data class Picked(val file: PickedFile) : AudioPickResult
    data object Cancelled : AudioPickResult
    data object Rejected : AudioPickResult
    data object TooLarge : AudioPickResult
}

/** FR-016 pre-checks: 5 MB ceiling, one of png/jpeg/webp for a cover image — the same limits
 * configured on the Supabase `matn-content` bucket (size limit + allowed MIME types), so a
 * rejection here is convenience, not the boundary. */
object JvmFileChooser {
    private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    private val ALLOWED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    /** FR-004/FR-004a: 10 MB ceiling for a **per-verse** recording. The extension filter is only
     * the first gate — a file merely named `.mp3` still has to survive header parsing downstream
     * (`AttachVerseAudioUseCase`); this chooser does not open the file to check. */
    const val MAX_PER_VERSE_AUDIO_BYTES = 10L * 1024 * 1024

    /** FR-004: 300 MB ceiling for a **split source**. Deliberately not the per-verse limit: a
     * continuous recording covering a whole matn is expected to dwarf any single verse, so
     * applying [MAX_PER_VERSE_AUDIO_BYTES] here would reject exactly the files this path exists
     * to accept. */
    const val MAX_SPLIT_SOURCE_BYTES = 300L * 1024 * 1024

    fun pickImage(): ImagePickResult {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Images (png, jpg, jpeg, webp)", "png", "jpg", "jpeg", "webp")
        }
        val file = showOpenDialog(chooser) ?: return ImagePickResult.Cancelled
        val extension = file.extension.lowercase()
        if (extension !in ALLOWED_IMAGE_EXTENSIONS) return ImagePickResult.Rejected
        val bytes = file.readBytes()
        if (bytes.size > MAX_IMAGE_BYTES) return ImagePickResult.Rejected
        return ImagePickResult.Picked(bytes, extension)
    }

    fun pickTextFile(): ByteArray? {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Text files (.txt)", "txt")
        }
        val file = showOpenDialog(chooser) ?: return null
        return file.readBytes()
    }

    /**
     * Picks an MP3, **without copying or reading its contents** — only path and size are returned
     * (FR-018 for the split source). [maxBytes] must be the ceiling for the path being used:
     * [MAX_PER_VERSE_AUDIO_BYTES] for a verse row, [MAX_SPLIT_SOURCE_BYTES] for a split source.
     * The two differ by 30×, so defaulting here would silently reject legitimate split sources.
     */
    fun pickAudio(maxBytes: Long): AudioPickResult {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Audio (mp3)", "mp3")
        }
        val file = showOpenDialog(chooser) ?: return AudioPickResult.Cancelled
        if (file.extension.lowercase() != "mp3") return AudioPickResult.Rejected
        val size = file.length()
        if (size > maxBytes) return AudioPickResult.TooLarge
        return AudioPickResult.Picked(PickedFile(path = file.absolutePath, sizeBytes = size))
    }

    private fun showOpenDialog(chooser: JFileChooser): File? =
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
