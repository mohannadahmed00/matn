package com.giraffe.matn.presentation

import com.giraffe.matn.presentation.common.A11yAction
import com.giraffe.matn.presentation.common.A11yLabels
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T056 (US2, accessibility-contract.md §2.2) — the catalogue is **total** over [A11yAction] and no
 * two actions share a resource. The cross-locale string-name-parity half of §2.2 (identical
 * `<string name=…>` sets in `values/` and `values-en/`) needs real file I/O, which is not available
 * uniformly across every `commonTest` target in this project (no cross-platform file-reading
 * dependency is declared) — it lives instead in `androidHostTest/.../A11yLabelLocaleParityTest.kt`,
 * following the same host-only-test precedent `db/MigrationTest.kt` already sets. Recorded in
 * `design-notes.md` (T103).
 */
class A11yLabelCatalogTest {

    @Test
    fun `catalogue has exactly one entry per action`() {
        assertEquals(A11yAction.entries.toSet(), A11yLabels.keys)
    }

    @Test
    fun `no two actions share a resource`() {
        val resources = A11yLabels.values.toList()
        assertEquals(resources.size, resources.toSet().size, "a StringResource is reused across two distinct actions")
    }
}
