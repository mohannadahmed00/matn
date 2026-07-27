package com.giraffe.matn.teacher.di

import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.createHttpClient
import com.giraffe.matn.data.remote.firestore.FirestoreRestClient
import com.giraffe.matn.data.remote.identity.IdentityToolkitClient
import com.giraffe.matn.data.remote.identity.TokenRefresher
import com.giraffe.matn.data.remote.storage.StorageRestClient
import com.giraffe.matn.data.repository.FirestoreCatalogRepository
import com.giraffe.matn.data.repository.IdentityTeacherAuthRepository
import com.giraffe.matn.domain.auth.TeacherAuthRepository
import com.giraffe.matn.domain.catalog.CatalogRepository
import com.giraffe.matn.domain.secret.SecretStore
import com.giraffe.matn.domain.usecase.LoadMatnForEditUseCase
import com.giraffe.matn.domain.usecase.PublishMatnUseCase
import com.giraffe.matn.domain.usecase.RestoreSessionUseCase
import com.giraffe.matn.domain.usecase.SaveDraftUseCase
import com.giraffe.matn.domain.usecase.SignInUseCase
import com.giraffe.matn.domain.usecase.SignOutUseCase
import com.giraffe.matn.domain.usecase.UploadCoverImageUseCase
import com.giraffe.matn.domain.usecase.ValidateMatnUseCase
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.koinApplication
import java.io.File
import java.util.Properties
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Explicit provider functions for every teacher-side dependency, per Ground Rule 11: teacher-side
 * classes added to `:shared` (`FirebaseConfig`'s consumers, the REST clients, repositories, use
 * cases) MUST NOT carry Koin annotations there — `:shared`'s `ContentModule` `@ComponentScan`
 * would otherwise pull them into the student apps' graph, which has no `FirebaseConfig`/
 * `HttpClient`/`SecretStore` to give them. `@ComponentScan` here is scoped to
 * `com.giraffe.matn.teacher` only, so it is safe to scan `:teacherApp`'s own ViewModels/platform
 * classes. Later tasks add their provider function here as they create each class.
 */
@OptIn(ExperimentalTime::class)
@Module
@ComponentScan("com.giraffe.matn.teacher")
class TeacherModule {

    @Single
    fun firebaseConfig(): FirebaseConfig {
        val props = Properties()
        val file = File("firebase/firebase.local.properties")
        if (file.exists()) file.inputStream().use { props.load(it) }
        return FirebaseConfig(
            projectId = System.getenv("FIREBASE_PROJECT_ID") ?: props.getProperty("projectId", ""),
            apiKey = System.getenv("FIREBASE_API_KEY") ?: props.getProperty("apiKey", ""),
            storageBucket = System.getenv("FIREBASE_STORAGE_BUCKET") ?: props.getProperty("storageBucket", ""),
            emulatorHost = System.getenv("FIREBASE_EMULATOR_HOST"),
        )
    }

    @Single
    fun httpClient(): HttpClient = createHttpClient()

    @Single
    fun identityToolkitClient(httpClient: HttpClient, firebaseConfig: FirebaseConfig): IdentityToolkitClient =
        IdentityToolkitClient(httpClient, firebaseConfig)

    @Single
    fun tokenRefresher(client: IdentityToolkitClient, secretStore: SecretStore): TokenRefresher =
        TokenRefresher(client, secretStore, nowMillis = { Clock.System.now().toEpochMilliseconds() })

    @Single
    fun teacherAuthRepository(
        client: IdentityToolkitClient,
        tokenRefresher: TokenRefresher,
        secretStore: SecretStore,
    ): TeacherAuthRepository = IdentityTeacherAuthRepository(client, tokenRefresher, secretStore)

    @Single
    fun signInUseCase(repository: TeacherAuthRepository): SignInUseCase = SignInUseCase(repository)

    @Single
    fun signOutUseCase(repository: TeacherAuthRepository): SignOutUseCase = SignOutUseCase(repository)

    @Single
    fun restoreSessionUseCase(repository: TeacherAuthRepository): RestoreSessionUseCase =
        RestoreSessionUseCase(repository)

    @Single
    fun firestoreRestClient(httpClient: HttpClient, firebaseConfig: FirebaseConfig, tokenRefresher: TokenRefresher): FirestoreRestClient =
        FirestoreRestClient(httpClient, firebaseConfig, tokenRefresher)

    @Single
    fun storageRestClient(httpClient: HttpClient, firebaseConfig: FirebaseConfig, tokenRefresher: TokenRefresher): StorageRestClient =
        StorageRestClient(httpClient, firebaseConfig, tokenRefresher)

    @Single
    fun catalogRepository(firestoreClient: FirestoreRestClient, storageClient: StorageRestClient): CatalogRepository =
        FirestoreCatalogRepository(firestoreClient, storageClient)

    @Single
    fun saveDraftUseCase(repository: CatalogRepository): SaveDraftUseCase = SaveDraftUseCase(repository)

    @Single
    fun loadMatnForEditUseCase(repository: CatalogRepository): LoadMatnForEditUseCase = LoadMatnForEditUseCase(repository)

    @Single
    fun uploadCoverImageUseCase(repository: CatalogRepository): UploadCoverImageUseCase = UploadCoverImageUseCase(repository)

    @Single
    fun validateMatnUseCase(): ValidateMatnUseCase = ValidateMatnUseCase()

    @Single
    fun publishMatnUseCase(repository: CatalogRepository): PublishMatnUseCase = PublishMatnUseCase(repository)
}

/** Starts a Koin instance scoped to `:teacherApp` with only [TeacherModule] — never `:shared`'s
 * `ContentModule`, and never `initMatnKoin`, which requires student-only platform singletons and
 * seeds bundled matns. `:teacherApp` opens no SQLDelight database. */
fun startTeacherKoin(): Koin = koinApplication { modules(TeacherModule().module()) }.koin
