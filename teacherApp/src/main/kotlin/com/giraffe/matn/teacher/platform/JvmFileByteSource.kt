package com.giraffe.matn.teacher.platform

import com.giraffe.matn.data.audio.ByteSource
import java.io.RandomAccessFile

/**
 * A [ByteSource] over [RandomAccessFile] — never reads the whole file into memory (`research.md`
 * D8). Deliberately does **not** wrap each read in its own `withContext(Dispatchers.IO)`: a frame
 * index performs one read per frame (hundreds to millions for a long recording), and a
 * dispatcher hop per 4-byte read is both wasted overhead and, when the calling coroutine is
 * already off the UI thread, unnecessary — the caller (`SplitViewModel`) wraps the whole loading
 * pipeline in one `withContext(Dispatchers.Default)` instead (`research.md` D7).
 */
class JvmFileByteSource(path: String) : ByteSource {
    private val file = RandomAccessFile(path, "r")

    override val size: Long get() = file.length()

    override suspend fun read(offset: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        file.seek(offset)
        file.readFully(bytes)
        return bytes
    }
}
