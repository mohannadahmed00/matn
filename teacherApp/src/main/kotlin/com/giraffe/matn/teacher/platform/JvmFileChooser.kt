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

/** FR-016 pre-checks: 5 MB ceiling, one of png/jpeg/webp for a cover image — the same limits
 * configured on the Supabase `matn-content` bucket (size limit + allowed MIME types), so a
 * rejection here is convenience, not the boundary. */
object JvmFileChooser {
    private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    private val ALLOWED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

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

    private fun showOpenDialog(chooser: JFileChooser): File? =
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
