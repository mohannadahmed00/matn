package com.giraffe.matn.teacher.di

import com.giraffe.matn.data.remote.FirebaseConfig
import com.giraffe.matn.data.remote.createHttpClient
import io.ktor.client.HttpClient
import org.koin.core.Koin
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.koinApplication
import java.io.File
import java.util.Properties

/**
 * Explicit provider functions for every teacher-side dependency, per Ground Rule 11: teacher-side
 * classes added to `:shared` (`FirebaseConfig`'s consumers, the REST clients, repositories, use
 * cases) MUST NOT carry Koin annotations there — `:shared`'s `ContentModule` `@ComponentScan`
 * would otherwise pull them into the student apps' graph, which has no `FirebaseConfig`/
 * `HttpClient`/`SecretStore` to give them. `@ComponentScan` here is scoped to
 * `com.giraffe.matn.teacher` only, so it is safe to scan `:teacherApp`'s own ViewModels/platform
 * classes. Later tasks add their provider function here as they create each class.
 */
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
}

/** Starts a Koin instance scoped to `:teacherApp` with only [TeacherModule] — never `:shared`'s
 * `ContentModule`, and never `initMatnKoin`, which requires student-only platform singletons and
 * seeds bundled matns. `:teacherApp` opens no SQLDelight database. */
fun startTeacherKoin(): Koin = koinApplication { modules(TeacherModule().module()) }.koin
