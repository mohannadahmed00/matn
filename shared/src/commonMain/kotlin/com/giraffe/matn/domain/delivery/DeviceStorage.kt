package com.giraffe.matn.domain.delivery

/**
 * The seam between the shared storage logic and the platform's filesystem inquiry API
 * (content-delivery-contract.md §2). Android uses `StatFs` + a recursive walk; iOS uses
 * `NSFileManager`; both are faked in `commonTest` (research D8). No business logic in either
 * actual — the free-space precondition lives in `InstallMatnContentUseCase` (Principle IV).
 */
interface DeviceStorage {

    /** Bytes of free space remaining on the device. Used by the install precondition (FR-007). */
    suspend fun freeSpaceBytes(): Long

    /** Total on-disk size of the directory at [path]; 0 when the path is absent. */
    suspend fun sizeOfDirectory(path: String): Long
}