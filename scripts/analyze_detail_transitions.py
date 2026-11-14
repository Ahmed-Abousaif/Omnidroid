"""Measure the 600 ms after a details tap, excluding trace pre-roll and UI lookup.

Pass an official Perfetto trace_processor binary or its Python launcher with
--trace-processor. This is host-side analysis; it adds nothing to the APK.

Frame pairing and duration definitions follow AndroidX FrameTimingQuery
(Copyright 2021 The Android Open Source Project, Apache-2.0):
https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:benchmark/benchmark-macro/src/main/java/androidx/benchmark/macro/perfetto/FrameTimingQuery.kt
"""

import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
import io
import json
from pathlib import Path
import re
import subprocess
import sys


QUERY = """
WITH bounds AS (SELECT ts, ts + dur AS end_ts FROM slice WHERE name = 'measureBlock' LIMIT 1)
SELECT 'tap' AS kind, s.name, s.ts, s.dur
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING(utid) JOIN process p USING(upid), bounds b
WHERE p.name = 'com.omnidroid' AND t.is_main_thread = 1
AND s.name LIKE 'dispatchInputEvent MotionEvent UP %' AND s.ts BETWEEN b.ts AND b.end_ts
UNION
SELECT CASE WHEN t.is_main_thread = 1 THEN 'ui' ELSE 'rt' END, s.name, s.ts, s.dur
FROM slice s JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t USING(utid) JOIN process p USING(upid)
WHERE p.name = 'com.omnidroid' AND s.dur > 0 AND (
 (t.is_main_thread = 1 AND s.name GLOB 'Choreographer#doFrame [0-9]*') OR
 (t.name = 'RenderThread' AND s.name GLOB 'DrawFrame* [0-9]*'))
UNION
SELECT 'actual', f.name, f.ts, f.dur
FROM actual_frame_timeline_slice f JOIN process p USING(upid)
WHERE p.name = 'com.omnidroid' AND f.dur > 0
UNION
SELECT 'expected', f.name, f.ts, f.dur
FROM expected_frame_timeline_slice f JOIN process p USING(upid)
WHERE p.name = 'com.omnidroid' AND f.dur > 0
ORDER BY ts
"""


def frame_by_id(rows, frame_id):
    low, high = 0, len(rows) - 1
    while low <= high:
        middle = (low + high) // 2
        value = rows[middle][0]
        if value < frame_id:
            low = middle + 1
        elif value > frame_id:
            high = middle - 1
        else:
            return rows[middle]
    return None


def analyze(trace, processor):
    command = ([sys.executable, str(processor)] if processor.suffix == '.py' else [str(processor)])
    result = subprocess.run(command + ['query', '--quiet', '--format', 'csv', str(trace), QUERY],
                            capture_output=True, text=True, encoding='utf-8', check=True)
    rows = list(csv.DictReader(io.StringIO(result.stdout)))
    taps = [int(r['ts']) for r in rows if r['kind'] == 'tap']
    if len(taps) != 1:
        raise ValueError(f'{trace}: expected one details tap; found {len(taps)}')
    tap = taps[0]
    slices = {kind: [] for kind in ['ui', 'rt', 'actual', 'expected']}
    for row in rows:
        if row['kind'] in slices:
            match = re.search(r'(\d+)$', row['name'])
            if match:
                slices[row['kind']].append((int(match[1]), int(row['ts']), int(row['dur'])))
    actual = slices['actual'].copy()
    frames = []
    for frame_id, rt_start, rt_duration in slices['rt']:
        main = frame_by_id(slices['ui'], frame_id)
        if main is None:
            continue
        _, start, duration = main
        midpoint = start + duration // 2
        # Match AndroidX's frame-ID workaround and consume each actual frame once.
        index = next((i for i in range(len(actual) - 1, -1, -1)
                      if actual[i][1] < start + 50_000 and
                      actual[i][1] <= midpoint <= actual[i][1] + actual[i][2]), None)
        if index is None:
            continue
        actual_id, actual_start, actual_duration = actual.pop(index)
        deadline = frame_by_id(slices['expected'], actual_id)
        if deadline is None:
            continue
        end = rt_start + rt_duration
        frames.append({'startOffsetMs': (start - tap) / 1e6,
                       'cpuMs': (end - start) / 1e6,
                       'overrunMs': (max(end, actual_start + actual_duration) - deadline[1] - deadline[2]) / 1e6})
    focused = [r for r in frames if 0 <= r['startOffsetMs'] < 600]
    if len(focused) < 10:
        raise ValueError(f'{trace}: only {len(focused)} transition frames')
    return {'trace': str(trace), 'tapTs': tap, 'allFrameCount': len(frames),
            'allCpuMs': [r['cpuMs'] for r in frames], 'frames': focused}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--trace-processor', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('traces', nargs='+', type=Path)
    args = parser.parse_args()
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda p: analyze(p, args.trace_processor), args.traces))
    args.out.write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    print(f'Analyzed {len(results)} traces; saved {args.out}')


if __name__ == '__main__':
    main()
