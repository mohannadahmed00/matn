# Matn — متن

Matn is a calm, distraction-free mobile app for reading, listening to, and memorizing Islamic texts (متون). It's built as a memorization companion rather than a traditional audio player — students read the Arabic text while listening to their teacher's recitation, with flexible repetition tools designed specifically for memorization.

This is a **Kotlin Multiplatform** project targeting **Android** and **iOS**.

## Features

- Verse-by-verse reading with synced audio highlighting and auto-scroll
- Per-verse and per-matn repetition controls, including a dedicated Memorization Mode
- A–B loop for practicing a specific range of verses
- Continue Learning — resumes exactly where you left off, including mid-loop
- Bookmarks and personal notes per verse
- Diacritic-insensitive search across verse text, verse number, and matn title
- Daily memorization goals with progress tracking based on actual recall, not just listen count
- Full RTL support, Arabic typography tuned for long reading sessions, dark mode
- Fully offline-first with per-matn downloads

## Project Structure

- [`/iosApp`](./iosApp/iosApp) — the iOS application entry point. Even when sharing UI via Compose Multiplatform, this is required for the iOS app, and is where SwiftUI code lives if needed.

- [`/shared`](./shared/src) — code shared across platforms:
  - [`commonMain`](./shared/src/commonMain/kotlin) — code common to all targets (verse/matn models, playback logic, repetition engine, search, progress tracking).
  - [`androidMain`](./shared/src/androidMain/kotlin) — Android-specific implementations (e.g. ExoPlayer integration for gapless verse-to-verse audio).
  - [`iosMain`](./shared/src/iosMain/kotlin) — iOS-specific implementations (e.g. AVQueuePlayer integration for gapless verse-to-verse audio).

## Running the Apps

Use the run configurations in your IDE's toolbar, or:

- **Android app:** `./gradlew :androidApp:assembleDebug`
- **iOS app:** open the [`/iosApp`](./iosApp) directory in Xcode and run it from there.

## Running Tests

Use the run button in your IDE's editor gutter, or run via Gradle:

- **Android tests:** `./gradlew :shared:testAndroidHostTest`
- **iOS tests:** `./gradlew :shared:iosSimulatorArm64Test`

---

Built with [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).