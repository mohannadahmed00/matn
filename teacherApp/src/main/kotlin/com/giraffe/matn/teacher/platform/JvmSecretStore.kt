package com.giraffe.matn.teacher.platform

import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.domain.secret.SecretStore
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import org.koin.core.annotation.Single
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * [SecretStore] dispatched per OS (research D7, FR-003a): Windows DPAPI via `jna-platform`'s
 * `Crypt32Util`, macOS `security(1)`, Linux `secret-tool` when present on `PATH`, and otherwise an
 * owner-readable file with [isProtected] `false` — the UI surfaces that as a visible warning.
 *
 * A `:teacherApp`-native class, so `@Single` here is safe (Ground Rule 11 only forbids annotating
 * teacher-side classes that live in `:shared`, where `ContentModule`'s `@ComponentScan` would pull
 * them into the student apps' graph).
 */
@Single(binds = [SecretStore::class])
class JvmSecretStore : SecretStore {

    override val isProtected: Boolean = Platform.isWindows() || Platform.isMac() || linuxSecretToolAvailable()

    override suspend fun put(key: String, value: String): Resource<Unit> = runCatching {
        when {
            Platform.isWindows() -> putWindows(key, value)
            Platform.isMac() -> putMac(key, value)
            linuxSecretToolAvailable() -> putLinuxSecretTool(key, value)
            else -> putFallbackFile(key, value)
        }
        Resource.Success(Unit)
    }.getOrElse { failure(key, "store", it) }

    override suspend fun get(key: String): Resource<String?> = runCatching {
        val value = when {
            Platform.isWindows() -> getWindows(key)
            Platform.isMac() -> getMac(key)
            linuxSecretToolAvailable() -> getLinuxSecretTool(key)
            else -> getFallbackFile(key)
        }
        Resource.Success(value)
    }.getOrElse { failure(key, "read", it) }

    override suspend fun clear(key: String): Resource<Unit> = runCatching {
        when {
            Platform.isWindows() -> fileFor(key).delete()
            Platform.isMac() -> clearMac(key)
            linuxSecretToolAvailable() -> clearLinuxSecretTool(key)
            else -> fileFor(key).delete()
        }
        Resource.Success(Unit)
    }.getOrElse { failure(key, "clear", it) }

    private fun <T> failure(key: String, action: String, t: Throwable): Resource<T> =
        Resource.Failure(AppError.Storage(t.message ?: "Failed to $action secret '$key'"))

    // ---- Windows: DPAPI, ciphertext file in the app-data directory ----

    private fun fileFor(key: String): File = File(AppDataDir.path, "$key.secret")

    private fun putWindows(key: String, value: String) {
        val encrypted = Crypt32Util.cryptProtectData(value.toByteArray(Charsets.UTF_8))
        fileFor(key).writeBytes(encrypted)
    }

    private fun getWindows(key: String): String? {
        val file = fileFor(key)
        if (!file.exists()) return null
        return Crypt32Util.cryptUnprotectData(file.readBytes()).toString(Charsets.UTF_8)
    }

    // ---- macOS: security(1) generic-password keychain items ----

    private fun serviceName(key: String) = "matn-teacher-$key"
    private fun accountName() = System.getProperty("user.name") ?: "teacher"

    private fun putMac(key: String, value: String) {
        ProcessBuilder(
            "security", "add-generic-password",
            "-a", accountName(), "-s", serviceName(key), "-w", value, "-U",
        ).start().waitFor()
    }

    private fun getMac(key: String): String? {
        val process = ProcessBuilder(
            "security", "find-generic-password",
            "-a", accountName(), "-s", serviceName(key), "-w",
        ).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        return if (process.waitFor() == 0) output else null
    }

    private fun clearMac(key: String) {
        ProcessBuilder("security", "delete-generic-password", "-a", accountName(), "-s", serviceName(key)).start().waitFor()
    }

    // ---- Linux: secret-tool (libsecret), when present on PATH ----

    private fun linuxSecretToolAvailable(): Boolean {
        if (!Platform.isLinux()) return false
        return runCatching { ProcessBuilder("which", "secret-tool").start().waitFor() == 0 }.getOrDefault(false)
    }

    private fun putLinuxSecretTool(key: String, value: String) {
        val process = ProcessBuilder(
            "secret-tool", "store", "--label=Matn Teacher ($key)", "service", "matn-teacher", "account", key,
        ).start()
        process.outputStream.use { it.write(value.toByteArray(Charsets.UTF_8)) }
        process.waitFor()
    }

    private fun getLinuxSecretTool(key: String): String? {
        val process = ProcessBuilder("secret-tool", "lookup", "service", "matn-teacher", "account", key).start()
        val output = process.inputStream.bufferedReader().readText()
        return if (process.waitFor() == 0) output.ifEmpty { null } else null
    }

    private fun clearLinuxSecretTool(key: String) {
        ProcessBuilder("secret-tool", "clear", "service", "matn-teacher", "account", key).start().waitFor()
    }

    // ---- Fallback: owner-readable plaintext file (isProtected = false) ----

    private fun putFallbackFile(key: String, value: String) {
        val file = fileFor(key)
        file.writeText(value, Charsets.UTF_8)
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }

    private fun getFallbackFile(key: String): String? {
        val file = fileFor(key)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }
}
