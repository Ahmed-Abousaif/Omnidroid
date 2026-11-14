"""Run release-like Android benchmarks and immediately preserve their output.

Install the matching target/producer APKs first; this script never installs,
uninstalls, clears app data, or launches emulation. See baselineprofile/README.md.
"""

import argparse
import datetime
from pathlib import Path
import re
import subprocess


PACKAGE = "com.omnidroid.baselineprofile"
RUNNER = PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"
TEST_PACKAGE = PACKAGE + "."
SUITES = {
    "all": "StartupBenchmarks,LibraryBenchmarks",
    "startup": "StartupBenchmarks",
    "startup-profiled": "StartupBenchmarks#startupCompilationBaselineProfiles",
    "library": "LibraryBenchmarks",
    "rendering": "LibraryBenchmarks#console5000,LibraryBenchmarks#openDetails",
    "details": "LibraryBenchmarks#openDetails",
    "profiles": "BaselineProfileGenerator",
    "profile-startup": "BaselineProfileGenerator#startup",
    "profile-library": "BaselineProfileGenerator#library",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--suite", choices=SUITES, default="all")
    parser.add_argument("--iterations", type=int, choices=range(3, 16), default=5)
    parser.add_argument("--out", default="build/performance/" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
    args = parser.parse_args()
    output = Path(args.out).resolve()
    output.mkdir(parents=True, exist_ok=True)
    adb_base = [args.adb, "-s", args.serial]

    def adb(*command):
        return subprocess.run(adb_base + list(command), check=True, capture_output=True,
                              text=True, encoding="utf-8", errors="replace").stdout

    if adb("get-state").strip() != "device":
        raise RuntimeError("The selected device is not ready")
    if RUNNER not in adb("shell", "pm", "list", "instrumentation"):
        raise RuntimeError("Install the matching benchmark producer APK first")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "am", "force-stop", "com.omnidroid")
    classes = ",".join(TEST_PACKAGE + name for name in SUITES[args.suite].split(","))
    log_path = output / "instrumentation.txt"
    print(f"Running {args.suite}; output: {output}", flush=True)
    with log_path.open("w", encoding="utf-8") as log:
        result = subprocess.run(adb_base + ["shell", "am", "instrument", "--user", "0",
            "-w", "-r", "-e", "class", classes, "-e", "iterations", str(args.iterations), RUNNER],
            stdout=log, stderr=subprocess.STDOUT)

    text = log_path.read_text(encoding="utf-8", errors="replace")
    # The runner clears this directory at the next invocation. Pull exactly the
    # reported artifacts now, including traces useful for diagnosing failures.
    paths = {path.strip() for path in re.findall(
        r"^INSTRUMENTATION_STATUS: additionalTestOutputFile_[^=]+=(.+)$", text, re.MULTILINE)}
    expected = "/storage/emulated/0/Android/media/" + PACKAGE + "/"
    # Aggregate JSON is written at instrumentation shutdown, without an
    # additionalTestOutputFile status entry on some benchmark versions.
    for name in adb("shell", "ls", expected).splitlines():
        if re.fullmatch(r"[A-Za-z0-9_.-]+-benchmarkData\.json", name):
            paths.add(expected + name)
    for remote_path in sorted(paths):
        if not remote_path.startswith(expected) or ".." in remote_path.split("/"):
            raise RuntimeError(f"Unexpected benchmark artifact path: {remote_path}")
        adb("pull", remote_path, str(output / remote_path.rsplit("/", 1)[-1]))
    success = re.search(r"^OK \([1-9]\d* tests?\)$", text, re.MULTILINE)
    missing_metrics = not args.suite.startswith("profile") and not any(
        path.endswith("-benchmarkData.json") for path in paths)
    if result.returncode or not success or not paths or missing_metrics:
        raise RuntimeError(f"Benchmark failed or produced no artifacts; inspect {log_path}")
    print(f"{success.group(0)}; saved {len(paths)} artifacts to {output}", flush=True)


if __name__ == "__main__":
    main()
