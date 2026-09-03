#!/usr/bin/env python3
"""Fetches the latest release APK for each catalog app, verifies it, and emits manifest.json.

Real errors abort the run (exit non-zero) rather than publishing a partial or fabricated
manifest - section 34 of the spec: never silently claim success.
"""
import hashlib
import json
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


def gh_get(path):
    req = urllib.request.Request(GITHUB_API + path, headers={"Accept": "application/vnd.github+json"})
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


def pick_asset(release, pattern):
    regex = re.compile(pattern)
    for asset in release.get("assets", []):
        if regex.match(asset["name"]):
            return asset
    raise RuntimeError(f"no asset matching {pattern!r} in release {release['tag_name']}")


def download(url, dest):
    req = urllib.request.Request(url, headers={"Accept": "application/octet-stream"})
    with urllib.request.urlopen(req, timeout=120) as resp, open(dest, "wb") as out:
        shutil.copyfileobj(resp, out)


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


def build_artifact(app, work_dir):
    source = app["source"]
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
    }
    return artifact, release


def main():
    metadata = json.loads(METADATA_PATH.read_text())
    WORK_DIR.mkdir(exist_ok=True)

    apps_out = []
    failures = []
    for app in metadata["apps"]:
        try:
            artifact, release = build_artifact(app, WORK_DIR)
        except (RuntimeError, urllib.error.URLError, subprocess.CalledProcessError) as exc:
            failures.append(f"{app['id']}: {exc}")
            continue

        apps_out.append(
            {
                "id": app["id"],
                "displayName": app["displayName"],
                "packageName": app["packageName"],
                "supportStatus": app["supportStatus"],
                "installationMode": app["installationMode"],
                "dependencyIds": app["dependencyIds"],
                "latestVersionName": artifact["versionName"],
                "downloadUrl": artifact["downloadUrl"],
                "sha256": artifact["sha256"],
                "certificateSha256": artifact["certificateSha256"],
                "releaseNotes": (release.get("body") or "").strip()[:2000],
                "enabled": app["enabled"],
            }
        )

    if failures:
        print("Ingestion failures (these apps will keep their last published manifest entry):", file=sys.stderr)
        for line in failures:
            print(f"  - {line}", file=sys.stderr)

    if not apps_out:
        print("No apps were successfully ingested, refusing to publish an empty manifest.", file=sys.stderr)
        sys.exit(1)

    manifest = {"schemaVersion": metadata["schemaVersion"], "apps": apps_out}
    OUTPUT_PATH.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Wrote {OUTPUT_PATH} with {len(apps_out)} app(s), {len(failures)} failure(s).")

    if failures:
        sys.exit(1)


if __name__ == "__main__":
    main()
