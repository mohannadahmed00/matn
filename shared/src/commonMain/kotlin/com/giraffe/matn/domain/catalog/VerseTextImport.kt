package com.giraffe.matn.domain.catalog

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource

data class ImportPreview(
    val lines: List<String>,
    /** Reserved for a per-line problem a plain UTF-8, no-delimiter format doesn't currently
     * produce — an invalid byte anywhere rejects the whole file (below) rather than flagging a
     * single line, so this is always empty in Phase 11. */
    val problemLineNumbers: List<Int>,
)

/**
 * FR-023a/FR-023b: plain UTF-8 text, one verse per line. **No delimiter, no CSV, no quoting** —
 * a line's entire content is the verse text, verbatim.
 */
object VerseTextImport {
    private const val BOM = '﻿'

    fun parse(bytes: ByteArray): Resource<ImportPreview> {
        val decoded = bytes.decodeUtf8Strict()
            ?: return Resource.Failure(AppError.Storage("The file is not valid UTF-8 text"))
        val withoutBom = if (decoded.startsWith(BOM)) decoded.substring(1) else decoded
        val lines = splitLines(withoutBom).filterNot { it.isBlank() }
        return Resource.Success(ImportPreview(lines = lines, problemLineNumbers = emptyList()))
    }

    private fun splitLines(text: String): List<String> =
        text.split("\r\n").flatMap { it.split("\n") }
}

/** A hand-rolled, strictly-validating UTF-8 decoder — `commonMain` has no strict decoder in the
 * stdlib (`String(bytes)` silently replaces invalid sequences instead of failing). Returns `null`
 * on any malformed, overlong, or out-of-range sequence. */
private fun ByteArray.decodeUtf8Strict(): String? {
    val sb = StringBuilder()
    var i = 0
    while (i < size) {
        val b0 = this[i].toInt() and 0xFF
        when {
            b0 and 0x80 == 0x00 -> {
                sb.append(b0.toChar())
                i += 1
            }
            b0 and 0xE0 == 0xC0 -> {
                if (i + 1 >= size) return null
                val b1 = this[i + 1].toInt() and 0xFF
                if (b1 and 0xC0 != 0x80) return null
                val cp = ((b0 and 0x1F) shl 6) or (b1 and 0x3F)
                if (cp < 0x80) return null
                sb.append(cp.toChar())
                i += 2
            }
            b0 and 0xF0 == 0xE0 -> {
                if (i + 2 >= size) return null
                val b1 = this[i + 1].toInt() and 0xFF
                val b2 = this[i + 2].toInt() and 0xFF
                if (b1 and 0xC0 != 0x80 || b2 and 0xC0 != 0x80) return null
                val cp = ((b0 and 0x0F) shl 12) or ((b1 and 0x3F) shl 6) or (b2 and 0x3F)
                if (cp < 0x800 || cp in 0xD800..0xDFFF) return null
                sb.append(cp.toChar())
                i += 3
            }
            b0 and 0xF8 == 0xF0 -> {
                if (i + 3 >= size) return null
                val b1 = this[i + 1].toInt() and 0xFF
                val b2 = this[i + 2].toInt() and 0xFF
                val b3 = this[i + 3].toInt() and 0xFF
                if (b1 and 0xC0 != 0x80 || b2 and 0xC0 != 0x80 || b3 and 0xC0 != 0x80) return null
                val cp = ((b0 and 0x07) shl 18) or ((b1 and 0x3F) shl 12) or ((b2 and 0x3F) shl 6) or (b3 and 0x3F)
                if (cp < 0x10000 || cp > 0x10FFFF) return null
                val adjusted = cp - 0x10000
                sb.append((0xD800 + (adjusted shr 10)).toChar())
                sb.append((0xDC00 + (adjusted and 0x3FF)).toChar())
                i += 4
            }
            else -> return null
        }
    }
    return sb.toString()
}
