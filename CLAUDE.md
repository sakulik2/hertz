# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Hertz is a single-module Android Compose pitch tuner. It reads the microphone, estimates pitch with TarsosDSP YIN, and shows the current note, cents deviation on a dial, and the observed vocal range.

- Module: `app/`, namespace and applicationId `xyz.sakulik.hertz`
- Builds on JDK 21 (verified); `compileOptions` targets Java 17 bytecode, which is independent of the JDK running Gradle
- Compile SDK 37, target SDK 37, min SDK 26
- Kotlin + Jetpack Compose (Material 3), Compose BOM `2024.05.00`
- `be.tarsos.dsp:core:2.5` for pitch detection, with `slf4j-nop` to silence its logging

## Build and Test

The Gradle wrapper (9.4.1) is checked in, so use `./gradlew`.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest        # compiles instrumentation tests without a device
./gradlew :app:connectedDebugAndroidTest       # requires a device/emulator
./gradlew :app:testDebugUnitTest --tests "xyz.sakulik.hertz.data.PitchTrackerTest.convertsA440ToMidi69"
```

`dependencyResolutionManagement` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories go in `settings.gradle.kts`, never in `app/build.gradle.kts`. `mvn.0110.be/releases` is load-bearing — it serves TarsosDSP, and removing it breaks dependency resolution. Dependency downloads from `dl.google.com` occasionally fail with a TLS handshake error; retrying the build resolves it.

## Architecture

Audio flows one way: microphone → TarsosDSP → `Channel` → `Flow` → `StateFlow` → Compose.

- `be.tarsos.dsp.io.android.{AudioDispatcherFactory, AndroidAudioInputStream}` (Java) is a hand-written bridge living in the TarsosDSP package namespace, because `be.tarsos.dsp:core:2.5` ships no Android backend. `AudioDispatcherFactory` owns `AudioRecord` creation (44.1 kHz mono PCM 16-bit) and throws `IllegalStateException` when it fails to initialize. `AndroidAudioInputStream.close()` is the only place the recorder is stopped and released, and the `AudioDispatcher` calls it — don't add a second release path.
- `data/PitchSource` is the interface `PitchViewModel` depends on; `data/PitchRepository` is the real implementation. The indirection exists so the ViewModel can be unit-tested against a fake without touching `AudioRecord` — keep new ViewModel dependencies behind it.
- `PitchRepository` runs the dispatcher on `Dispatchers.IO`, gates frames (`isPitched`, probability ≥ 0.85, 80–1100 Hz), and emits `PitchResult.Detected | Silence | Error` through a capacity-8 `DROP_OLDEST` channel so a slow collector can never block the audio thread. `startListening`/`stopListening` are `@Synchronized` and idempotent, and `startListening` drains the channel first so frames buffered before a pause aren't replayed into the new session.
- `data/PitchTracker` is pure Kotlin with no Android dependencies — deliberately, so the pitch math is JVM-unit-testable. Its companion object is the single source of truth for frequency→MIDI, cents, note name, and octave; `PitchRepository` calls into it rather than recomputing. It only commits a new range extreme after the same MIDI note appears in `streakThreshold` (default 3) consecutive frames, which filters transient YIN outliers.
- `ui/PitchViewModel` collects the flow into one `UiState` and exponentially smooths cents (`0.7 * previous + 0.3 * new`) for the dial. It separates user pauses from lifecycle pauses via `userManuallyPaused`: `resumeListeningFromLifecycle()` must not restart capture after the user tapped Pause. Built through `PitchViewModel.Factory`.
- `ui/PitchScreen` drives start/stop from a `LifecycleEventObserver` (ON_RESUME/ON_PAUSE) plus `onDispose`, and draws the dial with `Canvas` (210° start, 120° sweep; ±50 cents maps to ±60°).
- `MainActivity` owns `RECORD_AUDIO` permission only — it shows `PitchScreen` when granted, otherwise a rationale/request screen. A keyed `LifecycleResumeEffect` re-checks permission on every resume so returning from system settings updates the UI. Note `LifecycleResumeEffect` comes from `androidx.lifecycle.compose`, not `androidx.activity.compose`, and the no-key overload is deprecated into a compile error.
- Theming is deliberately coupled: `themes.xml` sets a black `windowBackground` and light status-bar icons to avoid a launch flash, so `MainActivity` passes `darkColorScheme()` to `MaterialTheme`. Changing one side without the other reintroduces a black-to-white flash and invisible status-bar icons. There is no `values-night`; the app is dark-only. `safeDrawingPadding()` handles the edge-to-edge enforcement that comes with targetSdk 35+.

## Conventions

- Kotlin official style, four-space indent. Compose screens in `*Screen.kt`, state holders in `*ViewModel.kt`, audio and domain code in `data/`. The Android audio bridge stays Java and follows Java conventions.
- `values/strings.xml` is the **Chinese** default (stored as XML character entities); `values-en/strings.xml` is English. Every user-visible string goes in both — no hardcoded UI text. Inline comments in `PitchRepository` and `PitchScreen` are Chinese; match the surrounding file.
- Conventional Commits with a lowercase type (`feat:`, `fix:`, `chore:`, `test:`, `docs:`).
- Name tests after behavior, e.g. `requiresStableFramesBeforeRecordingExtremes`. Unit-test pitch conversion, cents, smoothing, and range tracking; use instrumentation tests for permission and lifecycle behavior.
- Never commit `local.properties`, keystores, or captured audio.

## When changing audio code

Verify the recorder is released on pause and on `ViewModel.onCleared()` — a leaked `AudioRecord` holds the mic against other apps. Emulator microphone behavior is unreliable, so validate real pitch detection on a physical device and state that in the PR alongside any permission or localization impact.
