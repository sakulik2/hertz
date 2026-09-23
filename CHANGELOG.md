# Changelog

All notable changes to Hertz are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-09-24

Infrastructure hardening pass. The app's features were working, but nothing it produced
survived being closed, nothing was configurable, and there was no release path.

### Added

- Persistent personal-best vocal range, backed by DataStore. Records use a stricter
  confirmation threshold than the live readout so a single pitch-detection outlier
  cannot permanently poison a recorded extreme. Each record stores the reference pitch
  it was measured against.
- Settings screen: reference pitch calibration (415–466 Hz), enharmonic spelling
  (sharps or flats), detection sensitivity, pointer smoothing, and the frequency
  window. Changes apply to the next audio frame without restarting capture.
- Custom design system (`ui/theme`) replacing the stock Material baseline palette, with
  a monospace readout so digits do not shift width as the frequency updates.
- Typed microphone errors. A revoked permission, a busy device, and a hardware failure
  now give distinct explanations and distinct actions instead of one generic message.
- Accessibility: the dial reports its deviation to screen readers (previously an
  unlabelled `Canvas`, invisible to TalkBack), and readouts are announced as single
  coherent statements.
- The screen stays awake while listening, since singing into the microphone produces no
  touch events.
- R8 shrinking with rules for the hand-written audio bridge, release signing from
  `keystore.properties` or environment variables, and GitHub Actions CI running unit
  tests, debug and release builds, and instrumentation compilation.
- A tag-triggered release workflow. Unlike CI, it refuses to build without signing
  credentials, since an unsigned APK cannot install as an upgrade over a signed one, and
  it fails when the tag disagrees with `versionName`. CI verifies the signer of a signed
  build rather than trusting a successful build, because a `storeFile` path that
  `.properties` escaping had mangled still produced a green unsigned build.
- Gradle version catalog (`gradle/libs.versions.toml`).

### Changed

- Compose BOM upgraded from `2024.05.00` to `2026.09.00`, closing a roughly two-year
  gap against compileSdk 37 and Kotlin 2.2.10.
- Pitch maths consolidated into a `Note` value class and all tuning parameters into
  `TunerConfig`, removing constants that were duplicated across files and had to be
  kept in sync by hand.
- `PitchScreen` split into reusable components; the dial, readout and range card are no
  longer inline in one long function.
- Both screens scroll, so large font scales and short screens no longer clip controls.
  With that fixed, the portrait lock was removed.
- Dependencies resolve from upstream repositories rather than region-specific mirrors.
- Unit and instrumentation tests expanded from 11 to 65, covering pitch conversion at
  non-standard reference pitches, streak-breaking in range tracking, error
  classification, persistence, and settings validation.

### Fixed

- `AudioRecord` is now released in a `finally` block and tolerates a recorder that is
  not in the recording state, so a failed session still hands the microphone back to
  other apps.
- `skip()` no longer allocates a buffer sized to the full requested skip, and no longer
  treats a zero-byte read as progress.
- Microphone permission is re-checked on every resume, so returning from system
  settings after granting it updates the UI.
- Microphone failures are logged instead of silently swallowed.

## [1.0.1] - Earlier

- Real-time pitch detection with TarsosDSP YIN and a Compose UI showing the current
  note, cents deviation on a dial, and the observed vocal range.
