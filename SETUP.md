# Setup

These steps involve your own accounts and secrets, so each is written as a command to run yourself. None of it is needed to build and run the app locally - only for signing, push notifications, and CI publishing.

## 1. Release signing keystore (needed from Phase 4 onward)

Generate it locally, keep it outside the repo (`.gitignore` already excludes `*.jks`/`*.keystore`):

```
keytool -genkeypair -v -keystore release.jks -alias manager-release -keyalg RSA -keysize 4096 -validity 10000
```

Pick your own store/key passwords when prompted - don't reuse them elsewhere. Back the file up somewhere offline (per [spec section 20](spec/master-specification.md#20-apk-signing-strategy)); losing it means you can never publish an update under the same signing identity again.

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
2. Add an Android app with package name `dev.cl0ud9.manager`.
3. Download `google-services.json`, place it at `app/google-services.json`. It's gitignored - each environment (your machine, CI) needs its own copy or a secret-backed copy.
4. For CI, base64-encode it the same way as the keystore and store as `GOOGLE_SERVICES_JSON_BASE64`; the workflow decodes it before build.

## 3. Manifest signing key (needed from Phase 2, per amendment 44.3)

```
openssl genpkey -algorithm ed25519 -out manifest-signing.key
openssl pkey -in manifest-signing.key -pubout -out manifest-signing.pub
```

Keep `manifest-signing.key` as a CI secret (`MANIFEST_SIGNING_KEY`), never commit it. The public key (`manifest-signing.pub`) gets baked into the app as a resource - that one's fine to commit once Phase 2 wires it in.

## 4. GitHub access token (needed to install any Patched-style catalog entry)

Videoapp Patched, and any future sibling app built the same way (Videoapp Music, Photos, etc.), are
published as normal releases on one shared **private** repo (`Cl0ud-9/manager-artifacts`, see
section 5) rather than as public releases or drafts on the manager repo itself. For a private
repo, plain read access is enough to view and download a published release - unlike a draft
release, which GitHub only exposes to accounts with push access.

To install one of these apps you need to:
1. Be added as a collaborator on `Cl0ud-9/manager-artifacts` (ask whoever manages that repo - it's
   never self-service, since it's private).
2. Create a fine-grained personal access token scoped to just that repo with **"Contents:
   Read-only"** access (github.com -> Settings -> Developer settings -> Fine-grained tokens).
3. Paste it into the app's own Settings > GitHub access.

This is a per-installer credential entered in the app itself, not a CI secret - skip this section
entirely if you don't plan to install one of those entries. A token can never grant more access
than its owner's actual collaborator role, so step 1 has to happen before step 2 does anything.

## 5. Private artifacts repo (needed for `manifest.yml` to see any Patched-style catalog entry)

The actual patch-building pipeline for Videoapp Patched (and any future sibling app built the same
way) does **not** live in this repo at all. It lives entirely in
[Cl0ud-9/manager-artifacts](https://github.com/Cl0ud-9/manager-artifacts) - a separate, genuinely
private repo, deliberately, so that manager's public source and public Actions history never show
any trace of an automated Videoapp-patching pipeline (the mechanics, not just the resulting APK).
See that repo's own `README.md`/`SETUP.md` for its build script, its workflow, and its signing
secrets - none of that is this repo's concern.

What manager *does* still need: `catalog/scripts/generate_manifest.py` (run by `manifest.yml`
here) has to read whatever's published on that other repo, and the default per-run `GITHUB_TOKEN`
can't see a different repo than the one it's running in. Create a fine-grained personal access
token scoped to just `manager-artifacts`, with **"Contents: Read-only"** access - this workflow
only ever reads from that repo, never writes to it - then:

```
gh secret set ARTIFACTS_REPO_TOKEN --repo Cl0ud-9/manager
```

To let a friend install a Patched-style app, add them as a collaborator on `manager-artifacts`
(never on `manager` itself, never with anything above Read) - see that repo's `SETUP.md`. They
then follow section 4 above to get their own read-only token. That collaborator entry is the
entire access-control surface: adding or removing it is the only thing that grants or revokes
someone's ability to install these apps.
