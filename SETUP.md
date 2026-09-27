# Setup

These steps involve your own accounts and secrets, so each is written as a command to run yourself. None of it is needed to build and run the app locally - only for signing, push notifications, and CI publishing.

## 1. Release signing keystore (needed from Phase 4 onward)

Generate it locally, keep it outside the repo (`.gitignore` already excludes `*.jks`/`*.keystore`):

```
keytool -genkeypair -v -keystore release.jks -alias krate-release -keyalg RSA -keysize 4096 -validity 10000
```

Pick your own store/key passwords when prompted - don't reuse them elsewhere. Back the file up somewhere offline; losing it means you can never publish an update under the same signing identity again.

Base64-encode it for CI:

```
base64 -w0 release.jks > release.jks.b64
```

Add as GitHub Actions secrets (repo Settings -> Secrets and variables -> Actions), or via `gh`:

```
gh secret set RELEASE_KEYSTORE_BASE64 < release.jks.b64
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS
gh secret set RELEASE_KEY_PASSWORD
```

Delete `release.jks.b64` locally once uploaded.

## 2. Firebase project (needed from Phase 9, FCM)

1. Create a project at https://console.firebase.google.com (free Spark plan - FCM has no usage cap on it).
2. Add an Android app with package name `dev.cl0ud9.krate`.
3. Download `google-services.json`, place it at `app/google-services.json`. It's gitignored - each environment (your machine, CI) needs its own copy or a secret-backed copy.
4. For CI, base64-encode it the same way as the keystore and store as `GOOGLE_SERVICES_JSON_BASE64`; the workflow decodes it before build.

## 3. Manifest signing key (needed from Phase 2, per amendment 44.3)

```
openssl genpkey -algorithm ed25519 -out manifest-signing.key
openssl pkey -in manifest-signing.key -pubout -out manifest-signing.pub
```

Keep `manifest-signing.key` as a CI secret (`MANIFEST_SIGNING_KEY`), never commit it. The public key (`manifest-signing.pub`) gets baked into the app as a resource - that one's fine to commit once Phase 2 wires it in.

## 4. Invite-only catalog entries (optional)

Some catalog entries are published privately, in a separate signed catalog that Krate fetches only while a GitHub token
is saved in **Settings > GitHub access**. Building and publishing them, and granting access, is covered in that
private repo's own docs, not here.
