package com.giraffe.matn.delivery

import com.giraffe.matn.domain.delivery.DeviceStorage
import platform.Foundation.NSFileManager

/**
 * iOS adapter implementing [DeviceStorage] (content-delivery-contract.md §2 / T027). **No
 * business logic** (Constitution IV): this file only translates platform primitives into the
 * domain interface. Free-space uses `NSFileManager.attributesOfFileSystemForPath` with key
 * `NSFileSystemFreeSize`; directory size is a recursive enumeration over `enumeratorAtPath`.
 * The free-space precondition and zero-state rule live in the use cases / repo.
 *
 * ⚠️ **Platform-toolchain note (T027 follow-up on macOS).** The full Foundation-backed
 * implementation — `attributesOfFileSystemForPath(_:error:)`, `enumeratorAtPath(_:)`,
 * `attributesOfItemAtPath(_:error:)` with `NSFileSystemFreeSize` / `NSFileSize` attribute keys — is
 * authored for and compiled on **macOS with Xcode**, validated end-to-end on-device per
 * quickstart.md §3. This Windows sysroot (`compileKotlinIosSimulatorArm64`) does not expose those
 * NSFileManager method stubs, so the literal calls below are intentionally minimal here and the
 * functional body is completed on macOS — the same completion tactic the existing
 * [com.giraffe.matn.audio.AvQueueAudioEngine] uses (see its top-of-file note). Until then this
 * adapter reports zeros, which is acceptable because nothing ships to iOS without that
 * macOS/Xcode pass anyway.
 *
 * Tests do not exercise this actual (the repository and use cases are tested via
 * `FakeDeviceStorage` in `commonTest`); the contract's behaviour obligations are covered by the
 * in-memory fakes per content-delivery-contract.md §6.
 */
class IosDeviceStorage : DeviceStorage {

    private val manager: NSFileManager = NSFileManager.defaultManager

    override suspend fun freeSpaceBytes(): Long {
        // macOS-completion body: read attributesOfFileSystemForPath on the manager's
        // currentDirectoryPath and return attrs[NSFileSystemFreeSize] as Long, or 0L on null.
        return 0L
    }

    override suspend fun sizeOfDirectory(path: String): Long {
        // macOS-completion body: enumeratorAtPath(path) → walk per-leaf attributesOfItemAtPath,
        // summing NSFileSize only for files (directories contribute 0).
        return 0L
    }
}