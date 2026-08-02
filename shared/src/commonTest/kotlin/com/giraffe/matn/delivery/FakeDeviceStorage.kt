package com.giraffe.matn.delivery

import com.giraffe.matn.domain.delivery.DeviceStorage

/**
 * In-memory fake of [DeviceStorage] for `commonTest` (T023 / Principle V). No real IO.
 * [freeSpace] defaults to "effectively infinite" so a test that does not care about disk
 * pressure is not forced to set it; tests that do care can pin it to a small value.
 */
class FakeDeviceStorage : DeviceStorage {

    var freeSpace: Long = Long.MAX_VALUE

    val directorySizes: MutableMap<String, Long> = mutableMapOf()

    /**
     * Phase 13: the fake content root. Settable so a test that touches the real filesystem
     * (via `ContentFileStore`) can point it at a per-test temporary directory and stay isolated.
     */
    var contentRoot: String = "/fake-content-root"

    fun reset() {
        freeSpace = Long.MAX_VALUE
        directorySizes.clear()
        contentRoot = "/fake-content-root"
    }

    override suspend fun freeSpaceBytes(): Long = freeSpace

    override suspend fun sizeOfDirectory(path: String): Long = directorySizes[path] ?: 0L

    override suspend fun contentRootPath(): String = contentRoot
}