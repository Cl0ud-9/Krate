# Setup

Steps here touch your own accounts/secrets, so they're written as commands for you to run rather than something done on your behalf. None of this is needed to build/run the current app shell - only for the phases that need signing, push, or CI publishing.

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
section 6) rather than as public releases or drafts on the manager repo itself. For a private
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

## 5. Patched signing keystore (needed for `.github/workflows/patched-videoapp.yml`)

Reuses your own Patched Manager keystore rather than minting a new one, so anything this pipeline
signs stays update-compatible with anything you've already installed through Patched Manager
itself. Export it from the Patched Manager app (Settings -> Import & export -> Keystore ->
Export), note its alias and both passwords from the same screen, then:

```
base64 -w0 patched-manager.keystore > patched-manager.keystore.b64
gh secret set PATCHED_KEYSTORE_BASE64 < patched-manager.keystore.b64
gh secret set PATCHED_KEYSTORE_PASSWORD
gh secret set PATCHED_KEY_ALIAS
gh secret set PATCHED_KEY_PASSWORD
```

Delete `patched-manager.keystore.b64` locally once uploaded.

## 6. Private artifacts repo (needed for `patched-videoapp.yml` and any future sibling app pipeline)

A shared **private** repo, `Cl0ud-9/manager-artifacts`, holds nothing but release binaries and
their `artifact.json` metadata for every Patched-style app - never source, never workflows, never
secrets. Each app publishes one release per retained version there (see `catalog-metadata.json`'s
`retainVersions`), tagged `<app-id>-<version>` so multiple apps can share the one repo without
colliding.

Create it once:

```
gh repo create Cl0ud-9/manager-artifacts --private --description "Private release binaries for manager's Patched-style catalog entries"
```

The publishing workflow runs in the `manager` repo but needs to create/update releases in this
*different* repo - the workflow's own ambient `GITHUB_TOKEN` is scoped only to the repo it runs in,
so it cannot do that on its own. Create a second fine-grained personal access token, scoped to just
`manager-artifacts`, with **"Contents: Read and write"** access (this one legitimately needs write,
since it's the automation's own publishing credential, not a per-installer download token), then:

```
gh secret set ARTIFACTS_REPO_TOKEN --repo Cl0ud-9/manager
```

To let a friend install a Patched-style app, add them as a collaborator on `manager-artifacts`
only (Settings -> Collaborators, or `gh repo add-collaborator`) - never on `manager` itself, and
never with anything above Read. They then follow section 4 to get their own read-only token. This
is the entire access-control surface: adding or removing that one collaborator entry is the only
thing that grants or revokes someone's ability to install these apps.
