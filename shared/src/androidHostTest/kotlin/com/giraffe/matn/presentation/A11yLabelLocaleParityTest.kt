package com.giraffe.matn.presentation

import com.giraffe.matn.presentation.common.A11yLabels
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T056 (US2, accessibility-contract.md §2.2) — the file-I/O half of the label-catalogue gate:
 * `values/strings.xml` (Arabic) and `values-en/strings.xml` (English) declare an **identical** set
 * of `<string name=…>`, and every resource [A11yLabels] references resolves to a non-blank value in
 * both. Lives in `androidHostTest` rather than `commonTest` because it needs real file access, which
 * only the JVM-backed host-test target has here — the same reason `db/MigrationTest.kt` lives here
 * instead of `commonTest` (no cross-platform file-I/O dependency is declared in this project).
 */
class A11yLabelLocaleParityTest {

    private val nameRegex = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun parse(file: File): Map<String, String> =
        nameRegex.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2].trim() }

    private fun composeResourcesDir(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(6) {
            val candidate = File(dir, "shared/src/commonMain/composeResources")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate shared/src/commonMain/composeResources from ${System.getProperty("user.dir")}")
    }

    @Test
    fun `both locale files declare an identical set of string names`() {
        val dir = composeResourcesDir()
        val arabic = parse(File(dir, "values/strings.xml"))
        val english = parse(File(dir, "values-en/strings.xml"))
        assertEquals(arabic.keys, english.keys)
    }

    @Test
    fun `every catalogued resource is non-blank in both locales`() {
        val dir = composeResourcesDir()
        val arabic = parse(File(dir, "values/strings.xml"))
        val english = parse(File(dir, "values-en/strings.xml"))
        val referencedNames = A11yLabels.values.map { it.key.removePrefix("string:") }.toSet()
        for (name in referencedNames) {
            assertTrue(arabic[name]?.isNotBlank() == true, "'$name' missing or blank in values/strings.xml")
            assertTrue(english[name]?.isNotBlank() == true, "'$name' missing or blank in values-en/strings.xml")
        }
    }
}
