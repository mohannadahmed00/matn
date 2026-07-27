package com.giraffe.matn.teacher

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.di.startTeacherKoin
import com.giraffe.matn.teacher.platform.JvmLanguagePreference
import com.giraffe.matn.teacher.presentation.strings.ArabicStrings
import com.giraffe.matn.teacher.presentation.strings.EnglishStrings
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

fun main() {
    TeacherKoinHolder.initialize(startTeacherKoin())
    val language = JvmLanguagePreference.load()
    val strings = if (language == TeacherLanguage.ARABIC) ArabicStrings else EnglishStrings

    application {
        Window(onCloseRequest = ::exitApplication, title = "Matn — Teacher") {
            MatnTheme(layoutDirection = language.layoutDirection) {
                CompositionLocalProvider(LocalTeacherStrings provides strings) {
                    // T047 (US1) replaces this with sign-in/portal routing.
                    Text(strings.appName)
                }
            }
        }
    }
}
