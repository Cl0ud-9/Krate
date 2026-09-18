#!/usr/bin/env python3
"""Fetches the latest release APK for each catalog app, verifies it, and emits manifest.json.

Real errors abort the run (exit non-zero) rather than publishing a partial or fabricated
manifest - section 34 of the spec: never silently claim success.
"""
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

REPO_ROOT = Path(__file__).resolve().parents[2]
METADATA_PATH = REPO_ROOT / "catalog" / "catalog-metadata.json"
OUTPUT_PATH = REPO_ROOT / "manifest.json"
WORK_DIR = Path("manifest-work")

GITHUB_API = "https://api.github.com"
# this script only ever runs in this repo's own CI, publishing this repo's own manifest-latest
# release (see RemoteCatalogRepository.kt's hardcoded MANIFEST_URL on the client side) - not a
# per-app source, so it isn't read from catalog-metadata.json
OWN_REPO = "Cl0ud-9/manager"
# every Patched-style app's releases (past and present) live on one shared *private* repo, kept
# entirely separate from OWN_REPO's public source/workflows/secrets - see SETUP.md section 6. The
# default per-run GITHUB_TOKEN is scoped only to the repo a workflow runs in, so reading this
# different repo needs its own token
ARTIFACTS_TOKEN_ENV = "ARTIFACTS_REPO_TOKEN"
DEFAULT_RETAIN_VERSIONS = 3


def _resolve_token(explicit_token):
    return explicit_token or os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")


def gh_get(path, token=None):
    # Authenticated requests get 1000 req/hour instead of the 60 req/hour anonymous limit -
    # this runs on a 15-minute schedule (section 10), so staying anonymous risks 403s under load.
    headers = {"Accept": "application/vnd.github+json"}
    resolved = _resolve_token(token)
    if resolved:
        headers["Authorization"] = f"Bearer {resolved}"
    req = urllib.request.Request(GITHUB_API + path, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def pick_release(repo, include_prerelease):
    releases = gh_get(f"/repos/{repo}/releases?per_page=20")
    for release in releases:
        if release.get("draft"):
            continue
        if release.get("prerelease") and not include_prerelease:
            continue
        return release
    raise RuntimeError(f"no matching release found for {repo}")


# every release whose tag starts with tag_prefix, newest-first by the numeric version encoded in
# the tag (not creation date, so a same-day multi-build day still orders correctly) - this is what
# lets one shared private repo hold releases for several different apps (each with its own prefix)
# without them colliding, and lets each app keep more than just its single latest version
def list_releases(repo, tag_prefix, token):
    releases = gh_get(f"/repos/{repo}/releases?per_page=100", token=token)
    matching = [r for r in releases if not r.get("draft") and r["tag_name"].startswith(tag_prefix)]
    matching.sort(key=lambda r: _version_sort_key(r["tag_name"][len(tag_prefix):]), reverse=True)
    return matching


def _version_sort_key(version):
    return [int(part) for part in re.findall(r"\d+", version)] or [0]


def pick_asset(release, pattern):
    regex = re.compile(pattern)
    for asset in release.get("assets", []):
        if regex.match(asset["name"]):
            return asset
    raise RuntimeError(f"no asset matching {pattern!r} in release {release['tag_name']}")


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
    with urllib.request.urlopen(req, timeout=120) as resp, open(dest, "wb") as out:
        shutil.copyfileobj(resp, out)


# small release assets (e.g. artifact.json) that are read directly rather than saved to disk first
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


def find_apksigner():
    android_home = Path(__import__("os").environ.get("ANDROID_HOME", "/usr/local/lib/android/sdk"))
    matches = list(android_home.glob("build-tools/*/apksigner"))
    if not matches:
        raise RuntimeError("apksigner not found under ANDROID_HOME/build-tools")
    matches.sort()
    return str(matches[-1])


def certificate_sha256(apksigner_path, apk_path):
    result = subprocess.run(
        [apksigner_path, "verify", "--print-certs-pem", str(apk_path)],
        capture_output=True,
        text=True,
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


def build_artifact_from_public_release(app, source, work_dir):
    release = pick_release(source["repo"], source["includePrerelease"])
    asset = pick_asset(release, source["assetPattern"])

    apk_path = work_dir / f"{app['id']}.apk"
    download(asset["browser_download_url"], apk_path)

    apksigner = find_apksigner()
    artifact = {
        "versionName": release["tag_name"].lstrip("v"),
        "downloadUrl": asset["browser_download_url"],
        "sha256": sha256_of(apk_path),
        "certificateSha256": certificate_sha256(apksigner, apk_path),
        "requiresAuth": False,
        "patchesVersionName": None,
    }
    return [(artifact, release)]


# the shared private artifacts repo (see SETUP.md section 6) - up to source["retainVersions"]
# releases matching this app's tagPrefix, newest first, each downloaded and verified the same way
# a single-artifact app would be. artifact.json (uploaded alongside the apk by whichever build
# script produced it) carries both the patched app's own version and the version of the tool that
# patched it - surfaced as two separate fields rather than picking one over the other
def build_artifacts_from_private_release(app, source, work_dir):
    token = os.environ.get(ARTIFACTS_TOKEN_ENV)
    if not token:
        raise RuntimeError(f"{ARTIFACTS_TOKEN_ENV} is not set - needed to read {source['repo']}")
    repo = source["repo"]
    retain = source.get("retainVersions", DEFAULT_RETAIN_VERSIONS)
    releases = list_releases(repo, source["tagPrefix"], token)[:retain]
    if not releases:
        raise RuntimeError(f"no releases found for prefix {source['tagPrefix']!r} in {repo}")

    apksigner = find_apksigner()
    results = []
    for release in releases:
        asset = pick_asset(release, source["assetPattern"])
        asset_url = f"{GITHUB_API}/repos/{repo}/releases/assets/{asset['id']}"
        apk_path = work_dir / f"{app['id']}-{release['tag_name']}.apk"
        download(asset_url, apk_path, authenticated=True, token=token)

        artifact_json_asset = pick_asset(release, r"^artifact\.json$")
        artifact_json_url = f"{GITHUB_API}/repos/{repo}/releases/assets/{artifact_json_asset['id']}"
        artifact_metadata = download_json(artifact_json_url, authenticated=True, token=token)

        artifact = {
            "versionName": artifact_metadata["patchedAppVersion"],
            "downloadUrl": asset_url,
            "sha256": sha256_of(apk_path),
            "certificateSha256": certificate_sha256(apksigner, apk_path),
            "requiresAuth": True,
            "patchesVersionName": artifact_metadata.get("patchesVersion"),
        }
        results.append((artifact, release))
    return results


def build_artifacts(app, work_dir):
    source = app["source"]
    if source.get("type") == "private_release":
        return build_artifacts_from_private_release(app, source, work_dir)
    return build_artifact_from_public_release(app, source, work_dir)


# best-effort only, and never fatal on its own: this is purely the fallback source for an app whose
# fresh ingestion fails this run (see main()) - a first-ever run with no manifest-latest release
# yet, or any fetch hiccup, just means there is nothing to fall back to, which main() already
# handles as a hard failure for that one app
def fetch_previous_manifest():
    try:
        release = gh_get(f"/repos/{OWN_REPO}/releases/tags/manifest-latest")
        asset = pick_asset(release, r"^manifest\.json$")
        asset_url = f"{GITHUB_API}/repos/{OWN_REPO}/releases/assets/{asset['id']}"
        manifest = download_json(asset_url, authenticated=True)
        return {app["id"]: app for app in manifest.get("apps", [])}
    except Exception as exc:  # noqa: BLE001 - deliberately broad, see docstring above
        print(f"Could not fetch the previously published manifest as a fallback source: {exc}", file=sys.stderr)
        return {}


def main():
    metadata = json.loads(METADATA_PATH.read_text())
    WORK_DIR.mkdir(exist_ok=True)
    previous_apps_by_id = fetch_previous_manifest()

    apps_out = []
    degraded = []
    failures = []
    for app in metadata["apps"]:
        try:
            results = build_artifacts(app, WORK_DIR)
        except (RuntimeError, urllib.error.URLError, subprocess.CalledProcessError) as exc:
            previous = previous_apps_by_id.get(app["id"])
            if previous is None:
                failures.append(f"{app['id']}: {exc}")
            else:
                degraded.append(f"{app['id']}: {exc}")
                apps_out.append(previous)
            continue

        newest_release = results[0][1]
        apps_out.append(
            {
                "id": app["id"],
                "displayName": app["displayName"],
                "packageName": app["packageName"],
                "supportStatus": app["supportStatus"],
                "installationMode": app["installationMode"],
                "dependencyIds": app["dependencyIds"],
                "artifacts": [
                    {
                        "versionName": artifact["versionName"],
                        "downloadUrl": artifact["downloadUrl"],
                        "sha256": artifact["sha256"],
                        "certificateSha256": artifact["certificateSha256"],
                        "requiresAuth": artifact["requiresAuth"],
                        "patchesVersionName": artifact.get("patchesVersionName"),
                    }
                    for artifact, _release in results
                ],
                "releaseNotes": (newest_release.get("body") or "").strip()[:2000],
                "enabled": app["enabled"],
            }
        )

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

    manifest = {"schemaVersion": metadata["schemaVersion"], "apps": apps_out}
    OUTPUT_PATH.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Wrote {OUTPUT_PATH} with {len(apps_out)} app(s), {len(failures)} hard failure(s).")

    if failures:
        sys.exit(1)


if __name__ == "__main__":
    main()
