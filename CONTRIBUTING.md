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
- **Have a question?** Ask in [Discussions](https://github.com/Cl0ud-9/Krate/discussions).
- **Have an idea?** Open a **Feature idea** issue before writing code, so we can agree on the approach first.
  Pull requests for large changes that weren't discussed may be closed.
- **Looking for something to work on?** Issues labelled **good first issue** or **help wanted** are a good start.
- **Know an app that belongs in Krate?** Use **Suggest an app** in the app, or the **App suggestion** form here.
- **Found a security problem?** Don't open a public issue. Follow [SECURITY.md](SECURITY.md).

## Building

Requirements: JDK 17 or newer and the Android SDK (Android Studio installs both).

```bash
git clone https://github.com/<your-account>/Krate.git
cd Krate
./gradlew assembleDebug
```

The debug build needs no signing keys. It uses the same app ID as Krate's releases, so it can't install over a Krate
from the Releases page: uninstall that first, or use an emulator. Release signing and the catalog signing key are only
needed by the maintainer; see [SETUP.md](SETUP.md).

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
- User-facing text follows the writing guide below.
- No new dependency without discussing it first in an issue: every library ends up in the APK people install.

## Writing

The same rules apply to text in the app, the docs and release notes.

- **Talk to the reader.** Use "you", short sentences, and say what something does for them before how it works.
  Explain a technical term once, or leave it out.
- **Be plain and honest.** Say what Krate can't do and what it asks for. No hype ("powerful", "seamless"), no filler
  ("genuinely", "actually"). No exclamation marks, except at most one in a playful greeting or celebration line;
  never in instructions or errors.
- **A little character, in the right places.** The odd crate-and-shelf joke is welcome in headlines and empty states,
  at most once a section. Never in instructions, errors, security or licence text.
- **Punctuation and spelling.** No em dashes, and no spaced hyphen in their place: use a full stop, comma, colon or
  brackets. British spelling (colour, behaviour).
- **Formatting.** Sentence-case headings. Menu paths in bold with `>`, like **Settings > Downloads & storage**; button
  labels in italics, like *Install*. Versions as 0.4.9 in text and v0.4.9 for tags. Markdown wrapped at 120 characters.
- **Release notes.** A one-line intro, then bullets that each start with a bold phrase, most important first, with
  **Fixes.** last. Only what changed for the person using Krate: no file names, internals or refactors.

## What gets merged

The maintainer reviews every pull request and decides what goes in. A pull request is merged when:

- CI passes,
- it has been reviewed and approved,
- it fits Krate's direction (a small, curated, trustworthy app manager), and
- it doesn't weaken how Krate checks downloads (signatures, fingerprints, the signed catalog).

It's fine for a pull request to be closed, even a good one: sometimes a change just isn't the right fit. You'll always
get a reason.

Everyone whose pull request is merged is credited in the README and in the next release's notes.

## Code of Conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
