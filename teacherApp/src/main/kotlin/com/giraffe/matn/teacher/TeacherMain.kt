package com.giraffe.matn.teacher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.usecase.RestoreSessionUseCase
import com.giraffe.matn.domain.usecase.SignOutUseCase
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.di.startTeacherKoin
import com.giraffe.matn.teacher.platform.JvmLanguagePreference
import com.giraffe.matn.teacher.presentation.shell.PortalDestination
import com.giraffe.matn.teacher.presentation.shell.PortalShellContent
import com.giraffe.matn.teacher.presentation.shell.PortalShellState
import com.giraffe.matn.teacher.presentation.signin.SignInScreen
import com.giraffe.matn.teacher.presentation.strings.ArabicStrings
import com.giraffe.matn.teacher.presentation.strings.EnglishStrings
import com.giraffe.matn.teacher.presentation.strings.LocalTeacherStrings
import com.giraffe.matn.teacher.presentation.strings.TeacherLanguage
import kotlinx.coroutines.launch

fun main() {
    TeacherKoinHolder.initialize(startTeacherKoin())
    application {
        Window(onCloseRequest = ::exitApplication, title = "Matn — Teacher") {
            TeacherApp()
        }
    }
}

@Composable
private fun TeacherApp() {
    val koin = TeacherKoinHolder.koin
    val authRepository: TeacherAuthRepository = remember { koin.get() }
    val restoreSession: RestoreSessionUseCase = remember { koin.get() }
    val signOut: SignOutUseCase = remember { koin.get() }
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf(JvmLanguagePreference.load()) }
    val strings = if (language == TeacherLanguage.ARABIC) ArabicStrings else EnglishStrings

    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        restoreSession(Unit)
        restored = true
    }
    val session by authRepository.observeSession().collectAsState(initial = null)

    MatnTheme(layoutDirection = language.layoutDirection) {
        CompositionLocalProvider(LocalTeacherStrings provides strings) {
            val currentSession = session
            when {
                !restored -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                currentSession == null -> SignInScreen()
                else -> {
                    var destination by remember { mutableStateOf(PortalDestination.UPLOAD_MATN) }
                    PortalShellContent(
                        state = PortalShellState(
                            displayName = currentSession.displayName.ifBlank { currentSession.email },
                            selectedDestination = destination,
                            storageUsageText = null, // T086 wires the real figure
                            storageUsageFraction = 0f,
                        ),
                        onDestinationSelected = { destination = it },
                        onLanguageToggle = {
                            language = if (language == TeacherLanguage.ARABIC) TeacherLanguage.ENGLISH else TeacherLanguage.ARABIC
                            JvmLanguagePreference.save(language)
                        },
                        onSignOut = { scope.launch { signOut(Unit) } },
                    ) {
                        // T058/T059/T086 replace this per destination.
                        Text("${destination.name}")
                    }
                }
            }
        }
    }
}
