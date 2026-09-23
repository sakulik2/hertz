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

## Architecture

`MainActivity` handles permission and hosts Compose. `PitchScreen` renders state from `PitchViewModel`. `PitchRepository` owns TarsosDSP and microphone lifecycle. `PitchTracker` contains pure pitch-to-MIDI and vocal-range tracking logic so it can be tested without Android audio hardware.

## Contributions

Use Conventional Commits such as `feat: add tuner calibration` or `fix: release audio recorder on pause`. Keep localized text in `app/src/main/res/values*/strings.xml`, include tests for pure pitch logic, and describe device or permission requirements in pull requests.
