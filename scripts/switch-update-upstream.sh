#!/usr/bin/env bash
# scripts/switch-update-upstream.sh
# Safely checks and updates the upstream Eden submodule for Omnidroid.

set -euo pipefail

MODULE_DIR="modules/switch/upstream/eden"
DEFAULT_TAG="${1:-}"

echo "=== Omnidroid Switch Submodule Upstream Updater ==="

if [ ! -d "$MODULE_DIR" ]; then
    echo "Error: Submodule directory '$MODULE_DIR' does not exist." >&2
    exit 1
fi

cd "$MODULE_DIR"

# 1. Verify working tree is clean
if [ -n "$(git status --porcelain)" ]; then
    echo "Error: Eden submodule has uncommitted changes or untracked files!" >&2
    echo "Upstream must remain pristine. Discard local changes before updating." >&2
    git status --short
    exit 1
fi

CURRENT_COMMIT="$(git rev-parse HEAD)"
CURRENT_TAG="$(git describe --tags --exact-match 2>/dev/null || echo "un-tagged")"

echo "Current pinned commit: $CURRENT_COMMIT ($CURRENT_TAG)"

# 2. Fetch upstream tags and remote commits
echo "Fetching latest tags from upstream..."
git fetch --tags origin

if [ -z "$DEFAULT_TAG" ]; then
    # Discover latest version tag (v*.*.*)
    LATEST_TAG="$(git tag -l "v*" --sort=-v:refname | head -n 1)"
    if [ -z "$LATEST_TAG" ]; then
        echo "No version tags found; fetching master branch..."
        git fetch origin master
        TARGET="origin/master"
    else
        TARGET="$LATEST_TAG"
    fi
else
    TARGET="$DEFAULT_TAG"
fi

echo "Target version: $TARGET"

# 3. Checkout target tag/commit
git checkout "$TARGET"
NEW_COMMIT="$(git rev-parse HEAD)"

if [ "$CURRENT_COMMIT" = "$NEW_COMMIT" ]; then
    echo "Submodule is already at the target version ($CURRENT_COMMIT). No update needed."
    exit 0
fi

echo "Updated from $CURRENT_COMMIT to $NEW_COMMIT ($TARGET)"

# 4. Verify integrity and JNI compatibility invariants
echo "Verifying upstream structure integrity..."
if [ ! -f "CMakeLists.txt" ]; then
    echo "Error: Root CMakeLists.txt is missing!" >&2
    exit 1
fi

if [ ! -f "src/common/android/id_cache.cpp" ]; then
    echo "Error: src/common/android/id_cache.cpp is missing!" >&2
    exit 1
fi

if ! grep -q "org/yuzu/yuzu_emu" src/common/android/id_cache.cpp; then
    echo "Warning: JNI class path 'org/yuzu/yuzu_emu' was not found in id_cache.cpp. Upstream may have renamed JNI packages." >&2
fi

cd - >/dev/null

echo "Staging updated submodule in Omnidroid..."
git add "$MODULE_DIR"

echo "=== Submodule update complete ==="
echo "Submodule pinned to $NEW_COMMIT."
echo "Remember to run a build test (e.g. ./gradlew :omnidroid-app:compilePlayDynamicDebugKotlin) before committing."

