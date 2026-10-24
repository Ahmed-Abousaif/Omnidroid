"""Time today's cover fallback chain against one request for the real file.

Current behavior follows LibretroThumbnailFallbackInterceptor: Named_Boxarts, then
Named_Titles, Named_Snaps, then up to three apostrophe variants. The proposed
path requests the known thumbnail once.
"""

from __future__ import annotations

import re
import time
import urllib.error
import urllib.request
from pathlib import Path

HOST = "https://thumbnails.libretro.com"
THUMB_REPLACE = re.compile(r"""[&*/:`<>?\\|\"#%^~;\[\]{}@+=!$]""")
WORD_ENDING_IN_S = re.compile(r"(?<=[A-Za-z])s(?=\s|\(|$)")
TIMEOUT = 20
CACHE = Path.home() / "AppData/Local/Temp/omni-issue26"

FIXTURES = [
    (
        "Sony - PlayStation 2",
        "Yu-Gi-Oh! The Duelists of the Roses (USA)",
        "Yu-Gi-Oh! The Duelists of the Roses (USA).png",
    ),
    (
        "Sony - PlayStation",
        "Yu-Gi-Oh! Forbidden Memories (USA)",
        "Yu-Gi-Oh! Forbidden Memories (USA).png",
    ),
    (
        "Sony - PlayStation",
        "WWF SmackDown! (USA)",
        "WWF SmackDown! (USA).png",
    ),
    (
        "Nintendo - Nintendo DS",
        "Yu-Gi-Oh! - World Championship 2007 (USA) (En,Ja)",
        "Yu-Gi-Oh! - World Championship 2007 (USA) (En,Ja).png",
    ),
    (
        "Nintendo - Nintendo DS",
        "WarioWare - Touched! (USA)",
        "WarioWare - Touched! (USA).png",
    ),
    (
        "Nintendo - Wii",
        "Final Fantasy Crystal Chronicles: My Life as a King (USA)",
        "Final Fantasy Crystal Chronicles - My Life as a King (USA) (WiiWare).png",
    ),
    (
        "Nintendo - Wii U",
        "Mario Kart 8 (USA)",
        "Mario Kart 8 (USA).png",
    ),
]


def java_url_encode(value: str) -> str:
    out = []
    for byte in value.encode("utf-8"):
        char = chr(byte)
        if char.isalnum() or char in "-_.*":
            out.append(char)
        elif char == " ":
            out.append("%20")
        else:
            out.append(f"%{byte:02X}")
    return "".join(out)


def current_filename(name: str) -> str:
    cleaned = THUMB_REPLACE.sub("_", name).strip().strip("._ ")
    return cleaned + ".png"


def url_for(system: str, category: str, filename: str) -> str:
    return f"{HOST}/{java_url_encode(system)}/{category}/{java_url_encode(filename)}"


def possessive_filenames(filename: str) -> list[str]:
    if not filename.lower().endswith(".png"):
        return []
    stem = filename[:-4]
    if "'" in stem:
        return []
    words: dict[str, list[int]] = {}
    for match in WORD_ENDING_IN_S.finditer(stem):
        index = match.start()
        start = index
        while start > 0 and stem[start - 1].isalpha():
            start -= 1
        word = stem[start : index + 1]
        words.setdefault(word, []).append(index)
    variants = []
    for word in list(words)[:3]:
        updated = stem
        for index in sorted(words[word], reverse=True):
            updated = updated[:index] + "'" + updated[index:]
        variants.append(updated + ".png")
    return variants


def head(url: str, failed: set[str] | None = None) -> tuple[int, float]:
    if failed is not None and url in failed:
        return 0, 0.0
    request = urllib.request.Request(url, method="HEAD")
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            code = response.status
    except urllib.error.HTTPError as error:
        code = error.code
    elapsed = time.perf_counter() - started
    if failed is not None and code != 200:
        failed.add(url)
    return code, elapsed


def current_chain(system: str, database_name: str, failed: set[str] | None = None) -> tuple[int, int, float]:
    filename = current_filename(database_name)
    candidates = [url_for(system, "Named_Boxarts", filename)]
    for category in ("Named_Titles", "Named_Snaps"):
        candidates.append(url_for(system, category, filename))
    for variant in possessive_filenames(filename):
        candidates.append(url_for(system, "Named_Boxarts", variant))
    requests = 0
    elapsed = 0.0
    status = 0
    for candidate in candidates:
        code, delta = head(candidate, failed)
        if code == 0 and delta == 0.0:
            continue
        requests += 1
        elapsed += delta
        status = code
        if code == 200:
            break
    return requests, status, elapsed


def proposed_once(system: str, filename: str) -> tuple[int, int, float]:
    code, elapsed = head(url_for(system, "Named_Boxarts", filename))
    return 1, code, elapsed


def extra_samples() -> list[tuple[str, str, str]]:
    path = CACHE / "wii-thumbs.txt"
    if not path.exists():
        return []
    bangs = []
    plain = []
    raw = path.read_bytes()
    encoding = "utf-16" if raw.startswith(b"\xff\xfe") or raw.startswith(b"N\x00") else "utf-8"
    for line in raw.decode(encoding, errors="replace").splitlines():
        if not line.startswith("Named_Boxarts/") or not line.endswith(".png"):
            continue
        filename = line.split("/", 1)[1]
        stem = filename[:-4]
        if "!" in stem and len(bangs) < 6:
            bangs.append(("Nintendo - Wii", stem, filename))
        elif "!" not in stem and ":" not in stem and "&" not in stem and stem.endswith("(USA)") and len(plain) < 3:
            plain.append(("Nintendo - Wii", stem, filename))
    return bangs + plain


def show(label: str, rows: list[tuple[str, int, int, float]]) -> None:
    requests = sum(row[1] for row in rows)
    elapsed = sum(row[3] for row in rows)
    hits = sum(1 for row in rows if row[2] == 200)
    print(f"\n{label}")
    print(f"{'title':<62} {'req':>4} {'ms':>8} status")
    for title, count, status, seconds in rows:
        shown = status if status else "skip"
        print(f"{title[:62]:<62} {count:4} {seconds * 1000:8.0f} {shown}")
    print(f"{'total':<62} {requests:4} {elapsed * 1000:8.0f} hits {hits}/{len(rows)}")


def main() -> None:
    cases = FIXTURES + extra_samples()
    current_rows = []
    proposed_rows = []
    failed: set[str] = set()
    for system, database_name, real_file in cases:
        requests, status, elapsed = current_chain(system, database_name, failed)
        current_rows.append((database_name, requests, status, elapsed))
        requests, status, elapsed = proposed_once(system, real_file)
        proposed_rows.append((database_name, requests, status, elapsed))
    show("Current chain, first load", current_rows)
    show("Proposed single request", proposed_rows)
    repeat_rows = []
    for system, database_name, _real_file in cases:
        requests, status, elapsed = current_chain(system, database_name, failed)
        repeat_rows.append((database_name, requests, status, elapsed))
    show("Current chain again, failed URLs remembered", repeat_rows)


if __name__ == "__main__":
    main()
