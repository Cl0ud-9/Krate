#!/usr/bin/env python3
"""Builds manifest.json from catalog/catalog-metadata.json: for every catalog app, the newest few
releases of each of its sources, each APK verified (sha256, signing certificate, package name) and
described (version, minimum Android version, ABIs, release notes), plus the curated announcements
and per-build overrides from catalog/announcements.json and catalog/artifact-overrides.json.

Real errors abort the run (exit non-zero) rather than publishing a partial or fabricated
manifest - section 34 of the spec: never silently claim success.
"""
import base64
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

from apk_icon import icon_png

REPO_ROOT = Path(__file__).resolve().parents[2]
CATALOG_DIR = REPO_ROOT / "catalog"
METADATA_PATH = CATALOG_DIR / "catalog-metadata.json"
ANNOUNCEMENTS_PATH = CATALOG_DIR / "announcements.json"
OVERRIDES_PATH = CATALOG_DIR / "artifact-overrides.json"
OUTPUT_PATH = REPO_ROOT / "manifest.json"
WORK_DIR = Path("manifest-work")
# asset id -> verified facts about that exact upload. A GitHub release asset's bytes can never
# change without it getting a new id, so a hit here is as good as re-downloading and re-checking
# (the workflow keeps this file between runs with actions/cache)
CACHE_PATH = WORK_DIR / "asset-cache.json"

GITHUB_API = "https://api.github.com"
# this script only ever runs in this repo's own CI, publishing this repo's own manifest-latest
# release (see RemoteCatalogRepository.kt's hardcoded MANIFEST_URL on the client side)
OWN_REPO = "Cl0ud-9/manager"
# every Patched-style app's releases live on one shared *private* repo, kept separate from this
# public repo - see SETUP.md section 5. The per-run GITHUB_TOKEN only covers this repo, so reading
# that one needs its own token
ARTIFACTS_TOKEN_ENV = "ARTIFACTS_REPO_TOKEN"
DEFAULT_RETAIN_VERSIONS = 3
RELEASE_NOTES_LIMIT = 2000
MANIFEST_SCHEMA_VERSION = 2


def _resolve_token(explicit_token):
    return explicit_token or os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")


def gh_get(path, token=None):
    headers = {"Accept": "application/vnd.github+json"}
    resolved = _resolve_token(token)
    if resolved:
        headers["Authorization"] = f"Bearer {resolved}"
    req = urllib.request.Request(GITHUB_API + path, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def version_key(version):
    return [int(part) for part in re.findall(r"\d+", version)] or [0]


def pick_asset(release, pattern):
    regex = re.compile(pattern)
    for asset in release.get("assets", []):
        if regex.match(asset["name"]):
            return asset
    return None


def _asset_request_headers(authenticated, token=None):
    headers = {"Accept": "application/octet-stream"}
    if authenticated:
        resolved = _resolve_token(token)
        if not resolved:
            raise RuntimeError("no token available to download a private release asset")
        headers["Authorization"] = f"Bearer {resolved}"
    return headers


def download(url, dest, authenticated=False, token=None):
    req = urllib.request.Request(url, headers=_asset_request_headers(authenticated, token))
    with urllib.request.urlopen(req, timeout=300) as resp, open(dest, "wb") as out:
        shutil.copyfileobj(resp, out)


def download_json(url, authenticated=False, token=None):
    req = urllib.request.Request(url, headers=_asset_request_headers(authenticated, token))
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_build_tool(name):
    sdk = Path(os.environ.get("ANDROID_HOME", "/usr/local/lib/android/sdk"))
    # the .exe/.bat names only matter for a local run on Windows
    for candidate in (name, f"{name}.exe", f"{name}.bat"):
        matches = sorted(sdk.glob(f"build-tools/*/{candidate}"), key=lambda p: version_key(p.parent.name))
        if matches:
            return str(matches[-1])
    raise RuntimeError(f"{name} not found under ANDROID_HOME/build-tools")


def certificate_sha256(apk_path):
    result = subprocess.run(
        [find_build_tool("apksigner"), "verify", "--print-certs-pem", str(apk_path)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    pem_match = re.search(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", result.stdout, re.DOTALL)
    if not pem_match:
        raise RuntimeError(f"could not find a certificate in apksigner output for {apk_path}")
    # DER output is binary, keep it as bytes end to end - no text mode, no encoding round-trip
    openssl = subprocess.run(
        ["openssl", "x509", "-outform", "DER"],
        input=pem_match.group(0).encode("ascii"),
        capture_output=True,
        check=True,
    )
    return hashlib.sha256(openssl.stdout).hexdigest()


def badging(apk_path):
    result = subprocess.run(
        [find_build_tool("aapt2"), "dump", "badging", str(apk_path)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=True,
    )
    out = result.stdout
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", out)
    if not package:
        raise RuntimeError(f"could not read package info from {apk_path}")
    min_sdk = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", out, re.MULTILINE)
    native = re.search(r"^native-code: (.+)$", out, re.MULTILINE)
    return {
        "packageName": package.group(1),
        "versionCode": int(package.group(2)),
        "minSdk": int(min_sdk.group(1)) if min_sdk else None,
        "abis": re.findall(r"'([^']+)'", native.group(1)) if native else [],
    }


def launcher_icon(apk_path):
    """The launcher icon as base64 PNG, or None - never fatal, an app without one just keeps the
    manager's lettered placeholder."""
    try:
        png = icon_png(find_build_tool("aapt2"), apk_path)
    except Exception as exc:  # noqa: BLE001 - an icon is cosmetic, see docstring
        print(f"Could not render the icon of {apk_path}: {exc}", file=sys.stderr)
        return None
    return base64.b64encode(png).decode("ascii") if png else None


def inspect_apk(cache, asset, url, authenticated, token):
    """sha256, signing certificate, badging and launcher icon for one release asset, from the cache
    when this exact asset was already inspected on an earlier run."""
    key = str(asset["id"])
    if key in cache and "icon" in cache[key]:
        return cache[key]
    apk_path = WORK_DIR / f"asset-{asset['id']}.apk"
    download(url, apk_path, authenticated=authenticated, token=token)
    facts = {
        "sha256": sha256_of(apk_path),
        "certificateSha256": certificate_sha256(apk_path),
        **badging(apk_path),
        "icon": launcher_icon(apk_path),
    }
    apk_path.unlink()
    cache[key] = facts
    return facts


class IdentityMismatch(RuntimeError):
    pass


def check_identity(app, source, facts, tag):
    if facts["packageName"] != app["packageName"]:
        raise IdentityMismatch(f"{tag}: APK is {facts['packageName']}, catalog expects {app['packageName']}")
    expected_cert = source.get("expectedCertificateSha256")
    if expected_cert and facts["certificateSha256"] != expected_cert:
        raise IdentityMismatch(f"{tag}: signed by {facts['certificateSha256']}, expected {expected_cert}")


def accept_identity(app, source, facts, tag, is_newest):
    """A mismatch on the newest release fails the app (something changed upstream that needs a
    look). An older release that doesn't match - LTE Cleaner's releases from before its package
    rename, say - could never update the installed app anyway, so it is just left out."""
    try:
        check_identity(app, source, facts, tag)
        return True
    except IdentityMismatch as exc:
        if is_newest:
            raise
        print(f"Leaving out {exc}", file=sys.stderr)
        return False


def release_notes(release):
    return (release.get("body") or "").strip()[:RELEASE_NOTES_LIMIT] or None


def artifacts_from_public_source(app, source, cache):
    releases = gh_get(f"/repos/{source['repo']}/releases?per_page=30")
    retain = source.get("retainVersions", DEFAULT_RETAIN_VERSIONS)
    artifacts = []
    for release in releases:
        if release.get("draft") or (release.get("prerelease") and not source["includePrerelease"]):
            continue
        asset = pick_asset(release, source["assetPattern"])
        if asset is None:
            continue
        url = asset["browser_download_url"]
        facts = inspect_apk(cache, asset, url, authenticated=False, token=None)
        if not accept_identity(app, source, facts, release["tag_name"], is_newest=not artifacts):
            continue
        artifacts.append(
            {
                "versionName": release["tag_name"].lstrip("v"),
                "versionCode": facts["versionCode"],
                "buildId": release["tag_name"],
                "downloadUrl": url,
                "sha256": facts["sha256"],
                "certificateSha256": facts["certificateSha256"],
                "requiresAuth": False,
                "patchesVersionName": None,
                "minSdk": facts["minSdk"],
                "maxSdk": None,
                "abis": facts["abis"],
                "label": None,
                "releaseNotes": release_notes(release),
                "publishedAt": release.get("published_at"),
                "_icon": facts.get("icon"),
            }
        )
        if len(artifacts) == retain:
            break
    if not artifacts:
        raise RuntimeError(f"no release with an asset matching {source['assetPattern']!r} in {source['repo']}")
    return artifacts


# the shared private artifacts repo (see SETUP.md section 5): releases under this source's tag
# prefix, each carrying the apk plus the artifact.json its build script wrote. A release without
# artifact.json is an unfinished upload and is skipped until its build run completes it
def artifacts_from_private_source(app, source, cache, releases_by_repo):
    token = os.environ.get(ARTIFACTS_TOKEN_ENV)
    if not token:
        raise RuntimeError(f"{ARTIFACTS_TOKEN_ENV} is not set - needed to read {source['repo']}")
    repo = source["repo"]
    if repo not in releases_by_repo:
        releases_by_repo[repo] = gh_get(f"/repos/{repo}/releases?per_page=100", token=token)
    releases = [
        r for r in releases_by_repo[repo] if not r.get("draft") and r["tag_name"].startswith(source["tagPrefix"])
    ]
    retain = source.get("retainVersions", DEFAULT_RETAIN_VERSIONS)

    artifacts = []
    for release in releases:
        asset = pick_asset(release, source["assetPattern"])
        metadata_asset = pick_asset(release, r"^artifact\.json$")
        if asset is None or metadata_asset is None:
            print(f"Skipping unfinished release {release['tag_name']}", file=sys.stderr)
            continue
        metadata = download_json(
            f"{GITHUB_API}/repos/{repo}/releases/assets/{metadata_asset['id']}", authenticated=True, token=token
        )
        url = f"{GITHUB_API}/repos/{repo}/releases/assets/{asset['id']}"
        facts = inspect_apk(cache, asset, url, authenticated=True, token=token)
        if not accept_identity(app, source, facts, release["tag_name"], is_newest=not artifacts):
            continue
        artifacts.append(
            {
                "versionName": metadata["patchedAppVersion"],
                "versionCode": facts["versionCode"],
                "buildId": metadata.get("buildId", release["tag_name"]),
                "downloadUrl": url,
                "sha256": facts["sha256"],
                "certificateSha256": facts["certificateSha256"],
                "requiresAuth": True,
                "patchesVersionName": metadata.get("patchesVersion"),
                "minSdk": metadata.get("minSdk", facts["minSdk"]),
                "maxSdk": metadata.get("maxSdk"),
                "abis": metadata.get("abis", facts["abis"]),
                "label": metadata.get("profileLabel"),
                "releaseNotes": release_notes(release),
                "publishedAt": release.get("published_at"),
                "_icon": facts.get("icon"),
            }
        )
    # one build per patches release, newest patches first, the same rule prune uses: every build
    # targets the newest app version its patches support, so the history users roll back through
    # is the last few patches releases. A patches release built more than once keeps the build for
    # the newest app version, then the most recent one
    artifacts.sort(key=lambda a: a["publishedAt"] or "", reverse=True)
    artifacts.sort(key=lambda a: version_key(a["versionName"]), reverse=True)
    artifacts.sort(key=lambda a: version_key(a["patchesVersionName"] or ""), reverse=True)
    kept, seen_patches = [], set()
    for artifact in artifacts:
        if artifact["patchesVersionName"] in seen_patches:
            continue
        seen_patches.add(artifact["patchesVersionName"])
        kept.append(artifact)
    kept = kept[:retain]
    if not kept:
        raise RuntimeError(f"no finished releases for prefix {source['tagPrefix']!r} in {repo}")
    return kept


def release_key(artifact):
    """What "newer" means for the version history: the patches release for a patched app (a new
    patches release is an update even on the same app version), the app's own version otherwise."""
    return version_key(artifact["patchesVersionName"] or artifact["versionName"])


def build_artifacts(app, cache, releases_by_repo):
    """Every source's artifacts merged, newest release first. Within one release, sources keep
    their catalog order (so the preferred build comes first - an older manager that ignores
    minSdk/maxSdk then still picks it), then the newest app version and newest build first."""
    sources = app.get("sources") or [app["source"]]
    merged = []
    for index, source in enumerate(sources):
        if source.get("type") == "private_release":
            found = artifacts_from_private_source(app, source, cache, releases_by_repo)
        else:
            found = artifacts_from_public_source(app, source, cache)
        merged += [(index, artifact) for artifact in found]
    merged.sort(key=lambda pair: pair[1]["publishedAt"] or "", reverse=True)
    merged.sort(key=lambda pair: version_key(pair[1]["versionName"]), reverse=True)
    merged.sort(key=lambda pair: pair[0])
    merged.sort(key=lambda pair: release_key(pair[1]), reverse=True)
    return [artifact for _index, artifact in merged]


def apply_overrides(app_id, artifacts, overrides):
    """catalog/artifact-overrides.json lets a specific build be withdrawn (still listed, never
    offered as the update) or relabelled without touching the release itself."""
    for override in overrides:
        if override["appId"] != app_id:
            continue
        for artifact in artifacts:
            matches_build = override.get("buildId") and override["buildId"] == artifact["buildId"]
            matches_version = override.get("versionName") and override["versionName"] == artifact["versionName"]
            if not (matches_build or matches_version):
                continue
            if override.get("withdrawn"):
                artifact["withdrawn"] = True
                artifact["withdrawnReason"] = override.get("reason")
            if override.get("label"):
                artifact["label"] = override["label"]
            if override.get("notes"):
                artifact["notes"] = override["notes"]


def active_announcements():
    if not ANNOUNCEMENTS_PATH.exists():
        return []
    now = datetime.datetime.now(datetime.timezone.utc)
    active = []
    for announcement in json.loads(ANNOUNCEMENTS_PATH.read_text(encoding="utf-8")).get("announcements", []):
        expires = announcement.get("expiresAt")
        if expires and datetime.datetime.fromisoformat(expires.replace("Z", "+00:00")) <= now:
            continue
        active.append(announcement)
    return active


# best-effort only: the fallback source for an app whose fresh ingestion fails this run (see
# main()). A first-ever run, or any fetch hiccup, just means there is nothing to fall back to
def fetch_previous_manifest():
    try:
        release = gh_get(f"/repos/{OWN_REPO}/releases/tags/manifest-latest")
        asset = pick_asset(release, r"^manifest\.json$")
        asset_url = f"{GITHUB_API}/repos/{OWN_REPO}/releases/assets/{asset['id']}"
        manifest = download_json(asset_url, authenticated=True)
        return {app["id"]: app for app in manifest.get("apps", [])}
    except Exception as exc:  # noqa: BLE001 - deliberately broad, see comment above
        print(f"Could not fetch the previously published manifest as a fallback source: {exc}", file=sys.stderr)
        return {}


def load_cache():
    try:
        return json.loads(CACHE_PATH.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def main():
    metadata = json.loads(METADATA_PATH.read_text(encoding="utf-8"))
    overrides = json.loads(OVERRIDES_PATH.read_text(encoding="utf-8")).get("overrides", []) if OVERRIDES_PATH.exists() else []
    WORK_DIR.mkdir(exist_ok=True)
    cache = load_cache()
    previous_apps_by_id = fetch_previous_manifest()
    releases_by_repo = {}

    apps_out = []
    degraded = []
    failures = []
    for app in metadata["apps"]:
        try:
            artifacts = build_artifacts(app, cache, releases_by_repo)
        except (RuntimeError, urllib.error.URLError, subprocess.CalledProcessError, KeyError) as exc:
            previous = previous_apps_by_id.get(app["id"])
            if previous is None:
                failures.append(f"{app['id']}: {exc}")
            else:
                degraded.append(f"{app['id']}: {exc}")
                apps_out.append(previous)
            continue

        apply_overrides(app["id"], artifacts, overrides)
        newest = next((a for a in artifacts if not a.get("withdrawn")), artifacts[0])
        icon = newest.get("_icon") or next((a["_icon"] for a in artifacts if a.get("_icon")), None)
        for artifact in artifacts:
            artifact.pop("_icon", None)
        apps_out.append(
            {
                "id": app["id"],
                "displayName": app["displayName"],
                "packageName": app["packageName"],
                "supportStatus": app["supportStatus"],
                "installationMode": app["installationMode"],
                "dependencyIds": app["dependencyIds"],
                "artifacts": artifacts,
                # app-level notes are the newest build's, kept for managers that predate per-build notes
                "releaseNotes": newest.get("releaseNotes"),
                "enabled": app["enabled"],
                # shown until the app is installed and its real icon can be read on the device
                "iconPng": icon,
            }
        )

    CACHE_PATH.write_text(json.dumps(cache, indent=1, sort_keys=True) + "\n", encoding="utf-8")

    if degraded:
        print("Ingestion failures (kept last published manifest entry for these apps):", file=sys.stderr)
        for line in degraded:
            print(f"  - {line}", file=sys.stderr)
    if failures:
        print("Ingestion failures with no previous entry to fall back to:", file=sys.stderr)
        for line in failures:
            print(f"  - {line}", file=sys.stderr)
    if not apps_out:
        print("No apps were successfully ingested, refusing to publish an empty manifest.", file=sys.stderr)
        sys.exit(1)

    manifest = {
        "schemaVersion": MANIFEST_SCHEMA_VERSION,
        "apps": apps_out,
        "announcements": active_announcements(),
    }
    OUTPUT_PATH.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT_PATH} with {len(apps_out)} app(s), {len(failures)} hard failure(s).")

    if failures:
        sys.exit(1)


if __name__ == "__main__":
    main()
