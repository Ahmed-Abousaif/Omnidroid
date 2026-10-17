#!/usr/bin/env python3
"""
Omnidroid Vulkan Renderer Profiling & Telemetry Helper
Provides utilities to:
1. Stream live CPU/GPU/Pacing metrics from Omnidroid's native instrumentation layer.
2. Capture Perfetto traces with GPU counters, Vulkan events, SurfaceFlinger, and scheduler slices.
3. Enable/disable Khronos Vulkan validation layers on connected Android devices.
4. Prepare device for Android GPU Inspector (AGI) and Arm Performance Studio / PerfDoc.
"""

import argparse
import subprocess
import sys
import time
import re

APP_PACKAGE = "com.omnidroid.app"
STATS_TAG = "OmnidroidStats"

def run_adb(args, capture_output=False, check=True):
    cmd = ["adb"] + args
    try:
        return subprocess.run(cmd, capture_output=capture_output, text=True, check=check)
    except FileNotFoundError:
        print("[ERROR] 'adb' command not found in PATH. Please install Android SDK platform-tools.", file=sys.stderr)
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        if capture_output:
            print(f"[ERROR] adb command failed: {' '.join(cmd)}\n{e.stderr}", file=sys.stderr)
        sys.exit(e.returncode)

def cmd_stream_stats(args):
    print(f"[*] Enabling native profiling property (debug.omnidroid.profile=1)...")
    run_adb(["shell", "setprop", "debug.omnidroid.profile", "1"])
    print(f"[*] Clearing logcat and listening for {STATS_TAG}...")
    run_adb(["logcat", "-c"])

    proc = subprocess.Popen(
        ["adb", "logcat", "-s", f"{STATS_TAG}:I"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1
    )

    try:
        print("=" * 115)
        print(f"{'FPS':>7} | {'CPU-Run':>10} | {'CPU-Rend':>10} | {'GPU':>10} | {'Present':>10} | {'Drop':>6} | {'Pipelines':>14} | {'Memory':>10} | {'Alloc':>10}")
        print("=" * 115)
        for line in proc.stdout:
            match = re.search(r"FPS:\s*([0-9.]+)\s*\|\s*CPU-Run:\s*([0-9.]+\s*ms)\s*\|\s*CPU-Rend:\s*([0-9.]+\s*ms)(?:\s*\|\s*GPU:\s*([0-9.]+\s*ms))?\s*\|\s*(?:Present:\s*([0-9.]+\s*ms))?\s*\|\s*Drop:\s*(\d+)\s*\|\s*PipeComp:\s*(\d+\s*\([0-9.]+\s*ms\))\s*\|\s*RSS:\s*([0-9.]+\s*MB)\s*\|\s*Alloc:\s*([0-9.]+\s*KB/f)", line)
            if match:
                fps = match.group(1)
                cpu_run = match.group(2)
                cpu_rend = match.group(3)
                gpu = match.group(4) if match.group(4) else "N/A"
                present = match.group(5) if match.group(5) else "N/A"
                drop = match.group(6)
                pipes = match.group(7)
                rss = match.group(8)
                alloc = match.group(9)
                print(f"{fps:>7} | {cpu_run:>10} | {cpu_rend:>10} | {gpu:>10} | {present:>10} | {drop:>6} | {pipes:>14} | {rss:>10} | {alloc:>10}")
            elif STATS_TAG in line:
                print(line.strip())
    except KeyboardInterrupt:
        print("\n[*] Stopping stream...")
        proc.terminate()

def cmd_perfetto(args):
    duration = args.duration
    output = args.output or f"omnidroid_vulkan_trace_{int(time.time())}.perfetto-trace"
    print(f"[*] Recording {duration}s Perfetto trace (GPU slices, Vulkan, SurfaceFlinger, sched, frequency)...")

    perfetto_cfg = f"""
buffers: {{
    size_kb: 65536
    fill_policy: RING_BUFFER
}}
data_sources: {{
    config {{
        name: "linux.ftrace"
        ftrace_config {{
            ftrace_events: "sched_switch"
            ftrace_events: "sched_wakeup"
            ftrace_events: "power/cpu_frequency"
            ftrace_events: "power/gpu_frequency"
            ftrace_events: "gpu_mem/gpu_mem_total"
            ftrace_events: "dma_fence/*"
            atrace_categories: "gfx"
            atrace_categories: "view"
            atrace_categories: "sched"
            atrace_apps: "{APP_PACKAGE}"
        }}
    }}
}}
data_sources: {{
    config {{
        name: "gpu.renderstages"
    }}
}}
duration_ms: {duration * 1000}
"""
    # Push config to device
    p = subprocess.Popen(["adb", "shell", "perfetto", "-c", "-", "--txt", "-o", f"/data/misc/perfetto-traces/trace.pftrace"], stdin=subprocess.PIPE, text=True)
    p.communicate(input=perfetto_cfg)
    p.wait()

    print(f"[*] Pulling trace to {output}...")
    run_adb(["pull", "/data/misc/perfetto-traces/trace.pftrace", output])
    run_adb(["shell", "rm", "/data/misc/perfetto-traces/trace.pftrace"])
    print(f"[✓] Trace saved: {output}")
    print(f"[✓] View trace at: https://ui.perfetto.dev/")

def cmd_validation_layers(args):
    if args.enable:
        print("[*] Enabling Khronos Vulkan Validation Layer on device...")
        run_adb(["shell", "settings", "put", "global", "enable_gpu_debug_layers", "1"])
        run_adb(["shell", "settings", "put", "global", "gpu_debug_app", APP_PACKAGE])
        run_adb(["shell", "settings", "put", "global", "gpu_debug_layers", "VK_LAYER_KHRONOS_validation"])
        print("[✓] Validation layer active for package:", APP_PACKAGE)
        print("    Monitor warnings/errors with: adb logcat -s VulkanRenderer:E VulkanContext:E")
    else:
        print("[*] Disabling Vulkan Validation Layer on device...")
        run_adb(["shell", "settings", "delete", "global", "enable_gpu_debug_layers"])
        run_adb(["shell", "settings", "delete", "global", "gpu_debug_app"])
        run_adb(["shell", "settings", "delete", "global", "gpu_debug_layers"])
        print("[✓] Validation layer disabled.")

def main():
    parser = argparse.ArgumentParser(description="Omnidroid Vulkan Profiling & Telemetry Helper")
    subparsers = parser.add_subparsers(dest="subcommand", required=True)

    stream_parser = subparsers.add_parser("stream", help="Stream live native telemetry (CPU, GPU time, pacing, memory, allocs)")
    stream_parser.set_defaults(func=cmd_stream_stats)

    perfetto_parser = subparsers.add_parser("perfetto", help="Capture a system + GPU Perfetto trace")
    perfetto_parser.add_argument("--duration", "-d", type=int, default=10, help="Trace duration in seconds (default: 10)")
    perfetto_parser.add_argument("--output", "-o", type=str, default=None, help="Output trace file path")
    perfetto_parser.set_defaults(func=cmd_perfetto)

    layers_parser = subparsers.add_parser("validation", help="Toggle Vulkan validation layers on device")
    layers_parser.add_argument("--enable", action="store_true", help="Enable validation layers")
    layers_parser.add_argument("--disable", action="store_true", help="Disable validation layers")
    layers_parser.set_defaults(func=cmd_validation_layers)

    args = parser.parse_args()
    args.func(args)

if __name__ == "__main__":
    main()
