package com.giraffe.matn

import com.giraffe.matn.data.remote.SupabaseConfig
import java.io.File
import java.util.Properties

/**
 * The backend the desktop student client reads from, resolved the same way `:teacherApp` resolves
 * its own config (`TeacherModule.supabaseConfig`): environment variables first, then
 * `supabase/supabase.local.properties`.
 *
 * Injected, never compiled in (research D13) — pointing `SUPABASE_URL` at
 * `http://127.0.0.1:54321` runs the app against a local stack.
 *
 * Only `projectUrl`, `anonKey` and `bucket` are needed, and none of them is a secret: the anon key
 * identifies the project, not the caller, and the student sends no credential at all (FR-027).
 * Authorisation is the `published` flag enforced by row-level security, so a leaked anon key grants
 * exactly what the app already grants anonymously — the published catalog.
 */
fun studentSupabaseConfig(): SupabaseConfig {
    val props = localProperties()
    return SupabaseConfig(
        projectUrl = System.getenv("SUPABASE_URL") ?: props.getProperty("supabaseUrl", ""),
        anonKey = System.getenv("SUPABASE_ANON_KEY") ?: props.getProperty("supabaseAnonKey", ""),
        bucket = System.getenv("SUPABASE_BUCKET") ?: props.getProperty("supabaseBucket", "matn-content"),
    )
}

/**
 * Walks up from the working directory, because it differs between `./gradlew :desktopApp:run` (repo
 * root) and an IDE run configuration (the module directory) — the same trap `:teacherApp` documents,
 * where a plain relative path silently produced an empty config and surfaced as a misleading
 * "cannot reach the server".
 */
private fun localProperties(): Properties {
    val props = Properties()
    generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "supabase/supabase.local.properties") }
        .firstOrNull { it.isFile }
        ?.inputStream()
        ?.use { props.load(it) }
    return props
}
