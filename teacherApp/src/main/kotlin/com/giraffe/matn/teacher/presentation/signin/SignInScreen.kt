package com.giraffe.matn.teacher.presentation.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.secret.SecretStore
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.presentation.common.PreviewScaffold
import com.giraffe.matn.teacher.presentation.common.TeacherTextField
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import com.giraffe.matn.teacher.presentation.strings.messageFor

/** `contracts/teacher-ui-contract.md` §3.1. */
@Composable
fun SignInContent(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalTeacherStrings.current
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(MatnSpacing.gutter),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = MatnSpacing.surfaceMaxWidth),
                verticalArrangement = Arrangement.spacedBy(MatnSpacing.unit * 2),
            ) {
                Text(text = strings.signInTitle, style = MaterialTheme.typography.headlineSmall)

                if (state.secretStoreUnprotected) {
                    Text(
                        text = strings.secretStoreUnprotectedWarning,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                TeacherTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = strings.emailLabel,
                    enabled = !state.isSubmitting,
                )
                TeacherTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = strings.passwordLabel,
                    isPassword = true,
                    enabled = !state.isSubmitting,
                )

                state.error?.let { error ->
                    Text(
                        text = strings.messageFor(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmitting && state.email.isNotBlank() && state.password.isNotBlank(),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(MatnSpacing.unit * 2))
                    } else {
                        Text(if (state.isSubmitting) strings.signingIn else strings.signInButton)
                    }
                }
            }
        }
    }
}

@Composable
fun SignInScreen(modifier: Modifier = Modifier) {
    val koin = TeacherKoinHolder.koin
    val viewModel: SignInViewModel = viewModel {
        SignInViewModel(
            signIn = koin.get(),
            secretStoreUnprotected = !koin.get<SecretStore>().isProtected,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignInContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSubmit = viewModel::onSubmit,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun SignInIdleArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    SignInContent(SignInUiState(), {}, {}, {})
}

@Preview
@Composable
private fun SignInIdleEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    SignInContent(SignInUiState(), {}, {}, {})
}

@Preview
@Composable
private fun SignInErrorArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    SignInContent(SignInUiState(email = "a@b.com", error = RemoteError.Unauthorized), {}, {}, {})
}

@Preview
@Composable
private fun SignInErrorEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    SignInContent(SignInUiState(email = "a@b.com", error = RemoteError.Unauthorized), {}, {}, {})
}

@Preview
@Composable
private fun SignInUnprotectedStoreArabicPreview() = PreviewScaffold(TeacherLanguage.ARABIC) {
    SignInContent(SignInUiState(secretStoreUnprotected = true), {}, {}, {})
}

@Preview
@Composable
private fun SignInUnprotectedStoreEnglishPreview() = PreviewScaffold(TeacherLanguage.ENGLISH) {
    SignInContent(SignInUiState(secretStoreUnprotected = true), {}, {}, {})
}
