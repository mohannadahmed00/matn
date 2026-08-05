package com.giraffe.matn.teacher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.giraffe.matn.core.AppError
import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.catalog.MatnDraft
import com.giraffe.matn.domain.catalog.MatnDraftFactory
import com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase
import com.giraffe.matn.domain.usecase.RestoreSessionUseCase
import com.giraffe.matn.domain.usecase.SignOutUseCase
import com.giraffe.matn.presentation.common.formatBytes
import com.giraffe.matn.presentation.theme.MatnSpacing
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.teacher.di.TeacherKoinHolder
import com.giraffe.matn.teacher.di.startTeacherKoin
import com.giraffe.matn.teacher.platform.JvmLanguagePreference
import com.giraffe.matn.teacher.presentation.common.rememberScopedViewModelStoreOwner
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
import com.giraffe.matn.teacher.presentation.strings.messageFor
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
    TeacherKoinHolder.initialize(startTeacherKoin())
    application {
        Window(onCloseRequest = ::exitApplication, title = "Matn — Teacher") {
            TeacherApp()
        }
    }
}

/** Informational display cap only — no quota is enforced server-side beyond what Supabase itself
 * rejects (Assumptions, `contracts/rest-contract.md` §5.2). */
private const val STORAGE_DISPLAY_CAP_BYTES = 10_000_000_000L

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
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
    /** Bumped once per *open* — reopening from the library, or starting a new matn. Everything else
     * (typing, switching destinations, recomposing) leaves it alone, so work in progress survives.
     * See [rememberScopedViewModelStoreOwner] for why an "open" and not the matn's id is the right
     * identity here. */
    var editorEpoch by remember { mutableStateOf(0) }
    var openMatnError by remember { mutableStateOf<AppError?>(null) }
    var storageUsageBytes by remember { mutableStateOf<Long?>(null) }

    val editorStoreOwner = rememberScopedViewModelStoreOwner(editorEpoch)
    // Hoisted out of `EditorScreen`'s default argument and keyed to the epoch: a blank draft must
    // be minted once per "new matn", not once per composition and not shared between two opens.
    val draftForEditor = remember(editorEpoch) {
        editingDraft ?: MatnDraftFactory.newDraft(
            newId = { Uuid.random().toString() },
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }

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
                            // "used / cap" — the order is the meaning, so the pair is isolated as
                            // one run; the teacher interface can be Arabic, which would otherwise
                            // resolve the neutral separator right-to-left and swap the figures.
                            storageUsageText = usageBytes?.let {
                                com.giraffe.matn.presentation.common.ltrIsolated(
                                    "${formatBytes(it)} / ${formatBytes(STORAGE_DISPLAY_CAP_BYTES)}",
                                )
                            },
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
                                // One call site, one store scoped to this open. Two call sites and a
                                // process-wide store is what let a stale editor outlive the matn it
                                // belonged to.
                                CompositionLocalProvider(LocalViewModelStoreOwner provides editorStoreOwner) {
                                    EditorScreen(
                                        initialDraft = draftForEditor,
                                        // Saved or published means done with this matn: hand back a
                                        // blank editor so the next one can be started immediately.
                                        // The work is on the server and listed in the library, so
                                        // nothing is lost by clearing the screen.
                                        onFinished = {
                                            editingDraft = null
                                            editorEpoch++
                                        },
                                    )
                                }
                            }
                            PortalDestination.LIBRARY_MANAGEMENT -> Column(Modifier.fillMaxSize()) {
                                // A failed open used to be swallowed entirely, leaving the click
                                // looking like a dead button.
                                openMatnError?.let { error ->
                                    Text(
                                        text = strings.messageFor(error),
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(MatnSpacing.gutter),
                                    )
                                }
                                LibraryScreen(
                                    onOpenMatn = { matnId ->
                                        scope.launch {
                                            isLoadingEditingDraft = true
                                            openMatnError = null
                                            when (val result = loadMatnForEdit(matnId)) {
                                                is Resource.Success -> {
                                                    editingDraft = result.data
                                                    editorEpoch++
                                                    destination = PortalDestination.UPLOAD_MATN
                                                }
                                                is Resource.Failure -> openMatnError = result.error
                                            }
                                            isLoadingEditingDraft = false
                                        }
                                    },
                                    onCreateNew = {
                                        editingDraft = null
                                        editorEpoch++
                                        destination = PortalDestination.UPLOAD_MATN
                                    },
                                )
                            }
                            else -> Text(strings.screenNotYetAvailable)
                        }
                    }
                }
            }
        }
    }
}
