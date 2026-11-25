"""Stage a top-level engine project without modifying the pinned checkout."""
from pathlib import Path
import argparse
import subprocess


def project_cmake(text: str, host: Path) -> str:
    frontend = "if(ANDROID)\n    add_subdirectory(android)\nendif()"
    if text.count(frontend) != 1:
        raise ValueError("Pinned Android application target definition changed")
    text = text.replace(frontend, "# Android platform hosting belongs to Omnidroid.")
    return text + '\n# Omnidroid engine host\nadd_subdirectory("' + host.as_posix() + '" "${CMAKE_BINARY_DIR}/omnidroid-engine")\n'


def stage(source: Path, destination: Path, host: Path) -> None:
    source, destination, host = source.resolve(), destination.resolve(), host.resolve()
    if source == destination or source in destination.parents:
        raise ValueError("The staged project must be outside the upstream checkout")
    destination.mkdir(parents=True, exist_ok=True)
    for entry in source.iterdir():
        generated = entry.name == "build" or (entry.name.startswith("build-") and entry.name != "build-ffmpeg")
        if entry.name in ("CMakeLists.txt", ".git") or generated:
            continue
        link = destination / entry.name
        if not link.exists() and not link.is_symlink():
            link.symlink_to(entry, target_is_directory=entry.is_dir())
    git_dir = subprocess.check_output(["git", "-C", str(source), "rev-parse", "--absolute-git-dir"], text=True).strip()
    # Upstream's version generator requires .git/ to be a directory. Resolve a
    # submodule's gitdir file once and expose that directory read-only by link.
    git_link = destination / ".git"
    if git_link.is_file() and not git_link.is_symlink():
        git_link.unlink()
    if not git_link.exists():
        git_link.symlink_to(Path(git_dir), target_is_directory=True)
    text = (source / "CMakeLists.txt").read_text(encoding="utf-8")
    (destination / "CMakeLists.txt").write_text(project_cmake(text, host), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    stage(args.source, args.destination, Path(__file__).parent)
