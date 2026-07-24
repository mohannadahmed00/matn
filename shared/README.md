# `:shared` module

The Kotlin Multiplatform shared library consumed by `:androidApp` and `iosApp`.

## Phase 1 foundation

Phase 1 adds the offline content foundation: a pure-Kotlin domain model
(`Matn`, `Chapter`, `Verse`, `AudioAsset`) plus a SQLDelight local store and
repository interfaces/implementations. There is no UI, playback, or networking in
this phase; everything lives under `com.giraffe.matn` in `commonMain` with thin
`expect`/`actual` SQLDelight drivers per platform.

### Build

```bash
# from repo root
./gradlew :shared:assemble
```

A successful assemble confirms the KMP module (with the SQLDelight schema,
coroutines, serialization, and Koin dependencies) compiles for all configured
targets.

### Run the validation tests

```bash
# Android host (JVM) tests — the primary gate, no device required
./gradlew :shared:testAndroidHostTest

# All configured test targets
./gradlew :shared:allTests

# iOS simulator (macOS only)
./gradlew :shared:iosSimulatorArm64Test
```

The Phase 1 tests run entirely against an in-memory SQLDelight driver — no
device, emulator, network, or real audio files are required.

### Platform wiring (Koin)

The shared `contentModule()` wires the database (`ContentDatabase`), the three
repositories (`MatnRepository`, `VerseRepository`, `AudioAssetRepository`), and
the `ContentSeedLoader`. The platform `DatabaseDriverFactory` is an
`expect`/`actual` whose construction depends on the host (Android `Context` /
iOS bundle), so each app must register a `single { DatabaseDriverFactory(...) }`
binding in its own Koin module before starting Koin so that `contentModule()`
can resolve `DatabaseDriverFactory`.

See `specs/001-foundation-data-model/` for the full design documents.