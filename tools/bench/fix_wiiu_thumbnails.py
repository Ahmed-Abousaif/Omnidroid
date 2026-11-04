"""Re-assign Wii U thumbnailPath values with humanize-aware matchKey and rewrite gzip slices."""

from __future__ import annotations

import gzip
import hashlib
import json
import re
import sqlite3
import tempfile
from pathlib import Path

ROOT = Path(r"D:\Work\Omnidroid")
THUMB_JSON = ROOT / "omnidroid-cores/tools/libretro-db-builder/cache/thumbs/Nintendo_-_Wii_U.json"
GZ_PATHS = [
    ROOT / "omnidroid-cores/bundled-cores/src/main/assets/libretro-db/bundled/wiiu.sqlite.gz",
    ROOT / "omnidroid-cores/omnidroid_core_cemu/src/main/assets/libretro-db/cemu/wiiu.sqlite.gz",
]

ARTICLE_SORTED = re.compile(r"^(.*),\s*(The|A|An)(\b.*)$", re.I)
PARENS = re.compile(r"\([^)]*\)|\[[^\]]*\]")
NON_ALNUM = re.compile(r"[^a-z0-9]+")
TAGS = re.compile(r"\(([^)]*)\)")
ARTICLES = {"the", "a", "an"}
SKIP = {"demo", "beta", "proto", "dlc"}


def humanize(name: str) -> str:
    match = ARTICLE_SORTED.match(name.strip())
    if not match:
        return name
    return f"{match.group(2)} {match.group(1).strip()}{match.group(3)}".replace("  ", " ").strip()


def match_key(title: str) -> str:
    words = [
        word
        for word in NON_ALNUM.sub(" ", PARENS.sub(" ", humanize(title).lower())).split()
        if word
    ]
    if len(words) > 1 and words[-1] in ARTICLES:
        words = [words[-1]] + words[:-1]
    if words and words[0] in ARTICLES:
        words = words[1:]
    return " ".join(words)


def tags(title: str) -> set[str]:
    found: set[str] = set()
    for match in TAGS.finditer(title):
        for part in re.split(r"[,+/]", match.group(1)):
            token = part.strip().lower()
            if token:
                found.add(token)
    return found


def choose(db_name: str, stems: list[str]) -> str | None:
    wanted = tags(db_name)
    best = None
    best_score = None
    for stem in stems:
        have = tags(stem)
        if (have & SKIP) - wanted:
            continue
        overlap = len(wanted & have)
        if wanted and overlap == 0:
            continue
        score = (overlap, -(len(have - wanted)), -len(stem))
        if best_score is None or score > best_score:
            best = stem
            best_score = score
    return best


def build_indexes() -> dict[str, tuple[dict[str, str], dict[str, list[str]]]]:
    tree = json.loads(THUMB_JSON.read_text(encoding="utf-8"))["tree"]
    by_folder = {"Named_Boxarts": [], "Named_Titles": [], "Named_Snaps": []}
    for entry in tree:
        path = entry.get("path", "")
        if not path.endswith(".png"):
            continue
        folder, name = path.split("/", 1)
        if folder in by_folder:
            by_folder[folder].append(name[:-4])

    indexes: dict[str, tuple[dict[str, str], dict[str, list[str]]]] = {}
    for folder, stems in by_folder.items():
        exact = {stem: stem for stem in stems}
        by_key: dict[str, list[str]] = {}
        for stem in stems:
            by_key.setdefault(match_key(stem), []).append(stem)
        indexes[folder] = (exact, by_key)
    return indexes


def path_for(db_name: str, indexes) -> str | None:
    wanted = match_key(db_name)
    for folder in ("Named_Boxarts", "Named_Titles", "Named_Snaps"):
        exact, by_key = indexes[folder]
        if db_name in exact:
            return f"{folder}/{exact[db_name]}.png"
        candidates = by_key.get(wanted)
        if not candidates:
            continue
        best = choose(db_name, candidates)
        if best:
            return f"{folder}/{best}.png"
    return None


def rewrite_from_bytes(raw: bytes, indexes) -> tuple[bytes, list[tuple]]:
    with tempfile.TemporaryDirectory() as tmp:
        sqlite_path = Path(tmp) / "wiiu.sqlite"
        sqlite_path.write_bytes(raw)
        conn = sqlite3.connect(sqlite_path)
        rows = conn.execute("SELECT rowid, name, rawName, thumbnailPath FROM games").fetchall()
        updated = 0
        wind_rows = []
        for rowid, name, raw_name, old_path in rows:
            db_name = raw_name or name
            if not db_name:
                continue
            new_path = path_for(db_name, indexes)
            if "Wind Waker" in (name or "") or "Kaze no Takuto" in (name or ""):
                wind_rows.append((name, old_path, new_path))
            if new_path != old_path:
                conn.execute("UPDATE games SET thumbnailPath = ? WHERE rowid = ?", (new_path, rowid))
                updated += 1
        conn.commit()
        with_thumb = conn.execute(
            "SELECT count(*) FROM games WHERE thumbnailPath IS NOT NULL AND thumbnailPath != ''"
        ).fetchone()[0]
        conn.close()
        out = sqlite_path.read_bytes()
        compressed = gzip.compress(out, compresslevel=9)
        print(f"updated={updated} with_thumb={with_thumb} bytes={len(compressed)}")
        for row in wind_rows:
            print(" ", row)
        return compressed, wind_rows


def update_manifests(wiiu_gz: bytes) -> None:
    sha = hashlib.sha256(wiiu_gz).hexdigest()
    size = len(wiiu_gz)
    manifest_paths = [
        ROOT / "omnidroid-cores/bundled-cores/src/main/assets/libretro-db/bundled/manifest.json",
        ROOT / "omnidroid-cores/omnidroid_core_cemu/src/main/assets/libretro-db/cemu/manifest.json",
    ]
    for path in manifest_paths:
        if not path.exists():
            print("skip missing manifest", path)
            continue
        text = path.read_text(encoding="utf-8")
        # Strip trailing manifestSha wrapper if present, edit slices, recompute.
        if ',"manifestSha":"' in text:
            body = text[: text.rfind(',"manifestSha":"')] + "}"
        else:
            body = text
        data = json.loads(body)
        for slice_info in data["slices"]:
            if slice_info.get("sliceId") == "wiiu":
                slice_info["sha256"] = sha
                slice_info["size"] = size
        new_body = json.dumps(data, separators=(",", ":"))
        # Match SliceWriter.manifest: body without final }, then append manifestSha
        assert new_body.endswith("}")
        manifest_sha = hashlib.sha256(new_body.encode("utf-8")).hexdigest()
        final = new_body[:-1] + f',"manifestSha":"{manifest_sha}"}}'
        path.write_text(final, encoding="utf-8")
        print(f"manifest {path.parent.name}: wiiu sha={sha} size={size}")


def main() -> None:
    indexes = build_indexes()
    # Re-read original from git? Use current first file then fully reassign all paths.
    with gzip.open(GZ_PATHS[0], "rb") as handle:
        raw = handle.read()
    # Expand once, clear all thumbnailPaths, reassign from scratch for clean result
    with tempfile.TemporaryDirectory() as tmp:
        sqlite_path = Path(tmp) / "wiiu.sqlite"
        sqlite_path.write_bytes(raw)
        conn = sqlite3.connect(sqlite_path)
        conn.execute("UPDATE games SET thumbnailPath = NULL")
        conn.commit()
        conn.close()
        cleared = sqlite_path.read_bytes()
    compressed, _ = rewrite_from_bytes(cleared, indexes)
    for path in GZ_PATHS:
        path.write_bytes(compressed)
        print("wrote", path)
    update_manifests(compressed)


if __name__ == "__main__":
    main()
