package com.giraffe.matn.teacher.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.teacher.presentation.strings.ArabicStrings
import com.giraffe.matn.teacher.presentation.strings.EnglishStrings
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** Shared `@Preview` wrapper: themed + localized, no ViewModel, no Koin, no network. */
@Composable
fun PreviewScaffold(language: TeacherLanguage, content: @Composable () -> Unit) {
    val strings = if (language == TeacherLanguage.ARABIC) ArabicStrings else EnglishStrings
    MatnTheme(layoutDirection = language.layoutDirection) {
        CompositionLocalProvider(LocalTeacherStrings provides strings) {
            content()
        }
    }
}
