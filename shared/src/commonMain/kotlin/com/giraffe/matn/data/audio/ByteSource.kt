package com.giraffe.matn.data.audio

/**
 * A read-a-range abstraction over a (possibly large) MP3 file, so frame indexing and slicing never
 * need the whole file in memory (`research.md` D8). The JVM implementation is `RandomAccessFile`
 * backed; this in-memory one is for `commonTest`.
 */
interface ByteSource {
    val size: Long
    suspend fun read(offset: Long, length: Int): ByteArray
}

class ByteArrayByteSource(private val bytes: ByteArray) : ByteSource {
    override val size: Long get() = bytes.size.toLong()

    override suspend fun read(offset: Long, length: Int): ByteArray {
        val start = offset.toInt()
        return bytes.copyOfRange(start, start + length)
    }
}
