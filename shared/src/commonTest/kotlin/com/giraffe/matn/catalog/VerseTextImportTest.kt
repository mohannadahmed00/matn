package com.giraffe.matn.catalog

import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.catalog.VerseTextImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VerseTextImportTest {

    @Test
    fun `blank lines are skipped without being reported as problems`() {
        val bytes = "line one\n\nline two\n   \nline three".encodeToByteArray()

        val result = VerseTextImport.parse(bytes) as Resource.Success

        assertEquals(listOf("line one", "line two", "line three"), result.data.lines)
        assertTrue(result.data.problemLineNumbers.isEmpty())
    }

    @Test
    fun `both LF and CRLF line endings are handled`() {
        val bytes = "a\r\nb\nc\r\nd".encodeToByteArray()

        val result = VerseTextImport.parse(bytes) as Resource.Success

        assertEquals(listOf("a", "b", "c", "d"), result.data.lines)
    }

    @Test
    fun `a leading BOM is stripped`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "first line\nsecond line".encodeToByteArray()

        val result = VerseTextImport.parse(bytes) as Resource.Success

        assertEquals(listOf("first line", "second line"), result.data.lines)
    }

    @Test
    fun `non-UTF-8 bytes are rejected`() {
        val invalid = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00)

        val result = VerseTextImport.parse(invalid)

        assertTrue(result is Resource.Failure)
    }

    @Test
    fun `a line with commas and quotation marks is preserved verbatim - never split`() {
        val line = "hello, \"world\", again"

        val result = VerseTextImport.parse(line.encodeToByteArray()) as Resource.Success

        assertEquals(listOf(line), result.data.lines)
    }

    @Test
    fun `Arabic text with diacritics round-trips through the decoder untouched`() {
        val verse = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"

        val result = VerseTextImport.parse(verse.encodeToByteArray()) as Resource.Success

        assertEquals(listOf(verse), result.data.lines)
    }
}
