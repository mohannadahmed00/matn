package com.giraffe.matn.remote

import com.giraffe.matn.core.Resource
import com.giraffe.matn.data.remote.SupabaseConfig
import com.giraffe.matn.data.remote.auth.SupabaseAuthClient
import com.giraffe.matn.data.remote.auth.TokenRefresher
import com.giraffe.matn.domain.auth.TeacherSession
import com.giraffe.matn.domain.secret.SecretStore
import org.junit.Assume.assumeTrue

class FakeSecretStore : SecretStore {
    private val store = mutableMapOf<String, String>()
    override val isProtected: Boolean = true
    override suspend fun put(key: String, value: String): Resource<Unit> {
        store[key] = value
        return Resource.Success(Unit)
    }
    override suspend fun get(key: String): Resource<String?> = Resource.Success(store[key])
    override suspend fun clear(key: String): Resource<Unit> {
        store.remove(key)
        return Resource.Success(Unit)
    }
}

/** A refresher already holding [session], with an expiry far enough out that nothing refreshes. */
fun primedRefresher(client: SupabaseAuthClient, session: TeacherSession?): TokenRefresher =
    TokenRefresher(client, FakeSecretStore(), nowMillis = { 0L }).apply { session?.let { setSession(it) } }

/**
 * Set by the RLS workflow (`.github/workflows/rls-policy-tests.yml`) from `supabase status`.
 * Unset means no local Supabase stack on this machine, and the policy tests skip rather than fail —
 * an ordinary `./gradlew test` stays green with no Supabase CLI installed (research D9).
 */
data class LocalStack(val url: String, val anonKey: String, val serviceRoleKey: String)

fun localStackOrNull(): LocalStack? {
    val url = System.getenv("SUPABASE_TEST_URL") ?: return null
    val anonKey = System.getenv("SUPABASE_TEST_ANON_KEY") ?: return null
    val serviceRoleKey = System.getenv("SUPABASE_TEST_SERVICE_ROLE_KEY") ?: return null
    return LocalStack(url, anonKey, serviceRoleKey)
}

fun requireLocalStack(): LocalStack {
    val stack = localStackOrNull()
    assumeTrue("SUPABASE_TEST_URL/ANON_KEY/SERVICE_ROLE_KEY not set — skipping stack-gated test", stack != null)
    return requireNotNull(stack)
}

fun LocalStack.config(bucket: String = "matn-content") =
    SupabaseConfig(projectUrl = url, anonKey = anonKey, bucket = bucket)
