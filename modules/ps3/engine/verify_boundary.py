"""Reject frontend sources and legacy ABI exports in engine-only build artifacts."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def verify_sources(commands: Path, policy: dict) -> None:
    entries = json.loads(commands.read_text(encoding="utf-8"))
    # The engine project must not even configure the upstream application host.
    relevant = [e for e in entries if any(t in e.get("command", "") for t in
        ("CMakeFiles/omnidroid-ps3-engine.dir/", "CMakeFiles/rpcs3_emu.dir/"))]
    if not relevant:
        raise ValueError("No engine compilation commands found")
    for entry in entries:
        path = entry["file"].replace("\\", "/")
        for forbidden in policy["forbidden_sources"]:
            if forbidden in path:
                raise ValueError(f"Frontend source in engine target: {path}")


def verify_exports(library: Path, nm: Path, policy: dict) -> None:
    result = subprocess.check_output([str(nm), "--dynamic", "--defined-only", str(library)], text=True)
    exported = {line.split()[-1].split("@")[0] for line in result.splitlines() if line.split()}
    functions = {name for name in exported if name != "OMNIDROID_PS3_1"}
    if functions != set(policy["exports"]):
        raise ValueError(f"Unexpected public engine ABI: {sorted(functions)}")
    # Inspect unstripped symbols as well: hiding a frontend is not removing it.
    symbols = subprocess.check_output([str(nm), "--defined-only", "--demangle", str(library)], text=True)
    for forbidden in ("home_menu_dialog::", "big_picture_dialog::", "remote_play_start", "_armsx3_initialize",
                      "g_remote_play_", "vk::remote_play_capture_pass::", "VKGSRender::remote_capture_record("):
        if forbidden in symbols:
            raise ValueError(f"Frontend implementation linked into engine: {forbidden}")


def verify_package(package: Path, policy: dict) -> None:
    with zipfile.ZipFile(package) as archive:
        for name in archive.namelist():
            if any(path in name for path in policy["forbidden_package_paths"]):
                raise ValueError(f"Frontend asset/class in package: {name}")
            if name.endswith(("/libarmsx3-core.so", "/libomnidroid-ps3-engine.so")):
                raise ValueError("Legacy or split PS3 native library in package")
            if name.endswith(".dex"):
                data = archive.read(name)
                if any(b"L" + path.encode() in data for path in ("com/armsx2/", "com/armsx3/")):
                    raise ValueError(f"Upstream application callback namespace in {name}")
            if name.endswith("/libomnidroid-armsx3.so"):
                data = archive.read(name)
                for symbol in (b"_armsx3_initialize", b"_armsx3_openHomeMenu", b"Java_com_armsx", b"com/armsx3/core/"):
                    if symbol in data:
                        raise ValueError(f"Legacy frontend ABI {symbol!r} in {name}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compile-commands", type=Path)
    parser.add_argument("--library", type=Path)
    parser.add_argument("--nm", type=Path)
    parser.add_argument("--package", type=Path, help="APK, split APK or AAB to inspect")
    args = parser.parse_args()
    policy = json.loads((ROOT / "boundary.json").read_text(encoding="utf-8"))
    if not (args.compile_commands or args.library or args.package):
        parser.error("Supply compilation commands, library or package")
    if args.compile_commands:
        verify_sources(args.compile_commands, policy)
    if args.library:
        if not args.nm:
            parser.error("--library requires --nm")
        verify_exports(args.library, args.nm, policy)
    if args.package:
        verify_package(args.package, policy)
