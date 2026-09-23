# Repository Guidelines

## Project Structure & Module Organization

Hertz is a single-module Android application in `app/`.

- `app/src/main/java/xyz/sakulik/hertz/` contains the app entry point, Compose UI, `PitchViewModel`, and pitch-domain repository code.
- `app/src/main/java/be/tarsos/dsp/io/android/` contains the Android microphone bridge used by TarsosDSP.
- `app/src/main/res/` contains manifests, localized strings, themes, and drawable or launcher assets.
- `app/src/test/` contains JVM tests for pure pitch and range logic; add Android or Compose tests under `app/src/androidTest/` when needed.
- Root `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties` define the Gradle build.

## Build, Test, and Development Commands

Use JDK 21 and an Android SDK that provides compile SDK 37. `compileOptions` targets Java 17 bytecode, which is independent of the JDK running Gradle.

- `./gradlew :app:assembleDebug` builds a debug APK. The Gradle wrapper (9.4.1) is checked in.
- `./gradlew :app:testDebugUnitTest` runs JVM unit tests.
- `./gradlew :app:assembleDebugAndroidTest` compiles the instrumentation tests without needing a device.
- `./gradlew :app:connectedDebugAndroidTest` runs Compose and Android instrumentation tests on a connected device or emulator.
- Open the project in Android Studio to deploy the debug build and exercise microphone permission and pitch detection on a physical device.

## Coding Style & Naming Conventions

Use Kotlin official style with four-space indentation, expression-oriented code, and descriptive `PascalCase` types and `camelCase` functions or properties. Keep Compose screen functions in `*Screen.kt`, state holders in `*ViewModel.kt`, and data or audio integration in the `data` package. Use Java conventions for the small Android bridge. Keep user-visible text in `res/values*/strings.xml`; do not hardcode localized UI text. Run Android Studio formatting or `ktlint` if it is added before submitting changes.

## Testing Guidelines

Use JUnit tests for pitch conversion, cents calculations, smoothing, and vocal-range tracking. Use Compose or instrumentation tests for permission and lifecycle behavior. Name tests after the behavior, for example `requiresStableFramesBeforeRecordingExtremes`. Keep microphone-dependent checks on a real device or emulator and document hardware assumptions.

## Commit & Pull Request Guidelines

Use Conventional Commits with a required lowercase type, such as `feat:`, `fix:`, `chore:`, `test:`, or `docs:`; for example, `feat: add chromatic deviation indicator`. Keep the subject focused and explain behavior changes in the body when needed. Pull requests should describe the user-visible change, list validation commands, link an issue when applicable, and include screenshots or a short recording for UI changes. Call out microphone, permission, device, or localization impacts explicitly.

## Security & Configuration Tips

Never commit `local.properties`, signing keys, keystores, or captured audio. Keep `RECORD_AUDIO` handling least-privileged and verify that audio resources stop when the activity is paused or the ViewModel is cleared.
