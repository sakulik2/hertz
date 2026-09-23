# Hertz

Hertz is an Android Compose pitch tuner. It captures microphone audio, estimates pitch with TarsosDSP YIN, and displays the current note, cents deviation, and observed vocal range.

## Requirements

- Android Studio with JDK 21 (the build is verified on 21; `compileOptions` targets Java 17 bytecode, which is independent of the JDK running Gradle)
- Android SDK platform 37 and build tools
- A device or emulator with a microphone

The Gradle wrapper (9.4.1) is checked in, so use `./gradlew`.

## Build and Test

```text
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebugAndroidTest    # compiles instrumentation tests without a device
./gradlew :app:connectedDebugAndroidTest   # requires a device/emulator
```

Grant microphone permission on first launch. Pitch detection is best validated on a physical device because emulator microphone behavior varies.

## Release builds

Release builds run R8. The `be.tarsos.dsp.io.android` audio bridge is kept by
`app/proguard-rules.pro`; after changing those rules, re-verify pitch detection on a
physical device, because obfuscation is most likely to break that cross-package bridge.

Signing credentials are read from `keystore.properties` in the repository root, or from
`HERTZ_STORE_FILE`, `HERTZ_STORE_PASSWORD`, `HERTZ_KEY_ALIAS` and `HERTZ_KEY_PASSWORD`.
If none are present the release build still succeeds and produces an unsigned APK.
Never commit keystores or `keystore.properties`; both are gitignored.

```properties
# keystore.properties (not tracked)
storeFile=/absolute/path/to/hertz.jks
storePassword=...
keyAlias=hertz
keyPassword=...
```

`mapping.txt` is required to read release stack traces. CI uploads it as an artifact.

## Architecture

`MainActivity` handles permission and hosts Compose, switching between `PitchScreen` and
`SettingsScreen`. `AppContainer` (in `HertzApp`) holds the shared `SettingsStore`,
`RangeRecordStore` and `ConfigSnapshot` so a settings change reaches the running pitch
detection.

`PitchRepository` owns TarsosDSP and the microphone lifecycle, reading settings per frame
from `ConfigSnapshot` because the audio callback cannot suspend on a `Flow`. `Note`,
`TunerConfig`, `PitchTracker` and the error classifier are plain Kotlin with no Android
dependencies, so the pitch maths, range tracking and failure classification are all
JVM-testable without audio hardware.

## Contributions

Use Conventional Commits such as `feat: add tuner calibration` or `fix: release audio recorder on pause`. Keep localized text in `app/src/main/res/values*/strings.xml`, include tests for pure pitch logic, and describe device or permission requirements in pull requests.
