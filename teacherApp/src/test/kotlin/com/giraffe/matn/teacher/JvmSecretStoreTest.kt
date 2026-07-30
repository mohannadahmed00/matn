package com.giraffe.matn.teacher

import com.giraffe.matn.core.Resource
import com.giraffe.matn.teacher.platform.AppDataDir
import com.giraffe.matn.teacher.platform.JvmSecretStore
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JvmSecretStoreTest {

    private val store = JvmSecretStore()
    private val key = "test-secret-${System.nanoTime()}"

    @AfterTest
    fun tearDown() = runTest {
        store.clear(key)
    }

    @Test
    fun `put then get returns the same value`() = runTest {
        store.put(key, "super-secret-value")
        assertEquals(Resource.Success("super-secret-value"), store.get(key))
    }

    @Test
    fun `clear then get returns null`() = runTest {
        store.put(key, "super-secret-value")
        store.clear(key)
        assertEquals(Resource.Success(null), store.get(key))
    }

    @Test
    fun `the stored file does not contain the plaintext value`() = runTest {
        val plaintext = "super-secret-value-${System.nanoTime()}"
        store.put(key, plaintext)
        val file = File(AppDataDir.path, "$key.secret")
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(plaintext))
    }
}
