package com.giraffe.matn.teacher.presentation.signin

import com.giraffe.matn.domain.error.RemoteError
import com.giraffe.matn.domain.usecase.SignInUseCase
import com.giraffe.matn.presentation.base.BaseViewModel

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: RemoteError? = null,
    val secretStoreUnprotected: Boolean = false,
)

/** `contracts/teacher-ui-contract.md` §3.1. */
class SignInViewModel(
    private val signIn: SignInUseCase,
    secretStoreUnprotected: Boolean,
) : BaseViewModel<SignInUiState>(SignInUiState(secretStoreUnprotected = secretStoreUnprotected)) {

    fun onEmailChange(value: String) = setState { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = setState { it.copy(password = value, error = null) }

    fun onSubmit() {
        val current = stateValue
        if (current.email.isBlank() || current.password.isBlank() || current.isSubmitting) return
        setState { it.copy(isSubmitting = true, error = null) }
        runUseCase(
            useCase = signIn,
            params = SignInUseCase.Params(current.email, current.password),
            onSuccess = { setState { it.copy(isSubmitting = false) } },
            onError = { error ->
                setState { it.copy(isSubmitting = false, error = error as? RemoteError ?: RemoteError.Decode) }
            },
        )
    }
}
