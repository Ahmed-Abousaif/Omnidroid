#!/usr/bin/env bash
# Resolve the latest Eden master commit (or an explicit branch/tag/SHA).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$ROOT/omnidroid-cores/build_eden.py" --update-only --ref "${1:-master}"
