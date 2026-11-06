# Setup

None of this is needed to build and run Krate yourself. It covers what the maintainer needs to sign releases and
publish the catalog. Each step is a command to run yourself, because it involves your own keys and accounts.

## 1. Release signing key

Krate's releases are signed on the maintainer's machine, not in CI. Make the key once:

```
keytool -genkeypair -v -keystore manager-release.keystore -alias manager-release -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12
```

Then create `keystore.properties` next to `build.gradle.kts`:

```
storeFile=manager-release.keystore
storePassword=<the password you chose>
keyAlias=manager-release
keyPassword=<the same password>
```

Both files are ignored by git. `./gradlew assembleRelease` signs with them, and a release build without them comes out
unsigned rather than signed with the wrong key.

Back both up somewhere safe and offline. Every Krate release has to be signed with this same key: if it's lost, nobody
can update in place and everyone has to reinstall Krate.

## 2. Catalog signing key

The public catalog is signed with an Ed25519 key. Its public half is built into Krate, and the private half is a
GitHub Actions secret:

```
openssl genpkey -algorithm ed25519 -out manifest-signing.key
openssl pkey -in manifest-signing.key -pubout -out manifest-signing.pub
gh secret set MANIFEST_SIGNING_KEY < manifest-signing.key
```

Never commit the private key. Changing it means shipping a Krate update with the new public key first.

Optionally, `RELEASE_NOTES_BLOCKLIST` (a comma-separated list of words) keeps upstream release notes that mention any
of them out of the catalog. Krate links to that release on GitHub instead:

```
gh secret set RELEASE_NOTES_BLOCKLIST
```

## 3. Invite-only apps

Some apps are published privately, in a separate signed catalog that Krate fetches only while a GitHub token is saved
in **Settings > GitHub access**. Building them, signing that catalog and giving people access are covered in the
private repo's own docs.

## 4. Before a release

Unit tests and lint can't tell whether Krate still feels smooth, so every release gets three more checks.

**UI tests.** CI runs them on an Android 14 emulator for every push (the *ui-tests* job). They include the shape of a
back swipe held halfway: the page has to slide right and stay close to full size, as the back button's animation
does. To run them yourself, on an emulator:

```
./gradlew connectedDebugAndroidTest
```

**Smoothness numbers, on a real phone.** An emulator's numbers say nothing about smoothness. Connect the phone you
measured the last release on, with gesture navigation on and the screen unlocked, then:

```
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.cl0ud9.krate.baselineprofile.SmoothnessBenchmark
python tools/smoothness/compare.py --save
```

It measures a cold start, tab switches, scrolling the app list, and real back swipes from Settings and an app's page,
with liquid glass off and on. It takes about half an hour. The benchmark build installs as `dev.cl0ud9.krate.bench`, next
to your own Krate, never over it. `compare.py` sets the run against the last release's numbers in
`benchmarks/smoothness/` and fails if anything got noticeably slower; `--save` keeps this run as the new version's
numbers, so commit that file with the release.

On Windows, a long project path can stop the results being copied off the phone. Map the folder to a drive letter
first, for example `subst K: .`, and run the commands from `K:\`.

**Your own eyes.** Swipe back from Settings, a Settings page and an app's page, start a swipe and change your mind,
and switch tabs, with liquid glass off and on. Numbers can match while something still looks wrong.
