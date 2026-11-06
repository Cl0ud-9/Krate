#!/usr/bin/env python3
"""Compares a SmoothnessBenchmark run with the last release's numbers, and can save it as this version's.

Run the benchmark on a real phone first (see "Before a release" in SETUP.md), then:

    python tools/smoothness/compare.py           # compare with the last saved release
    python tools/smoothness/compare.py --save    # and keep this run as the current version's numbers

Exits with 1 when something got noticeably slower than the last release on the same phone model.
"""

import argparse
import glob
import json
import os
import re
import sys
from datetime import date

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SAVED = os.path.join(ROOT, "benchmarks", "smoothness")
RESULTS = os.path.join(
    ROOT, "baselineprofile", "build", "outputs", "connected_android_test_additional_output",
    "benchmarkRelease", "connected", "*", "*benchmarkData.json",
)

# how much worse a number can get before it counts: both the share and the absolute amount must be crossed, so
# tiny numbers wobbling by a fraction of a millisecond don't fail a release
FRAME_SHARE, FRAME_MS = 0.15, 1.0
STARTUP_SHARE, STARTUP_MS = 0.10, 30.0
JANK_POINTS = 2.0


def version_name():
    text = open(os.path.join(ROOT, "app", "build.gradle.kts"), encoding="utf-8").read()
    return re.search(r'versionName = "([^"]+)"', text).group(1)


def version_key(name):
    return tuple(int(part) for part in re.findall(r"\d+", name))


def newest_results(path):
    if path:
        return path
    found = glob.glob(RESULTS)
    if not found:
        sys.exit("No benchmark results found. Run :baselineprofile:connectedBenchmarkReleaseAndroidTest first.")
    return max(found, key=os.path.getmtime)


def summarise(results_path):
    data = json.load(open(results_path, encoding="utf-8"))
    build = data["context"]["build"]
    summary = {
        "version": version_name(),
        "date": date.today().isoformat(),
        "phone": f'{build["brand"]} {build["model"]}',
        "android": build["version"]["sdk"],
        "benchmarks": {},
    }
    for bench in data["benchmarks"]:
        metrics, sampled = bench.get("metrics", {}), bench.get("sampledMetrics", {})
        numbers = {}
        if "timeToInitialDisplayMs" in metrics:
            numbers["startup_ms"] = round(metrics["timeToInitialDisplayMs"]["median"], 1)
        if "frameDurationCpuMs" in sampled:
            frames = sampled["frameDurationCpuMs"]
            numbers.update({f"frame_{p.lower()}_ms": round(frames[p], 2) for p in ("P50", "P90", "P99")})
        if "frameOverrunMs" in sampled:
            overrun = sampled["frameOverrunMs"]
            all_frames = [value for run in overrun["runs"] for value in run]
            late = sum(1 for value in all_frames if value > 0)
            numbers["janky_percent"] = round(100 * late / max(len(all_frames), 1), 1)
            numbers["overrun_p99_ms"] = round(overrun["P99"], 2)
        summary["benchmarks"][bench["name"]] = numbers
    return summary


def on_emulator(summary):
    return "sdk_gphone" in summary["phone"] or summary["phone"].startswith("google sdk")


def previous_release(current):
    saved = [p for p in glob.glob(os.path.join(SAVED, "*.json"))]
    older = [p for p in saved if version_key(os.path.basename(p)[:-5]) < version_key(current)]
    if not older:
        return None
    return json.load(open(max(older, key=lambda p: version_key(os.path.basename(p)[:-5])), encoding="utf-8"))


def worse(name, new, old):
    if name == "janky_percent":
        return new - old > JANK_POINTS
    share, amount = (STARTUP_SHARE, STARTUP_MS) if name == "startup_ms" else (FRAME_SHARE, FRAME_MS)
    return new > old * (1 + share) and new - old > amount


def compare(summary, before):
    regressions = []
    print(f'\n{"benchmark":34} {"number":16} {before["version"]:>10} {summary["version"]:>10}')
    for bench, numbers in summary["benchmarks"].items():
        old_numbers = before["benchmarks"].get(bench, {})
        for name, new in numbers.items():
            old = old_numbers.get(name)
            flag = ""
            if old is not None and worse(name, new, old):
                flag = "  <- slower"
                regressions.append(f"{bench} {name}: {old} -> {new}")
            print(f'{bench:34} {name:16} {"" if old is None else old:>10} {new:>10}{flag}')
    return regressions


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--results", help="a benchmarkData.json to use instead of the newest one")
    parser.add_argument("--save", action="store_true", help="keep this run as the current version's numbers")
    args = parser.parse_args()

    summary = summarise(newest_results(args.results))
    print(f'Krate {summary["version"]} on {summary["phone"]}, Android SDK {summary["android"]}')
    if on_emulator(summary):
        sys.exit("These numbers come from an emulator, which can't say anything about smoothness. Use a real phone.")

    before = previous_release(summary["version"])
    regressions = []
    if before is None:
        print("No earlier release saved yet, so there's nothing to compare with.")
        print(json.dumps(summary["benchmarks"], indent=2))
    elif before["phone"] != summary["phone"]:
        print(f'The last saved release ({before["version"]}) was measured on {before["phone"]}; '
              "numbers from different phones can't be compared.")
    else:
        regressions = compare(summary, before)

    if args.save:
        os.makedirs(SAVED, exist_ok=True)
        target = os.path.join(SAVED, f'{summary["version"]}.json')
        with open(target, "w", encoding="utf-8", newline="\n") as out:
            json.dump(summary, out, indent=2)
            out.write("\n")
        print(f"\nSaved as {os.path.relpath(target, ROOT)}")

    if regressions:
        print("\nSlower than the last release:\n  " + "\n  ".join(regressions))
        sys.exit(1)
    print("\nNothing got noticeably slower.")


if __name__ == "__main__":
    main()
