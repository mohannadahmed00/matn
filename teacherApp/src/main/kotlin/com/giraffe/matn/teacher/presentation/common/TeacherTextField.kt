package com.giraffe.matn.teacher.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage

/** Shared text field for `:teacherApp` (sign-in, editor metadata, chapter titles). */
@Composable
fun TeacherTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { text -> { Text(text) } },
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        shape = MatnShapes.lg,
    )
}

@Preview
@Composable
private fun TeacherTextFieldArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    TeacherTextField(value = "", onValueChange = {}, label = "عنوان المتن", placeholder = "مثال: الجزرية في التجويد")
}

@Preview
@Composable
private fun TeacherTextFieldEnglishErrorPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    TeacherTextField(value = "", onValueChange = {}, label = "Matn Title", isError = true)
}
