package com.giraffe.matn.appearance

import com.giraffe.matn.domain.appearance.AppearanceMirror
import com.giraffe.matn.domain.model.Appearance

/**
 * In-memory fake [AppearanceMirror] for tests (T031). Records the last written value so a test
 * can assert that the resolved appearance was mirrored, and which one.
 */
class FakeAppearanceMirror : AppearanceMirror {
    var lastWritten: Appearance? = null
        private set

    override fun write(appearance: Appearance) {
        lastWritten = appearance
    }
}