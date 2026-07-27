package com.giraffe.matn.teacher.platform

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** FR-016 pre-checks: 5 MB ceiling, one of png/jpeg/webp for a cover image — the same limits the
 * Storage rules enforce server-side (`contracts/security-rules.md` §2), so a rejection here is
 * convenience, not the boundary. */
object JvmFileChooser {
    private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    private val ALLOWED_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun pickImage(): ByteArray? {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Images (png, jpg, jpeg, webp)", "png", "jpg", "jpeg", "webp")
        }
        val file = showOpenDialog(chooser) ?: return null
        if (file.extension.lowercase() !in ALLOWED_IMAGE_EXTENSIONS) return null
        val bytes = file.readBytes()
        if (bytes.size > MAX_IMAGE_BYTES) return null
        return bytes
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
