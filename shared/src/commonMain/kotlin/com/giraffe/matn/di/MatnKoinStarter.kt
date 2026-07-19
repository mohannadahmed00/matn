package com.giraffe.matn.di

import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.data.seed.ContentSeedLoader
import com.giraffe.matn.data.seed.SeedAudio
import com.giraffe.matn.data.seed.SeedChapter
import com.giraffe.matn.data.seed.SeedMatn
import com.giraffe.matn.data.seed.SeedVerse
import com.giraffe.matn.domain.repository.MatnRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Holds the started [Koin] instance for the app, accessible from `commonMain` on every target.
 *
 * Constitution Principle IV (shared-first) requires that DI resolution works identically on
 * Android and iOS from one source. Koin 4.x's `org.koin.core.context.GlobalContext` is not
 * resolvable on the Kotlin/Native target (the `context` package isn't exported there), so we
 * keep the started `Koin` instance in this common holder instead — a single source of truth
 * across targets, no `expect`/`actual` duplication, no JVM-only API. Initialized exactly once
 * by [initMatnKoin] before any composable resolves a dependency.
 */
object MatnKoinHolder {
    // Single-threaded app access: `initMatnKoin` runs once before any composable reads this.
    // `@Volatile` is intentionally avoided — it isn't resolvable on Kotlin/Native and there's
    // no cross-platform multiplatform equivalent without an expect/actual, which would violate
    // the common-single-source-of-truth goal. The holder is published before the UI starts.
    private var _koin: Koin? = null

    val koin: Koin
        get() = _koin ?: error("MatnKoinHolder not initialized — call initMatnKoin first")

    fun isInitialized(): Boolean = _koin != null

    fun initialize(koin: Koin) {
        _koin = koin
    }
}

/**
 * Boots the app-wide Koin instance with the platform-provided [driverFactory] plus the shared
 * [contentModule]. The platform shell (Android `MainActivity`, iOS `MainViewController`) owns
 * the platform-specific construction of [DatabaseDriverFactory] and calls this exactly once
 * before any composable that resolves a use case (Constitution Principle I/III).
 *
 * After Koin starts, optionally seeds the local store with the bundled sample content if the
 * library is empty — so the Phase 1 manual walkthrough has at least one simple and one
 * structured matn to exercise (per Phase 0's "Assumptions: a simple and a structured matn are
 * seeded"). Seeding is fire-and-forget on a background [SupervisorJob] scope so it never blocks
 * app startup; if it fails the empty-state screen is shown (FR-004/SC-008) rather than crashing.
 */
fun initMatnKoin(driverFactory: DatabaseDriverFactory, seedIfEmpty: Boolean = true) {
    // Guard the whole body: a second call (e.g. Android `onCreate` after a rotation) must not
    // re-start Koin nor re-launch the seed coroutine. Everything below runs exactly once.
    if (MatnKoinHolder.isInitialized()) return

    val app = koinApplication {
        modules(
            module {
                single { driverFactory }
            },
            contentModule(),
        )
    }
    MatnKoinHolder.initialize(app.koin)

    if (seedIfEmpty) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch { seedBundledSamplesIfEmpty() }
    }
}

private suspend fun seedBundledSamplesIfEmpty() {
    val koin = MatnKoinHolder.koin
    val matnRepo = koin.get<MatnRepository>()
    val loader = koin.get<ContentSeedLoader>()
    val library = matnRepo.observeLibrary().first()
    if (library.isNotEmpty()) return
    bundledSampleMatns().forEach { payload -> loader.load(payload) }
}

private fun bundledSampleMatns(): List<SeedMatn> = listOf(
    SeedMatn(
        id = "b3f1e2a4-0000-4000-8000-000000000001",
        title = "الأجرومية",
        author = "ابن آجُرُّوم",
        description = "متن مختصر في علم النحو",
        // No cover assets are bundled in Phase 1 — leave null so the shared placeholder renders
        // (FR-002: "cover image, or a consistent placeholder when none is set"). Real cover
        // rendering from a content-provided ref is deferred to a later phase.
        coverImageRef = null,
        structureKind = "SIMPLE",
        defaultReciterId = "reciter-default-v1",
        verses = listOf(
            SeedVerse(
                id = "b3f1e2a4-0000-4000-8000-0000000000v1",
                displayNumber = 1,
                arabicText = "الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ",
                durationMs = 8200,
                audio = SeedAudio(
                    id = "b3f1e2a4-0000-4000-8000-0000000000a1",
                    fileRef = "ajurrumiyya_verse_001.mp3",
                    durationMs = 8200,
                ),
            ),
            SeedVerse(
                id = "b3f1e2a4-0000-4000-8000-0000000000v2",
                displayNumber = 2,
                arabicText = "وَأَقسامُهُ ثَلاثَةٌ: اِسمٌ، وَفِعلٌ، وَحَرفٌ جاءَ لِمَعنىً",
                durationMs = 7400,
                audio = SeedAudio(
                    id = "b3f1e2a4-0000-4000-8000-0000000000a2",
                    fileRef = "ajurrumiyya_verse_002.mp3",
                    durationMs = 7400,
                ),
            ),
            SeedVerse(
                id = "b3f1e2a4-0000-4000-8000-0000000000v3",
                displayNumber = 3,
                arabicText = "فَالاسمُ يُعرَفُ بِالخَفضِ وَالتَنوينِ وَوُروجِ الأَلِفِ",
                durationMs = 6900,
                audio = SeedAudio(
                    id = "b3f1e2a4-0000-4000-8000-0000000000a3",
                    fileRef = "ajurrumiyya_verse_003.mp3",
                    durationMs = 6900,
                ),
            ),
            SeedVerse(
                id = "b3f1e2a4-0000-4000-8000-0000000000v4",
                displayNumber = 4,
                arabicText = "والفِعلُ يُعرَفُ بِقَد وَالسينِ وَسَوفَ وَتاءِ التَّأنيثِ",
                durationMs = 8800,
                audio = SeedAudio(
                    id = "b3f1e2a4-0000-4000-8000-0000000000a4",
                    fileRef = "ajurrumiyya_verse_004.mp3",
                    durationMs = 8800,
                ),
            ),
        ),
    ),
    SeedMatn(
        id = "e5c5c5c5-0000-4000-8000-000000000001",
        title = "متن الآجرومية مبوب",
        author = "ابن آجُرُّوم",
        description = "نموذج مبوب بأبواب",
        coverImageRef = null,
        structureKind = "STRUCTURED",
        defaultReciterId = "reciter-default-v1",
        chapters = listOf(
            SeedChapter(
                id = "e5c5c5c5-0000-4000-8000-0000000000c1",
                title = "باب الكلام",
                order = 1,
            ),
            SeedChapter(
                id = "e5c5c5c5-0000-4000-8000-0000000000c2",
                title = "باب الإعراب",
                order = 2,
            ),
        ),
        verses = listOf(
            SeedVerse(
                id = "e5c5c5c5-0000-4000-8000-0000000000v1",
                chapterId = "e5c5c5c5-0000-4000-8000-0000000000c1",
                displayNumber = 1,
                arabicText = "الكَلامُ هُوَ اللَّفظُ المُرَكَّبُ المُفيدُ بِالوَضعِ",
                durationMs = 8200,
                audio = SeedAudio(
                    id = "e5c5c5c5-0000-4000-8000-0000000000a1",
                    fileRef = "structured_verse_001.mp3",
                    durationMs = 8200,
                ),
            ),
            SeedVerse(
                id = "e5c5c5c5-0000-4000-8000-0000000000v2",
                chapterId = "e5c5c5c5-0000-4000-8000-0000000000c1",
                displayNumber = 2,
                arabicText = "وَأَقسامُهُ ثَلاثَةٌ: اِسمٌ، وَفِعلٌ، وَحَرفٌ",
                durationMs = 7400,
                audio = SeedAudio(
                    id = "e5c5c5c5-0000-4000-8000-0000000000a2",
                    fileRef = "structured_verse_002.mp3",
                    durationMs = 7400,
                ),
            ),
            SeedVerse(
                id = "e5c5c5c5-0000-4000-8000-0000000000v3",
                chapterId = "e5c5c5c5-0000-4000-8000-0000000000c1",
                displayNumber = 3,
                arabicText = "فَالاسمُ يُعرَفُ بِالخَفضِ وَالتَنوينِ",
                durationMs = 6900,
                audio = SeedAudio(
                    id = "e5c5c5c5-0000-4000-8000-0000000000a3",
                    fileRef = "structured_verse_003.mp3",
                    durationMs = 6900,
                ),
            ),
            SeedVerse(
                id = "e5c5c5c5-0000-4000-8000-0000000000v4",
                chapterId = "e5c5c5c5-0000-4000-8000-0000000000c2",
                displayNumber = 4,
                arabicText = "الإِعرابُ هُوَ تَغييرُ أَواخِرِ الكَلِماتِ",
                durationMs = 8100,
                audio = SeedAudio(
                    id = "e5c5c5c5-0000-4000-8000-0000000000a4",
                    fileRef = "structured_verse_004.mp3",
                    durationMs = 8100,
                ),
            ),
            SeedVerse(
                id = "e5c5c5c5-0000-4000-8000-0000000000v5",
                chapterId = "e5c5c5c5-0000-4000-8000-0000000000c2",
                displayNumber = 5,
                arabicText = "وَأَقسامُهُ أَربَعَةٌ: رَفعٌ وَنَصبٌ وَخَفضٌ وَجَزْمٌ",
                durationMs = 9300,
                audio = SeedAudio(
                    id = "e5c5c5c5-0000-4000-8000-0000000000a5",
                    fileRef = "structured_verse_005.mp3",
                    durationMs = 9300,
                ),
            ),
        ),
    ),
)