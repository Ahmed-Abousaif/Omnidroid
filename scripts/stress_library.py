"""Run isolated, real-screen capacity trials on one connected Android device.

Build/install the freeDynamic debug app and its androidTest APK first. No ROMs or
user database rows are created or removed. See docs/LIBRARY_SCREEN_TECHNICAL_GUIDE.md.
"""

import argparse
import datetime
import json
from pathlib import Path
import re
import subprocess
import time


PACKAGE = "com.omnidroid.debug"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST = "com.omnidroid.app.testing.LibraryCapacityTest#capacity"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True, help="Explicit device serial; never picks a device implicitly")
    parser.add_argument("--counts", default="1000,5000,10000,25000,50000,100000")
    parser.add_argument("--layouts", default="CONSOLE,GRID,LIST")
    parser.add_argument("--orientations", default="portrait,landscape")
    parser.add_argument("--swipes", type=int, default=30)
    parser.add_argument("--zoom", type=int, choices=(0, 1, 2), default=2)
    parser.add_argument("--featured", action="store_true", help="Include the pinned Continue item")
    parser.add_argument("--screenshots", action="store_true", help="Save the rendered initial viewport")
    parser.add_argument("--timeout", type=int, default=240, help="Per-trial wall timeout in seconds")
    parser.add_argument("--out", default="build/library-capacity/" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S"))
    parser.add_argument("--bisect", action="store_true", help="Refine a reproducible app crash to within 100 games")
    args = parser.parse_args()
    counts = sorted(set(int(value) for value in args.counts.split(",")))
    layouts = args.layouts.upper().split(",")
    orientations = args.orientations.split(",")
    if not counts or not all(1 <= count <= 10_000_000 for count in counts):
        parser.error("counts must be between 1 and 10,000,000")
    if not set(layouts) <= {"CONSOLE", "GRID", "LIST"}:
        parser.error("layouts must be CONSOLE,GRID,LIST")
    if not set(orientations) <= {"portrait", "landscape"}:
        parser.error("orientations must be portrait,landscape")
    if args.swipes < 0 or args.timeout < 1:
        parser.error("swipes must be nonnegative and timeout must be positive")
    output = Path(args.out).resolve()
    output.mkdir(parents=True, exist_ok=True)
    adb_base = [args.adb, "-s", args.serial]

    def adb(*command, timeout=30):
        result = subprocess.run(adb_base + list(command), capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stderr or result.stdout)
        return result.stdout.strip()

    if adb("get-state") != "device":
        raise RuntimeError("The selected device is not ready")
    if RUNNER not in adb("shell", "pm", "list", "instrumentation"):
        raise RuntimeError("Install both debug and androidTest APKs before running")
    # Wake the device. A secure lock screen still needs to be unlocked by the user.
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    package_info = adb("shell", "dumpsys", "package", PACKAGE)
    device = {
        "serial": args.serial,
        "model": adb("shell", "getprop", "ro.product.model"),
        "android": adb("shell", "getprop", "ro.build.version.release"),
        "memory": adb("shell", "cat", "/proc/meminfo"),
        "display": adb("shell", "wm", "size"),
        "density": adb("shell", "wm", "density"),
        "package": [line.strip() for line in package_info.splitlines()
                    if re.match(r"\s*(versionCode|versionName|flags|pkgFlags|codePath)=", line)],
        "workload": {"counts": counts, "layouts": layouts, "orientations": orientations,
                     "swipes": args.swipes, "timeout_seconds": args.timeout,
                     "zoom": args.zoom, "featured": args.featured},
    }
    (output / "device.json").write_text(json.dumps(device, indent=2), encoding="utf-8")
    results = []

    def trial(count, layout, orientation):
        name = f"{len(results):03d}-{layout}-{orientation}-{count}"
        print(f"START {name}", flush=True)
        adb("shell", "am", "force-stop", PACKAGE)
        log_path = output / (name + ".logcat.txt")
        started = time.monotonic()
        with log_path.open("w", encoding="utf-8") as log_file:
            logger = subprocess.Popen(adb_base + ["logcat", "-T", "1", "-v", "threadtime",
                "-s", "LibraryCapacity:I", "AndroidRuntime:E", "libc:F", "DEBUG:F"],
                stdout=log_file, stderr=subprocess.STDOUT)
            try:
                command = adb_base + ["shell", "am", "instrument", "-w", "-r",
                    "-e", "class", TEST, "-e", "games", str(count),
                    "-e", "layout", layout, "-e", "orientation", orientation,
                    "-e", "swipes", str(args.swipes), "-e", "zoom", str(args.zoom),
                    "-e", "featured", str(args.featured).lower(),
                    "-e", "capture", str(args.screenshots).lower(), RUNNER]
                process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    text=True, encoding="utf-8", errors="replace")
                deadline = time.monotonic() + args.timeout
                frames = "Frame statistics unavailable before the instrumentation process exited."
                timed_out = False
                # Instrumentation can terminate the target process as soon as JUnit finishes.
                # Sample gfxinfo while it is alive instead of querying after it has exited.
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        process.kill()
                        instrument, _ = process.communicate()
                        timed_out = True
                        break
                    try:
                        instrument, _ = process.communicate(timeout=min(2, remaining))
                        break
                    except subprocess.TimeoutExpired:
                        snapshot = adb("shell", "dumpsys", "gfxinfo", PACKAGE, timeout=10)
                        if "Total frames rendered:" in snapshot:
                            frames = snapshot
                (output / (name + ".instrumentation.txt")).write_text(instrument, encoding="utf-8")
                (output / (name + ".frames.txt")).write_text(frames, encoding="utf-8")
                screenshot = re.search(r"INSTRUMENTATION_STATUS: capacity_screenshot=([^\r\n]+)", instrument)
                if screenshot:
                    adb("pull", screenshot.group(1), str(output / (name + ".png")))
            finally:
                logger.terminate()
                logger.wait(timeout=10)
                adb("shell", "am", "force-stop", PACKAGE)
        logs = log_path.read_text(encoding="utf-8")
        # A timeout or assertion failure is not evidence of an app crash.
        if timed_out:
            status = "timeout"
        elif "capacity_result=PASS" in instrument and "OK (1 test)" in instrument:
            status = "pass"
        elif "Process crashed" in instrument or (
                "FATAL EXCEPTION" in logs and "Process: " + PACKAGE in logs):
            status = "app_crash"
        elif 'was already used' in instrument and 'androidx.compose.ui.layout.LayoutNodeSubcompositionsState' in instrument:
            # Compose's test exception handler catches this production rendering exception
            # before AndroidRuntime can terminate the process. Preserve that distinction.
            status = "ui_exception"
        elif "ComposeTimeoutException" in instrument and "LibraryCapacityTest.awaitTitle" in instrument:
            status = "render_timeout"
        else:
            status = "test_failure"
        pss = [int(value) for value in re.findall(r"pssKb=(\d+)", logs)]
        timings = re.search(r"PASS count=\d+ layout=\w+ seedMs=(\d+) renderMs=(\d+)", logs)
        result = {"games": count, "layout": layout, "orientation": orientation,
                  "zoom": args.zoom, "featured": args.featured,
                  "status": status, "seconds": round(time.monotonic() - started, 2),
                  "max_sampled_pss_mb": round(max(pss) / 1024, 1) if pss else None,
                  "seed_ms": int(timings[1]) if timings else None,
                  "first_render_ms": int(timings[2]) if timings else None,
                  "exception": (re.search(r"INSTRUMENTATION_STATUS: stack=([^\r\n]+)", instrument).group(1)
                                if "INSTRUMENTATION_STATUS: stack=" in instrument else None),
                  "evidence": name}
        results.append(result)
        (output / "results.json").write_text(json.dumps({"device": device["model"],
            "fixture": f"real Room paging, 256 repeated local 384x512 covers, zoom={args.zoom}, featured={args.featured}",
            "trials": results}, indent=2), encoding="utf-8")
        print(json.dumps(result), flush=True)
        return status

    for orientation in orientations:
        for layout in layouts:
            lower = 0
            for count in counts:
                status = trial(count, layout, orientation)
                if status == "pass":
                    lower = count
                    continue
                if status == "app_crash" and args.bisect and trial(count, layout, orientation) == "app_crash":
                    upper = count
                    while upper - lower > 100:
                        midpoint = (lower + upper) // 2
                        status = trial(midpoint, layout, orientation)
                        if status == "pass":
                            lower = midpoint
                        elif status == "app_crash":
                            upper = midpoint
                        else:
                            break
                # Resolve harness errors/timeouts before making capacity claims or increasing load.
                break
    print(f"Evidence saved to {output}", flush=True)
    return 0 if all(result["status"] == "pass" for result in results) else 1


if __name__ == "__main__":
    raise SystemExit(main())
