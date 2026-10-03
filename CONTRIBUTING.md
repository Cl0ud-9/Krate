# Contributing to Krate

Thanks for wanting to help. Bug reports, ideas, app suggestions and pull requests are all welcome. This page covers how
to get each one in, and what a pull request needs before it can be merged.

Krate is source-available, not open source: see [LICENSE](LICENSE). You may fork this repository to prepare
contributions, but not to publish or share your own builds. By opening a pull request you agree to clause 7 of the
license: you have the right to submit the change, and it becomes part of Krate under the author's terms.

## Before you start

- **Found a bug?** Search the [issues](https://github.com/Cl0ud-9/Krate/issues) first, then open one with the
  **Bug report** form. The fastest way to fill it in is from inside the app: **Settings > Feedback & bug reports**
  attaches a diagnostic report (app version, device, Android version) with no personal data.
- **Have an idea?** Open a **Feature idea** issue before writing code, so we can agree on the approach first.
  Pull requests for large changes that weren't discussed may be closed.
- **Know an app that belongs in Krate?** Use **Suggest an app** in the app, or the **App suggestion** form here.
- **Found a security problem?** Don't open a public issue. Follow [SECURITY.md](SECURITY.md).

## Building

Requirements: JDK 17 or newer and the Android SDK (Android Studio installs both).

```bash
git clone https://github.com/<your-account>/Krate.git
cd Krate
./gradlew assembleDebug
```

The debug build installs alongside nothing else and needs no signing keys. Release signing and the catalog signing key
are only needed by the maintainer; see [SETUP.md](SETUP.md).

## Making a change

1. Fork the repository and create a branch from `main` with a short descriptive name, for example
   `fix-update-badge` or `feature-share-to-suggest`.
2. Keep the change focused: one fix or feature per pull request.
3. Run the same checks CI runs, and make sure they all pass:

   ```bash
   ./gradlew ktlintCheck detekt lint testDebugUnitTest
   ```

   `./gradlew ktlintFormat` fixes most formatting problems for you.
4. Add or update unit tests for any logic you change (version comparison, catalog parsing, install routing and so on
   all have tests under `app/src/test`).
5. Try the change on a real device or emulator, in light and dark theme. For anything visual, check gesture and
   3-button navigation, and portrait and landscape.
6. Open a pull request and fill in its template.

## Code style

- Kotlin and Jetpack Compose, following the existing code around your change.
- ktlint and detekt enforce formatting and size limits (60-line functions, 120-character lines); don't suppress a
  rule to get past it without explaining why in the pull request.
- Comments explain *why*, not *what*, and stay short. No commented-out code.
- User-facing text is plain and friendly: short sentences, no jargon, no exclamation marks in errors.
- No new dependency without discussing it first in an issue: every library ends up in the APK people install.

## What gets merged

The maintainer reviews every pull request and decides what goes in. A pull request is merged when:

- CI passes,
- it has been reviewed and approved,
- it fits Krate's direction (a small, curated, trustworthy app manager), and
- it doesn't weaken how Krate checks downloads (signatures, fingerprints, the signed catalog).

It's fine for a pull request to be closed, even a good one: sometimes a change just isn't the right fit. You'll always
get a reason.

## Code of Conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
