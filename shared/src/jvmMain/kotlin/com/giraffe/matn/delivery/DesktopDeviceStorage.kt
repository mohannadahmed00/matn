package com.giraffe.matn.delivery

import com.giraffe.matn.domain.delivery.DeviceStorage
import java.io.File
import java.nio.file.Files

/**
 * Desktop adapter implementing [DeviceStorage] (content-delivery-contract.md §2). Free-space uses
 * [Files.getFileStore] on the user's home directory; directory size is a recursive walk — the same
 * shape as [com.giraffe.matn.delivery.AndroidDeviceStorage].
 */
class DesktopDeviceStorage : DeviceStorage {

    override suspend fun freeSpaceBytes(): Long {
        val home = File(System.getProperty("user.home"))
        return Files.getFileStore(home.toPath()).usableSpace
    }

    override suspend fun sizeOfDirectory(path: String): Long {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
