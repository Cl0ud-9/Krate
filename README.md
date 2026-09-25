# Krate

Krate is a curated Android application deployment and update manager. Full product/architecture spec lives in [spec/master-specification.md](spec/master-specification.md), with agreed fixes in [spec/amendment-44.md](spec/amendment-44.md).

Zero paid infrastructure by design: GitHub Actions + GitHub Releases for CI/CD and artifact hosting, Firebase Cloud Messaging for push, everything else on-device.

## Status

Scaffold stage. App shell (Compose + Material 3, bottom nav with Home/Apps/Updates/Settings placeholders) builds and runs, CI lints and assembles a debug APK. No manifest ingestion, download, or install logic yet - see [spec/master-specification.md#36-implementation-phases](spec/master-specification.md#36-implementation-phases) for the build order.

## Stack

- Kotlin 2.4.0, Jetpack Compose, Material 3
- minSdk 30 (Android 11), compileSdk/targetSdk 37
- AGP 9.1.1, Gradle 9.1.0
- ktlint + detekt + Android Lint, enforced in CI
- GitHub Actions for CI, GitHub Releases for artifact/manifest hosting
- Firebase Cloud Messaging for push (added when Phase 9 lands)

## Building

Open the project root in Android Studio and let it sync - the Gradle wrapper pulls everything else. To build from a terminal:

```
./gradlew assembleDebug
```

Requires JDK 17+. Set `JAVA_HOME` if the IDE's bundled JDK isn't on `PATH`.

## Linting

```
./gradlew ktlintCheck   # or ktlintFormat to auto-fix
./gradlew detekt
./gradlew lint
```

All three run in CI on every push/PR to `main`.

## Application ID

`dev.cl0ud9.manager`. This is set once and is awkward to change after real installs exist, so it stays fixed going forward.

## Setup notes

Steps that touch external accounts or secrets (Firebase project, release signing keystore, GitHub Actions secrets) aren't automatable from here - see [SETUP.md](SETUP.md) for exact commands and where each one goes.
