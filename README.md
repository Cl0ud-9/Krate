<p align="center">
  <img src="docs/images/krate-icon.svg" width="112" alt="Krate icon">
</p>

<h1 align="center">Krate</h1>

<p align="center">
  <b>Your apps, straight from their releases.</b><br>
  <i>No Play Store was harmed in this Krate.</i>
</p>

<p align="center">
  <a href="https://github.com/Cl0ud-9/Krate/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/Cl0ud-9/Krate?label=latest&color=5470FF"></a>
  <img alt="Android 11+" src="https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Material 3 Expressive" src="https://img.shields.io/badge/Material%203-Expressive-2B42D6">
</p>

<p align="center">
  <a href="https://github.com/Cl0ud-9/Krate/releases/latest"><img alt="Download the latest Krate APK" src="https://img.shields.io/badge/Download-Krate%20APK-5470FF?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

<p align="center">
  <img src="docs/screenshots/home.png" width="23%" alt="Home">
  <img src="docs/screenshots/apps.png" width="23%" alt="Apps">
  <img src="docs/screenshots/app-details.png" width="23%" alt="App details">
  <img src="docs/screenshots/app-details-about.png" width="23%" alt="What's in the Krate">
</p>
<p align="center">
  <img src="docs/screenshots/setup-welcome.png" width="23%" alt="Welcome to Krate">
  <img src="docs/screenshots/setup-theme.png" width="23%" alt="Pick a theme">
  <img src="docs/screenshots/updates.png" width="23%" alt="Updates">
  <img src="docs/screenshots/settings.png" width="23%" alt="Settings">
</p>

---

## What's in the Krate

Some of the best Android apps never make it to the Play Store. They live on GitHub, and keeping them up to date means
checking release pages, downloading APKs and hoping you grabbed the right file.

**Krate does that for you.** It keeps a small, hand-picked shelf of apps, checks for new versions in the background,
downloads the right build for your phone, checks that it's genuine, and hands it to Android to install. You tap
*Install*, Android asks once, and that's it.

It also has a bit of a personality. Open it in the morning and it might tell you to *rise and Krate*. Prefer it
quiet? Turn off **Playful messages** in **Settings > Appearance > Personality**.

## Features

| | |
|---|---|
| 📦 **A curated catalog** | A short shelf of apps worth having, each with a plain-language description of what it does and where it comes from. |
| ⚡ **One-tap installs and updates** | One tap downloads and installs. Updates to apps Krate installed go on without Android's prompt, and *Update all* puts dependencies in first. |
| 🧭 **Set up in a tap** | After installing, each app's permissions are a tap away, ticked off as you turn them on, with the settings worth a look and how to back them up. |
| 🔐 **Checked before it installs** | The catalog is signed with Ed25519, and every APK's SHA-256 and signing certificate are checked before Android ever sees it. |
| 🔔 **Updates find you** | A background check every few hours, and a notification when something new lands. |
| 🌙 **Updates that install themselves** | Updates to apps from Krate install a day after release, while you're not using them. It never rolls an app back or erases its data, and any app can stay manual or skip a version. On for new installs; anyone upgrading is asked once. |
| 📶 **Automatic downloads** | Updates can download ahead of time on Wi-Fi (mobile data is opt-in), so installing takes seconds. |
| 🏃 **Downloads that keep going** | Switch apps mid-download and it carries on, with progress in the notification. A dropped connection picks up where it stopped. |
| 🕰️ **Version history** | A new build misbehaving? Pick an older one from the list and go back. |
| 💾 **Automatic backups** | Optional. For apps that export their settings as text, Krate can back them up before a reinstall from scratch and put them back after, by going through the app's own backup screen with Android's accessibility access. It only acts while a backup runs. |
| 🛟 **Safe clean installs** | When an update can't go on top, Krate keeps a copy of the current version and puts it back if anything fails. |
| 🎨 **Looks the part** | Material 3 Expressive, Material You colors, light and dark themes, a floating or full-width nav bar, and optional liquid glass across the app, tinted by your colours, and a proper landscape layout. |
| 💡 **Suggest from anywhere** | Share a GitHub link to *Suggest to Krate* from your browser and the suggestion is filled in. |
| 🙈 **Hide the icon** | Keep Krate off your home screen. It keeps working, and opens from its notifications or App info. |
| 🧾 **Readable release notes** | GitHub's markup, cleaned up, with "what's new since yours" at a glance. |
| 🐞 **Feedback built in** | Report a bug from any app, with an optional diagnostic report that never includes your token. |
| 🚀 **Keeps itself fresh** | Krate updates itself from its own releases, and tells you what changed. |

## Apps in the Krate

| App | What it does | Packed from |
|---|---|---|
| **Mihon** | A free, open-source manga reader | [mihonapp/mihon](https://github.com/mihonapp/mihon) |
| **LTE Cleaner FOSS** | Frees up storage by clearing out junk files | [MDP43140/LTECleanerFOSS](https://github.com/MDP43140/LTECleanerFOSS) |
| **App Cache Cleaner** | Clears every app's cache in one go, no root needed | [bmx666/android-appcachecleaner](https://github.com/bmx666/android-appcachecleaner) |
| **MicroG RE** | Google account sign-in for apps that need it, without Google Play services | [MorpheApp/MicroG-RE](https://github.com/MorpheApp/MicroG-RE) |

Know an app that belongs here? Suggest it from **Settings > Suggest an app**, or
[open an issue](https://github.com/Cl0ud-9/Krate/issues/new).

## Get Krate

1. **Download** the latest APK from [Releases](https://github.com/Cl0ud-9/Krate/releases/latest).
2. **Pause Play Protect, then open it** and let your browser or files app install it when Android asks. See
   [Play Protect](#play-protect) below for why and how.
3. **Follow the setup.** It takes about a minute and asks for what Krate needs: permission to install apps, and on
   Android 13 or newer, notifications.

After that, Krate updates itself, so you only ever download it once.

**Needs:** Android 11 or newer.

### Play Protect

Google Play Protect currently blocks Krate with a "Harmful app blocked" message. It flags Krate because Krate installs
apps from outside the Play Store. Krate checks every app it installs against a signed list, and its code is public
right here, so you can see exactly what it does. If you'd rather not continue, that's a fine choice too.

To install or update Krate:

1. Open the **Play Store**, tap your profile picture, then **Play Protect** and the settings icon at the top right.
2. Turn off **Scan apps with Play Protect**.
3. Install or update Krate.
4. Turn **Scan apps with Play Protect** back on.

Krate walks you through this when it updates itself, opens the Play Protect screen for you, and reminds you to switch
scanning back on once the update is in. Apps you install through Krate aren't affected.

## How it works

```
GitHub releases ──► catalog (built every 2 hours, signed) ──► Krate on your phone
                                                                 │
                         checks the signature, finds updates ◄───┘
                         downloads the APK, checks SHA-256 and signing certificate
                         hands it to Android's installer, you confirm
```

- A GitHub Actions workflow reads each app's releases and publishes a signed catalog.
- Krate refuses any catalog whose signature doesn't match the key built into the app.
- Downloads come straight from each app's own GitHub releases.
- Installing uses Android's own installer. You confirm every new app; updates to apps Krate installed can go on
  by themselves if you leave automatic updates on.

## Privacy

- No accounts, no ads, no analytics, no tracking.
- Krate only talks to GitHub: the catalog, releases and its own updates.
- If you add a GitHub token, it's stored encrypted on your device and only ever sent to GitHub.
- Diagnostic reports are only created when you ask for one, and you choose where to send them.
- Automatic backups are off until you turn them on. They stay in Krate on your phone, and the accessibility access they use only looks at the app being backed up, only while it runs.

## FAQ

<details>
<summary><b>Does Krate install things on its own?</b></summary>

Only updates, and only if you want it. Every new app needs your tap and Android's confirmation. With automatic
updates on, updates to apps Krate installed go on by themselves a day after release, while you're not using the
app. You can switch that off in <b>Settings > Downloads & storage</b>, or per app in its details.
</details>

<details>
<summary><b>Do downloads keep going if I leave Krate?</b></summary>

Yes. A download you start keeps going while you use other apps, with its progress in the notification, and a dropped
connection resumes where it stopped instead of starting over.
</details>

<details>
<summary><b>I hid the icon. How do I open Krate?</b></summary>

Tap any Krate notification, or go to your phone's <b>Settings > Apps > Krate</b> and tap <b>Open</b>. To bring the icon
back, turn off <b>Hide Krate's icon</b> in <b>Settings > Appearance</b>.
</details>

<details>
<summary><b>Can I use mobile data for automatic downloads?</b></summary>

Yes, in <b>Settings > Downloads & storage > Use mobile data too</b>. It's off by default, and it never runs while
roaming, with Data Saver on, or in Battery Saver.
</details>

## Build it yourself

```bash
git clone https://github.com/Cl0ud-9/Krate.git
cd Krate
./gradlew assembleDebug
```

Requires JDK 17 or newer. The same checks CI runs on every push:

```bash
./gradlew ktlintCheck detekt lint testDebugUnitTest
```

Release signing, the catalog signing key and the GitHub Actions secrets are covered in [SETUP.md](SETUP.md).

**Built with** Kotlin, Jetpack Compose, Material 3 Expressive, WorkManager, OkHttp, kotlinx.serialization, DataStore and
Tink, checked by ktlint, detekt and Android Lint.

## Support Krate

Krate runs on coffee. If it saves you some tapping, you can top it up. Bug reports and app suggestions help just as
much.

<a href='https://ko-fi.com/Z3E027P5XA' target='_blank'><img height='36' style='border:0px;height:36px;' src='https://storage.ko-fi.com/cdn/kofi1.png?v=6' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>

## Credits

- **Google Sans Flex**, the typeface throughout Krate (SIL Open Font License 1.1)
- **Font Awesome Free**, for the Krate mark, the notification rocket and the GitHub mark (CC BY 4.0)
- **Material Symbols**, for icons across the app (Apache License 2.0)
- And every developer whose app sits on the shelf. Krate only carries the boxes.

Full notices are in [licenses/](licenses).

## License

Krate is **source-available**: Copyright 2026 Cloud/9, all rights reserved. You can read the source, build it for your
own personal, non-commercial use, and fork it to contribute back. Redistributing it or anything built from it,
publishing rebranded or modified builds, putting it on any app store, or using it to train AI models is not allowed
without written permission. See [LICENSE](LICENSE) for the full terms. These terms apply from 0.4.8 on; earlier
versions were released under the Apache License 2.0.

Bundled fonts, icons and libraries keep their own licenses, listed above and in [NOTICE](NOTICE).

Builds from anywhere but this repository's releases aren't supported. If you use one, ask whoever made it for help.

## Contributing

Bug reports, ideas and pull requests are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md), and please read the
[Code of Conduct](CODE_OF_CONDUCT.md). Security problems go through [SECURITY.md](SECURITY.md), not public issues.

<p align="center"><sub>Made by <a href="https://github.com/Cl0ud-9">Cloud/9</a>, for friends, shaped by what they ask for.</sub></p>
