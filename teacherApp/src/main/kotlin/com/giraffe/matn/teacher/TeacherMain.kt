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
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase
import com.giraffe.matn.domain.usecase.RestoreSessionUseCase
import com.giraffe.matn.domain.usecase.SignOutUseCase
import com.giraffe.matn.presentation.common.formatBytes
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.di.startTeacherKoin
import com.giraffe.matn.teacher.platform.JvmLanguagePreference
import com.giraffe.matn.teacher.presentation.editor.EditorScreen
import com.giraffe.matn.teacher.presentation.library.LibraryScreen
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

/** Informational display cap only — no quota is enforced server-side beyond what Firebase itself
 * rejects (Assumptions, `contracts/rest-contract.md` §5.2). */
private const val STORAGE_DISPLAY_CAP_BYTES = 10_000_000_000L

@Composable
private fun TeacherApp() {
    val koin = TeacherKoinHolder.koin
    val authRepository: TeacherAuthRepository = remember { koin.get() }
    val restoreSession: RestoreSessionUseCase = remember { koin.get() }
    val signOut: SignOutUseCase = remember { koin.get() }
    val loadMatnForEdit: LoadMatnForEditUseCase = remember { koin.get() }
    val storageClient: StorageRestClient = remember { koin.get() }
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf(JvmLanguagePreference.load()) }
    val strings = if (language == TeacherLanguage.ARABIC) ArabicStrings else EnglishStrings

    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        restoreSession(Unit)
        restored = true
    }
    val session by authRepository.observeSession().collectAsState(initial = null)

    var destination by remember { mutableStateOf(PortalDestination.UPLOAD_MATN) }
    var editingDraft by remember { mutableStateOf<MatnDraft?>(null) }
    var isLoadingEditingDraft by remember { mutableStateOf(false) }
    var storageUsageBytes by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(session?.uid) {
        if (session == null) return@LaunchedEffect
        // A failed usage read shows nothing — it is informational only (T086).
        val result = storageClient.totalUsageBytes("matns/")
        storageUsageBytes = (result as? Resource.Success)?.data
    }

    MatnTheme(layoutDirection = language.layoutDirection) {
        CompositionLocalProvider(LocalTeacherStrings provides strings) {
            val currentSession = session
            when {
                !restored -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                currentSession == null -> SignInScreen()
                else -> {
                    val usageBytes = storageUsageBytes
                    PortalShellContent(
                        state = PortalShellState(
                            displayName = currentSession.displayName.ifBlank { currentSession.email },
                            selectedDestination = destination,
                            storageUsageText = usageBytes?.let { "${formatBytes(it)} / ${formatBytes(STORAGE_DISPLAY_CAP_BYTES)}" },
                            storageUsageFraction = usageBytes?.let { (it.toFloat() / STORAGE_DISPLAY_CAP_BYTES).coerceIn(0f, 1f) } ?: 0f,
                        ),
                        onDestinationSelected = { destination = it },
                        onLanguageToggle = {
                            language = if (language == TeacherLanguage.ARABIC) TeacherLanguage.ENGLISH else TeacherLanguage.ARABIC
                            JvmLanguagePreference.save(language)
                        },
                        onSignOut = { scope.launch { signOut(Unit) } },
                    ) {
                        when (destination) {
                            PortalDestination.UPLOAD_MATN -> if (isLoadingEditingDraft) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                            } else {
                                editingDraft?.let { EditorScreen(initialDraft = it) } ?: EditorScreen()
                            }
                            PortalDestination.LIBRARY_MANAGEMENT -> LibraryScreen(
                                onOpenMatn = { matnId ->
                                    scope.launch {
                                        isLoadingEditingDraft = true
                                        when (val result = loadMatnForEdit(matnId)) {
                                            is Resource.Success -> {
                                                editingDraft = result.data
                                                destination = PortalDestination.UPLOAD_MATN
                                            }
                                            is Resource.Failure -> Unit
                                        }
                                        isLoadingEditingDraft = false
                                    }
                                },
                                onCreateNew = {
                                    editingDraft = null
                                    destination = PortalDestination.UPLOAD_MATN
                                },
                            )
                            else -> Text(destination.name)
                        }
                    }
                }
            }
        }
    }
}
