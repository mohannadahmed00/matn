package com.giraffe.matn.teacher.platform

import java.io.File

/** The OS-appropriate per-user application-data directory for `:teacherApp`, created if absent. */
object AppDataDir {
    val path: File by lazy {
        val home = System.getProperty("user.home")
        val osName = System.getProperty("os.name").lowercase()
        val dir = when {
            osName.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "MatnTeacher")
            osName.contains("mac") -> File("$home/Library/Application Support", "MatnTeacher")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "matn-teacher")
        }
        dir.apply { mkdirs() }
    }
}
