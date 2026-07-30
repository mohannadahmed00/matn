package com.giraffe.matn.teacher.platform

import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import java.io.File
import java.util.Properties

/** Persists the chosen [TeacherLanguage] in a properties file under the OS app-data directory. */
object JvmLanguagePreference {
    private const val KEY = "language"
    private val file: File get() = File(AppDataDir.path, "preferences.properties")

    fun load(): TeacherLanguage {
        if (!file.exists()) return TeacherLanguage.ARABIC
        val props = Properties().apply { file.inputStream().use { load(it) } }
        return runCatching {
            TeacherLanguage.valueOf(props.getProperty(KEY, TeacherLanguage.ARABIC.name))
        }.getOrDefault(TeacherLanguage.ARABIC)
    }

    fun save(language: TeacherLanguage) {
        val props = Properties()
        if (file.exists()) file.inputStream().use { props.load(it) }
        props.setProperty(KEY, language.name)
        file.outputStream().use { props.store(it, null) }
    }
}
