#!/usr/bin/env python3
"""Build and run the opt-in Apple native lazy benchmark, then retain raw samples."""

import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import statistics
import subprocess
from datetime import datetime, timezone


ROOT = Path(__file__).resolve().parents[2]
FLARE_UI = ROOT / "flareUI"
FIELDS = ["platform", "scenario", "operation", "iteration", "ns", "created", "disposed", "active", "peak", "key_lookups"]


def capture(*command):
    return subprocess.check_output(command, cwd=ROOT, text=True).strip()


def run_logged(command, path, environment=None, allow_workload_failure=False):
    print(f"Running {' '.join(map(str, command))}; log: {path}", flush=True)
    with path.open("w") as log:
        result = subprocess.run(command, cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        if allow_workload_failure and result.returncode == 1 and "FLARE_BENCH_FAILED," in path.read_text():
            print(f"A workload failed its correctness check; retaining diagnostics in {path}", flush=True)
        else:
            result.check_returncode()


def summarize(output):
    samples = []
    reuse = []
    failures = []
    for path in sorted(output.glob("*-round-*.log")):
        round_number = int(path.stem.rsplit("-", 1)[1])
        lines = path.read_text().splitlines()
        completed = {line.split(",")[1] for line in lines if line.startswith("FLARE_BENCH_COMPLETED,")}
        failed = [line.split(",", 2) for line in lines if line.startswith("FLARE_BENCH_FAILED,")]
        if len(completed) + len(failed) != 5:
            raise RuntimeError(f"Incomplete run {path}: not all five workloads finished")
        failures.extend({"round": round_number, "log": path.name, "scenario": scenario, "error": error}
                        for _, scenario, error in failed)
        found = {}
        for line in lines:
            if line.startswith("FLARE_BENCH,"):
                values = line.split(",")[1:]
                if len(values) != len(FIELDS):
                    raise RuntimeError(f"Malformed sample in {path}: {line}")
                sample = dict(zip(FIELDS, values))
                if sample["scenario"] not in completed:
                    continue
                sample = {key: value if key in FIELDS[:3] else int(value) for key, value in sample.items()}
                samples.append({"round": round_number, **sample})
                found[sample["scenario"]] = found.get(sample["scenario"], 0) + 1
            elif line.startswith("FLARE_BENCH_REUSE,"):
                _, platform, scenario, operation, identities = line.split(",")
                if scenario not in completed:
                    continue
                reuse.append({"round": round_number, "platform": platform, "scenario": scenario,
                              "operation": operation, "observed_native_identities": int(identities)})
        if set(found) != completed or any(count != 285 for count in found.values()):
            raise RuntimeError(f"Incomplete samples in {path}: {found}")
    if not samples:
        raise RuntimeError("No benchmark samples found")
    with (output / "samples.csv").open("w") as file:
        writer = csv.DictWriter(file, fieldnames=["round", *FIELDS])
        writer.writeheader()
        writer.writerows(samples)
    groups = {}
    for sample in samples:
        groups.setdefault(tuple(sample[field] for field in FIELDS[:3]), []).append(sample)
    summary = []
    for (platform, scenario, operation), group in sorted(groups.items()):
        milliseconds = sorted(sample["ns"] / 1e6 for sample in group)
        summary.append({
            "platform": platform, "scenario": scenario, "operation": operation, "n": len(group),
            "p50_ms": statistics.median(milliseconds),
            "p95_ms": milliseconds[math.ceil(len(milliseconds) * .95) - 1],
            "max_ms": max(milliseconds),
            "mean_created": statistics.mean(sample["created"] for sample in group),
            "mean_disposed": statistics.mean(sample["disposed"] for sample in group),
            "peak_active": max(sample["peak"] for sample in group),
            "mean_key_lookups": statistics.mean(sample["key_lookups"] for sample in group),
        })
    with (output / "summary.csv").open("w") as file:
        writer = csv.DictWriter(file, fieldnames=summary[0].keys())
        writer.writeheader()
        writer.writerows(summary)
    (output / "reuse.json").write_text(json.dumps(reuse, indent=2) + "\n")
    (output / "failures.json").write_text(json.dumps(failures, indent=2) + "\n")
    print(f"Saved {len(samples)} samples and {len(summary)} summaries to {output}")
    if failures:
        print(f"Excluded {len(failures)} failed workloads; see failures.json")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--simulator", help="UDID of an already booted iOS simulator")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--summarize-only", action="store_true")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if args.summarize_only:
        summarize(output)
        return
    if not args.simulator or args.rounds < 1:
        parser.error("--simulator and a positive --rounds are required")
    if list(output.glob("*-round-*.log")):
        parser.error("Choose a fresh output directory to avoid mixing independent runs")
    devices = json.loads(capture("xcrun", "simctl", "list", "devices", "--json"))["devices"]
    selected = [(runtime, device) for runtime, group in devices.items() for device in group
                if device["udid"] == args.simulator]
    if len(selected) != 1 or selected[0][1]["state"] != "Booted":
        parser.error("--simulator must identify an already booted iOS simulator")
    runtime_id, device = selected[0]
    runtimes = json.loads(capture("xcrun", "simctl", "list", "runtimes", "--json"))["runtimes"]
    runtime = next(item for item in runtimes if item["identifier"] == runtime_id)
    if not args.skip_build:
        run_logged([
            str(ROOT / "gradlew"), "-p", str(FLARE_UI), "-PflareLazyBenchmark=true",
            ":flare-lazy-layout:linkBenchmarkReleaseTestMacosArm64",
            ":flare-lazy-layout:linkBenchmarkReleaseTestIosSimulatorArm64", "--console=plain",
        ], output / "build.log")
    binaries = {
        platform: FLARE_UI / "lazy-layout/build/bin" / target / "benchmarkReleaseTest/benchmark.kexe"
        for platform, target in [("macos", "macosArm64"), ("ios", "iosSimulatorArm64")]
    }
    metadata = {
        "started_utc": datetime.now(timezone.utc).isoformat(),
        "git_head": capture("git", "rev-parse", "HEAD"),
        "git_status": capture("git", "status", "--short"),
        "machine": capture("sysctl", "-n", "machdep.cpu.brand_string"),
        "memory_bytes": capture("sysctl", "-n", "hw.memsize"),
        "macos": capture("sw_vers"),
        "xcode": capture("xcodebuild", "-version"),
        "simulator_udid": args.simulator,
        "simulator": {key: device[key] for key in ["name", "udid", "state", "deviceTypeIdentifier"]},
        "runtime": {key: runtime[key] for key in ["name", "identifier", "version", "buildversion"]},
        "rounds": args.rounds,
        "binary_sha256": {platform: hashlib.sha256(path.read_bytes()).hexdigest() for platform, path in binaries.items()},
        "source_sha256": {
            str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
            for module in ["lazy-layout", "runtime", "foundation"]
            for path in sorted((FLARE_UI / module / "src").rglob("*")) if path.is_file()
        },
    }
    (output / "metadata.json").write_text(json.dumps(metadata, indent=2) + "\n")
    environment = dict(os.environ, FLARE_LAZY_BENCHMARK="1", SIMCTL_CHILD_FLARE_LAZY_BENCHMARK="1")
    environment.pop("FLARE_LAZY_BENCHMARK_SCENARIO", None)
    environment.pop("SIMCTL_CHILD_FLARE_LAZY_BENCHMARK_SCENARIO", None)
    for round_number in range(1, args.rounds + 1):
        # Alternate platform order; never measure both concurrently or during compilation.
        for platform in (["macos", "ios"] if round_number % 2 else ["ios", "macos"]):
            command = [str(binaries[platform]), "--ktest_filter=*AppleLazyBenchmark*", "--ktest_logger=SIMPLE"]
            if platform == "ios":
                command = ["xcrun", "simctl", "spawn", args.simulator, *command]
            run_logged(command, output / f"{platform}-round-{round_number}.log", environment, allow_workload_failure=True)
    summarize(output)


if __name__ == "__main__":
    main()
