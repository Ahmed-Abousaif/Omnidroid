"""Time slice-build matching against the sanitizer a scan does today.

Game titles come from a local Libretro .rdb file. Thumbnail filenames come from
a saved Named_Boxarts listing. Download time is not included.
"""

from __future__ import annotations

import re
import time
from pathlib import Path

CACHE = Path.home() / "AppData/Local/Temp/omni-issue26"
THUMB_REPLACE = re.compile(r"""[&*/:`<>?\\|\"#%^~;\[\]{}@+=!$]""")
PARENS = re.compile(r"\([^)]*\)|\[[^\]]*\]")
NON_ALNUM = re.compile(r"[^a-z0-9]+")
TAG = re.compile(r"\(([^)]*)\)")
SKIP_TAGS = {"demo", "beta", "proto", "dlc", "sample", "taikenban"}
NAME_KEY = b"\xa4name"


def read_names(path: Path) -> list[str]:
    data = path.read_bytes()
    names = []
    cursor = 0
    while True:
        found = data.find(NAME_KEY, cursor)
        if found < 0:
            break
        marker_at = found + len(NAME_KEY)
        if marker_at >= len(data):
            break
        marker = data[marker_at]
        if 0xA0 <= marker <= 0xBF:
            length = marker - 0xA0
            start = marker_at + 1
        elif marker == 0xD9 and marker_at + 1 < len(data):
            length = data[marker_at + 1]
            start = marker_at + 2
        elif marker == 0xDA and marker_at + 2 < len(data):
            length = int.from_bytes(data[marker_at + 1 : marker_at + 3], "big")
            start = marker_at + 3
        else:
            cursor = marker_at
            continue
        raw = data[start : start + length]
        cursor = start + length
        if raw.endswith((b".iso", b".bin", b".nds", b".wbfs", b".zip", b".cue")):
            continue
        try:
            names.append(raw.decode("utf-8"))
        except UnicodeDecodeError:
            continue
    return names


def read_boxarts(path: Path) -> list[str]:
    names = []
    raw = path.read_bytes()
    encoding = "utf-16" if raw.startswith(b"\xff\xfe") or raw.startswith(b"N\x00") else "utf-8"
    for line in raw.decode(encoding, errors="replace").splitlines():
        if line.startswith("Named_Boxarts/") and line.endswith(".png"):
            names.append(line.split("/", 1)[1][:-4])
    return names


def match_key(title: str) -> str:
    text = PARENS.sub(" ", title).lower()
    text = NON_ALNUM.sub(" ", text).strip()
    for article in ("the ", "a ", "an "):
        if text.startswith(article):
            text = text[len(article) :]
    return text.strip()


def tags(title: str) -> set[str]:
    found = set()
    for group in TAG.findall(title):
        for part in re.split(r"[,+/]", group):
            token = part.strip().lower()
            if token:
                found.add(token)
    return found


def build_index(filenames: list[str]) -> dict[str, list[str]]:
    index: dict[str, list[str]] = {}
    for filename in filenames:
        index.setdefault(match_key(filename), []).append(filename)
    return index


def choose(database_name: str, candidates: list[str]) -> str | None:
    if not candidates:
        return None
    wanted = tags(database_name)
    best = None
    best_score = None
    for candidate in candidates:
        have = tags(candidate)
        if (have & SKIP_TAGS) - wanted:
            continue
        overlap = len(wanted & have)
        extra = len(have - wanted)
        score = (overlap, -extra, -len(candidate))
        if best_score is None or score > best_score:
            best = candidate
            best_score = score
    return best


def current_filename(name: str) -> str:
    return THUMB_REPLACE.sub("_", name).strip().strip("._ ")


def main() -> None:
    titles = read_names(CACHE / "wii.rdb")
    boxarts = read_boxarts(CACHE / "wii-thumbs.txt")
    started = time.perf_counter()
    index = build_index(boxarts)
    index_seconds = time.perf_counter() - started
    started = time.perf_counter()
    chosen = [choose(title, index.get(match_key(title), [])) for title in titles]
    match_seconds = time.perf_counter() - started
    started = time.perf_counter()
    sanitized = [current_filename(title) for title in titles]
    sanitize_seconds = time.perf_counter() - started
    found = [name for name in chosen if name]
    different = sum(1 for pick, clean in zip(chosen, sanitized) if pick and pick != clean)
    print(f"wii titles {len(titles)}")
    print(f"boxarts {len(boxarts)}")
    print(f"index keys {len(index)}")
    print(f"index build {index_seconds * 1000:.1f} ms")
    print(f"match all {match_seconds * 1000:.1f} ms ({len(titles) / match_seconds:.0f} titles/s)")
    print(f"sanitize all {sanitize_seconds * 1000:.1f} ms ({len(titles) / sanitize_seconds:.0f} titles/s)")
    print(f"matched {len(found)} ({100 * len(found) / len(titles):.1f}%)")
    print(f"matched name differs from today's filename {different}")


if __name__ == "__main__":
    main()
