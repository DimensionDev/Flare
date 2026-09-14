#!/usr/bin/env python3
"""Run serial, fresh-process comparisons with the same Swift app measurement loop."""

import argparse
import csv
import hashlib
import json
import math
import platform
import plistlib
import statistics
import subprocess
from collections import defaultdict
from pathlib import Path


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform", choices=["macos", "ios_simulator"], required=True)
    parser.add_argument("--optimized-app", type=Path, required=True)
    parser.add_argument("--baseline-app", type=Path)
    parser.add_argument("--simulator")
    parser.add_argument("--simulator-headless", action="store_true", help="Run an attached UIKit window without a foreground UIScene")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--allow-occluded", action="store_true")
    parser.add_argument("--scenario", action="append")
    args = parser.parse_args()
    if args.platform == "ios_simulator" and not args.simulator:
        parser.error("--simulator is required for iOS")
    args.output.mkdir(parents=True, exist_ok=True)
    cases = args.scenario or ["column_1000", "column_10000", "column_100000", "cards_10000", "row_10000"]
    variants = [("optimized", "nativekit", args.optimized_app), ("native", "native", args.optimized_app)]
    if args.baseline_app:
        variants.insert(0, ("before", "nativekit", args.baseline_app))
    metadata = {
        "host": platform.platform(), "platform": args.platform,
        "simulator": args.simulator, "rounds": args.rounds,
        "simulator_headless": args.simulator_headless, "scenarios": cases,
        "viewport_pt": [390, 780], "native_layout": "stock UICollectionViewFlowLayout / NSCollectionViewFlowLayout, declared item extents",
        "timing": "action through two stable geometry passes and CATransaction.flush; no frame pacing; excludes verification and logging",
        "binary_sha256": {},
    }
    metadata["source_commit"] = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    metadata["source_diff_sha256"] = hashlib.sha256(subprocess.check_output(["git", "diff", "--", "compose-nativekit/lazy-layout", "compose-nativekit/runtime"])).hexdigest()
    if args.simulator:
        devices = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "devices", "-j"]))["devices"]
        metadata["simulator_device"] = next(dict(runtime=runtime, name=device["name"], udid=device["udid"])
                                            for runtime, group in devices.items() for device in group if device["udid"] == args.simulator)
    for variant, _, app in variants:
        executable = app / ("Contents/MacOS/LazyBenchmarkMac" if args.platform == "macos" else "LazyBenchmarkIOS")
        metadata["binary_sha256"][variant] = digest(executable)
    (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    samples, failures = [], []
    installed = None
    expected = {"mount": 20, "idle": 20, "scroll_48pt": 180, "jump": 25, "prepend_20": 20, "resize_visible": 20}
    for round_id in range(1, args.rounds + 1):
        order = variants if round_id % 2 else list(reversed(variants))
        for scenario in cases:
            for variant, backend, app in order:
                # a394's macOS horizontal bug is already covered by the original failing benchmark.
                if args.platform == "macos" and variant == "before" and scenario == "row_10000":
                    continue
                stem = f"round-{round_id}-{variant}-{scenario}"
                log = args.output / f"{stem}.log"
                error_log = args.output / f"{stem}.stderr.log"
                flags = ["--backend", backend, "--scenario", scenario]
                if args.allow_occluded:
                    flags.append("--allow-occluded")
                print(f"Running {args.platform} {stem}", flush=True)
                try:
                    if args.platform == "macos":
                        command = ["open", "-n", "-W", "--stdout", str(log), "--stderr", str(error_log), str(app), "--args", *flags]
                        subprocess.run(command, check=True, timeout=120, capture_output=True, text=True)
                    elif args.simulator_headless:
                        command = ["xcrun", "simctl", "spawn", args.simulator, str(app / "LazyBenchmarkIOS"), "--headless", *flags]
                        with log.open("w") as output:
                            subprocess.run(command, check=True, timeout=120, stdout=output, stderr=subprocess.STDOUT)
                    else:
                        if installed != app:
                            subprocess.run(["xcrun", "simctl", "install", args.simulator, str(app)], check=True, timeout=90, capture_output=True)
                            installed = app
                        bundle_id = plistlib.loads((app / "Info.plist").read_bytes())["CFBundleIdentifier"]
                        command = ["xcrun", "simctl", "launch", "--console-pty", "--terminate-running-process", args.simulator, bundle_id, *flags]
                        with log.open("w") as output:
                            subprocess.run(command, check=True, timeout=120, stdout=output, stderr=subprocess.STDOUT)
                    text = log.read_text()
                    if f"APPLE_LAZY_COMPLETED,{backend},{scenario}," not in text or "APPLE_LAZY_FAILED," in text:
                        raise ValueError("Fixture did not complete; see its log")
                    environment = args.platform
                    if args.platform == "ios_simulator":
                        environment += "_headless" if args.simulator_headless else "_scene"
                    if args.platform == "macos":
                        environment = "macos_visible" if "visible=true" in text else "macos_occluded"
                        if environment == "macos_occluded" and not args.allow_occluded:
                            raise ValueError("Window is occluded")
                    rows = []
                    for line in text.splitlines():
                        if not line.startswith("APPLE_LAZY_SAMPLE,"):
                            continue
                        _, found_backend, found_scenario, operation, iteration, nanos, created, active = next(csv.reader([line]))
                        if found_backend != backend or found_scenario != scenario or int(nanos) <= 0:
                            raise ValueError("Sample identity or duration mismatch")
                        rows.append(dict(platform=environment, variant=variant, round=round_id, backend=backend, scenario=scenario,
                                         operation=operation, iteration=int(iteration), nanoseconds=int(nanos), created=int(created), active=int(active)))
                    for operation, count in expected.items():
                        if sorted(row["iteration"] for row in rows if row["operation"] == operation) != list(range(count)):
                            raise ValueError(f"Incomplete {operation} samples")
                    if len(rows) != sum(expected.values()):
                        raise ValueError("Unexpected sample count")
                    samples.extend(rows)
                except (subprocess.SubprocessError, ValueError, OSError) as error:
                    failures.append(dict(round=round_id, variant=variant, scenario=scenario, error=str(error)))
                    print(f"FAILED {stem}: {error}", flush=True)
    if samples:
        with (args.output / "samples.csv").open("w", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=list(samples[0]))
            writer.writeheader()
            writer.writerows(samples)
        groups = defaultdict(list)
        for row in samples:
            groups[tuple(row[key] for key in ["platform", "variant", "scenario", "operation"])].append(row)
        with (args.output / "summary.csv").open("w", newline="") as file:
            writer = csv.writer(file)
            writer.writerow(["platform", "variant", "scenario", "operation", "samples", "p50_ms", "p95_ms", "mean_created", "max_active"])
            for key, rows in sorted(groups.items()):
                values = sorted(row["nanoseconds"] / 1e6 for row in rows)
                writer.writerow([*key, len(rows), statistics.median(values), values[math.ceil(len(values) * .95) - 1],
                                 statistics.mean(row["created"] for row in rows), max(row["active"] for row in rows)])
    (args.output / "failures.json").write_text(json.dumps(failures, indent=2) + "\n")
    print(f"Valid samples: {len(samples)}; failed fixtures: {len(failures)}", flush=True)
    if failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
