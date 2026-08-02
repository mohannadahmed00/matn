package com.giraffe.matn.delivery

import android.content.Context
import android.os.StatFs
import com.giraffe.matn.domain.delivery.DeviceStorage
import java.io.File

/**
 * Android adapter implementing [DeviceStorage] (content-delivery-contract.md §2 / T026).
 * **No business logic** (Constitution IV): this file only translates platform primitives into the
 * domain interface. Free-space uses `StatFs` on the app's files directory; directory size is a
 * recursive walk. The free-space precondition and zero-state rule live in the use cases / repo.
 *
 * Construction mirrors [com.giraffe.matn.data.db.DatabaseDriverFactory] (it accepts the application
 * context and does nothing else); the only "decision" made here is choosing which path to inspect,
 * which is the platform's own convention.
 */
class AndroidDeviceStorage(private val context: Context) : DeviceStorage {

    override suspend fun freeSpaceBytes(): Long {
        val stats = StatFs(context.filesDir.absolutePath)
        return stats.availableBlocksLong * stats.blockSizeLong
    }

    override suspend fun sizeOfDirectory(path: String): Long {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    override suspend fun contentRootPath(): String = context.filesDir.absolutePath
}