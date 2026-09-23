# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Hertz is a single-module Android Compose pitch tuner. It reads the microphone, estimates pitch with TarsosDSP YIN, and shows the current note, cents deviation on a dial, and the observed vocal range.

- Module: `app/`, namespace and applicationId `xyz.sakulik.hertz`
- Builds on JDK 21 (verified); `compileOptions` targets Java 17 bytecode, which is independent of the JDK running Gradle
- Compile SDK 37, target SDK 37, min SDK 26
- Kotlin + Jetpack Compose (Material 3), Compose BOM `2026.09.00`
- `be.tarsos.dsp:core:2.5` for pitch detection, with `slf4j-nop` to silence its logging
- `androidx.datastore:datastore-preferences` for persisted settings and the range record
- Dependency versions live in `gradle/libs.versions.toml` — add them there, not inline

## Build and Test

The Gradle wrapper (9.4.1) is checked in, so use `./gradlew`.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest        # compiles instrumentation tests without a device
./gradlew :app:connectedDebugAndroidTest       # requires a device/emulator
./gradlew :app:assembleRelease                 # exercises R8; see proguard-rules.pro
./gradlew :app:testDebugUnitTest --tests "xyz.sakulik.hertz.data.NoteTest.convertsA440ToMidi69"
```

`dependencyResolutionManagement` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories go in `settings.gradle.kts`, never in `app/build.gradle.kts`. `mvn.0110.be/releases` is load-bearing — it serves TarsosDSP, and removing it breaks dependency resolution. Dependency downloads from `dl.google.com` occasionally fail with a TLS handshake error; retrying the build resolves it.

## Architecture

Audio flows one way: microphone → TarsosDSP → `Channel` → `Flow` → `StateFlow` → Compose.

- `be.tarsos.dsp.io.android.{AudioDispatcherFactory, AndroidAudioInputStream}` (Java) is a hand-written bridge living in the TarsosDSP package namespace, because `be.tarsos.dsp:core:2.5` ships no Android backend. `AudioDispatcherFactory` owns `AudioRecord` creation (44.1 kHz mono PCM 16-bit) and throws `MicrophoneUnavailableException` carrying a `Reason` (`NOT_INITIALIZED` vs `START_FAILED`) so the failure modes can be told apart. `AndroidAudioInputStream.close()` is the only place the recorder is stopped and released, and the `AudioDispatcher` calls it — don't add a second release path.
- `data/PitchSource` is the interface `PitchViewModel` depends on; `data/PitchRepository` is the real implementation. The indirection exists so the ViewModel can be unit-tested against a fake without touching `AudioRecord` — keep new ViewModel dependencies behind it. `SettingsStore` and `RangeRecordStore` follow the same pattern, with in-memory implementations in `data/InMemoryStores.kt` shared by unit tests, instrumentation tests, and previews.
- `data/TunerConfig` is the single source of truth for every tunable parameter and validates them in `init`. `data/Note` is a `@JvmInline value class` over a MIDI number holding the pitch maths; reference pitch and enharmonic spelling are *parameters*, not fields, because the same A4 is the same note at A440 and A442. Don't reintroduce loose `noteName: String` + `octave: Int` pairs.
- `PitchRepository` runs the dispatcher on `Dispatchers.IO`, gates frames (`isPitched`, confidence, frequency window), and emits `PitchResult.Detected | Silence | Error` through a capacity-8 `DROP_OLDEST` channel so a slow collector can never block the audio thread. `startListening`/`stopListening` are `@Synchronized` and idempotent, and `startListening` drains the channel first so frames buffered before a pause aren't replayed into the new session. Settings arrive through `configProvider`, read **per frame** from `ConfigSnapshot` (a `@Volatile` holder) because the TarsosDSP callback runs on the audio thread and cannot suspend on a `Flow`. Sample rate and buffer size are read once per session — changing them requires rebuilding `AudioRecord`.
- `data/PitchTracker` is pure Kotlin with no Android dependencies — deliberately, so the pitch math is JVM-unit-testable. It only commits a range extreme after the same note appears in `streakThreshold` consecutive frames, filtering transient YIN outliers. The low/high logic is one `ExtremeStreak` class used twice; don't fork it back into two near-identical branches.
- **Session readout and persisted record use separate trackers with different thresholds.** At ~43 frames/second the default 3 frames is ~70 ms — fine for a live readout that refreshes, far too loose to commit something permanent, since one outlier would poison a recorded extreme forever. `recordStreakThreshold` (default 20) governs the persistence path. Keep them decoupled.
- `ui/PitchViewModel` collects the flow into one `UiState`, exponentially smooths cents for the dial (weight from `config.smoothingFactor`), and writes settings into the shared `ConfigSnapshot`. It separates user pauses from lifecycle pauses via `userManuallyPaused`: `resumeListeningFromLifecycle()` must not restart capture after the user tapped Pause. `resetRange()` clears only the session; `clearBestRange()` deletes the persisted record and needs UI confirmation. Use `PitchViewModel.forTesting(...)` in tests.
- `data/PitchError` is a sealed class and `UiState.error` is a `PitchError?`, not a boolean. Each kind maps to its own message *and its own action*: a revoked permission opens system settings (retrying can't help), a busy device offers retry, an init failure offers neither. `classifyPitchError` is a top-level function taking `hasPermission` as a parameter so it stays JVM-testable and so permission is re-read at failure time — it can be revoked mid-capture.
- `HertzApp`/`AppContainer` is a hand-rolled container (no DI framework; there are three dependencies). It exists because both screens **must** share one `SettingsStore` and one `ConfigSnapshot` — otherwise a settings change never reaches the running pitch detection. Both ViewModel factories resolve through it via `APPLICATION_KEY`.
- `ui/PitchScreen` drives start/stop from a `LifecycleEventObserver` (ON_RESUME/ON_PAUSE) plus `onDispose`, and composes `ui/components/{TunerDial, NoteDisplay, RangeCard}`. Dial geometry lives in `TunerDial` (210° start, 120° sweep; ±50 cents maps to ±60°). `KeepScreenOn` holds the window flag while listening, because singing into the mic produces no touch events. Both screens are `verticalScroll`, which is what makes the unlocked orientation and large font scales safe.
- The dial is the app's main information carrier and a bare `Canvas` is invisible to TalkBack, so `TunerDial` requires a `semanticLabel`. Keep supplying one.
- `MainActivity` owns `RECORD_AUDIO` permission and switches between `PitchScreen` and `SettingsScreen` with a `rememberSaveable` flag plus `BackHandler` — deliberately no navigation-compose for two screens. A keyed `LifecycleResumeEffect` re-checks permission on every resume so returning from system settings updates the UI. Note `LifecycleResumeEffect` comes from `androidx.lifecycle.compose`, not `androidx.activity.compose`, and the no-key overload is deprecated into a compile error.
- `HertzTheme` follows the system via `isSystemInDarkTheme()`. Theming is coupled across four files and changing one side alone breaks it: `values/` and `values-night/` each supply `hertz_window_background` (kept equal to `BackgroundLight` / `BackgroundDeep` in `ui/theme/Color.kt`, or launch shows a colour-shift frame) and each set `windowLightStatusBar` in the matching direction (`true` for light, `false` for dark, or the status-bar icons match their background and vanish).
- **Set every `surfaceContainer*` rung and the `*Container` roles explicitly.** Material 3 components read different rungs — `ElevatedCard` takes `surfaceContainerLow`, `AlertDialog` takes `surfaceContainerHigh`, `FilledTonalButton` takes `secondaryContainer` — and any rung left unset silently falls back to the Material baseline purple even though the rest of the palette is applied. This shipped as visible purple cards and buttons until caught on device.
- Tuner semantics Material has no slot for (`inTune`/`slightlyOff`/`off`/`dialTrack`) live in `MaterialTheme.tunerColors`, not in `colorScheme`. Both themes supply a set; the `CompositionLocal` defaults to dark for previews and instrumentation tests that don't wrap `HertzTheme`. The light signal colour is a much darker teal than the dark one — the dark `#3DDCC4` only reaches about 1.8:1 on a light background. `safeDrawingPadding()` handles the edge-to-edge enforcement that comes with targetSdk 35+.

## Conventions

- Kotlin official style, four-space indent. Compose screens in `*Screen.kt`, state holders in `*ViewModel.kt`, audio and domain code in `data/`. The Android audio bridge stays Java and follows Java conventions.
- `values/strings.xml` is the **Chinese** default (stored as XML character entities); `values-en/strings.xml` is English. Every user-visible string goes in both — no hardcoded UI text. Inline comments in `PitchRepository` and `PitchScreen` are Chinese; match the surrounding file.
- Conventional Commits with a lowercase type (`feat:`, `fix:`, `chore:`, `test:`, `docs:`).
- Name tests after behavior, e.g. `requiresStableFramesBeforeRecordingExtremes`. Unit-test pitch conversion, cents, smoothing, and range tracking; use instrumentation tests for permission and lifecycle behavior.
- Never commit `local.properties`, `keystore.properties`, keystores, or captured audio.

## When changing audio code

Verify the recorder is released on pause and on `ViewModel.onCleared()` — a leaked `AudioRecord` holds the mic against other apps. Emulator microphone behavior is unreliable, so validate real pitch detection on a physical device and state that in the PR alongside any permission or localization impact.

## When changing release config

`isMinifyEnabled = true`, so R8 runs on release builds. `app/proguard-rules.pro` keeps the `be.tarsos.dsp.io.android` bridge and `PitchProcessor$PitchEstimationAlgorithm`; the TarsosDSP jar itself uses no reflection or service loaders, so keep the rules narrow rather than adding blanket keeps. After touching those rules, re-verify pitch detection on a physical device **with a release build** — obfuscation is most likely to break the cross-package audio bridge, and no test covers it. `mapping.txt` is the only way to read release stack traces; CI uploads it.

Signing reads `keystore.properties` or `HERTZ_STORE_FILE`/`HERTZ_STORE_PASSWORD`/`HERTZ_KEY_ALIAS`/`HERTZ_KEY_PASSWORD`. With none present, release builds still succeed unsigned — that is intentional, not a misconfiguration.
