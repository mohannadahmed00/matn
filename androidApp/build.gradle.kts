import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/**
 * Phase 13 (research D13): the backend the student reads from, injected rather than compiled in —
 * environment first, then `supabase/supabase.local.properties`, mirroring how `:teacherApp` and
 * `:desktopApp` resolve the same three values.
 *
 * None is a secret. The anon key identifies the *project*, not the caller, and the student sends no
 * credential at all (FR-027); authorisation is the `published` flag enforced by row-level security,
 * so this grants exactly what the app already grants anonymously — the published catalog.
 */
val supabaseProps = Properties().apply {
    generateSequence(rootDir) { it.parentFile }
        .map { File(it, "supabase/supabase.local.properties") }
        .firstOrNull { it.isFile }
        ?.inputStream()
        ?.use { load(it) }
}

fun supabaseValue(env: String, prop: String, fallback: String = ""): String =
    System.getenv(env) ?: supabaseProps.getProperty(prop, fallback)

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    implementation(libs.androidx.core.splashscreen)
}

android {
    namespace = "com.giraffe.matn"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.giraffe.matn"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${supabaseValue("SUPABASE_URL", "supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseValue("SUPABASE_ANON_KEY", "supabaseAnonKey")}\"")
        buildConfigField("String", "SUPABASE_BUCKET", "\"${supabaseValue("SUPABASE_BUCKET", "supabaseBucket", "matn-content")}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}