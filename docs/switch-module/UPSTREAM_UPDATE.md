# Upstream Eden Update Guide for Omnidroid

This document describes how to monitor, update, and verify the upstream [Eden](https://git.eden-emu.dev/eden-emu/eden) Nintendo Switch emulator submodule used by Omnidroid.

---

## 1. Upstream Topology & Architecture

- **Submodule Path**: `modules/switch/upstream/eden`
- **Canonical Remote**: `https://git.eden-emu.dev/eden-emu/eden.git`
- **GitHub Fallback Mirror**: `https://github.com/eden-emulator/mirror.git`
- **Current Pinned Release**: `v0.2.1` (`58c1e20ee58efa3900ba616207d460886214480b`)
- **Patch Directory**: `modules/switch/patches/` (currently empty / 0 patches)
- **Dynamic Feature Module**: `:omnidroid_core_switch` (`omnidroid-cores/omnidroid_core_switch/`)
- **Native Target**: Produces `libyuzu-android.so` for `arm64-v8a`.

---

## 2. Invariants & Rules

1. **Zero Modifications to Upstream Source**:
   The upstream Eden repository must remain completely pristine. Never edit, commit, or branch directly inside `modules/switch/upstream/eden`.
2. **Deterministic JNI Compatibility**:
   Eden's C++ native JNI layer (`src/common/android/id_cache.cpp`) statically looks up Java classes under `org/yuzu/yuzu_emu/*`. Omnidroid provides these bridge classes in `omnidroid-app` so upstream C++ compiles without any code divergence.
3. **Architecture Restrictions**:
   Eden is exclusively targeted and built for `arm64-v8a`. Do not attempt to compile for `armeabi-v7a`, `x86`, or `x86_64`.
4. **No Proprietary Nintendo Assets**:
   Never commit, bundle, or distribute Nintendo keys (`prod.keys`, `title.keys`), system firmware archives, or copyrighted game titles. Users must supply their own dumped keys.

---

## 3. How to Update Upstream

### Option A: Using the Automated Scripts (Recommended)

#### On Linux / macOS / WSL:
```bash
# Update to the latest upstream release tag automatically
./scripts/switch-update-upstream.sh

# Or update to a specific tag or commit
./scripts/switch-update-upstream.sh v0.2.2
```

#### On Windows (PowerShell):
```powershell
# Update to the latest upstream release tag automatically
.\scripts\switch-update-upstream.ps1

# Or update to a specific tag or commit
.\scripts\switch-update-upstream.ps1 -TargetTag "v0.2.2"
```

The updater script automatically:
1. Verifies that the submodule working tree is completely clean.
2. Fetches the latest remote tags and commits from `origin`.
3. Checks out the target tag or commit.
4. Verifies structural integrity (`CMakeLists.txt`, `id_cache.cpp`).
5. Stages the updated submodule commit in git.

---

### Option B: Manual Git Submodule Update

```bash
cd modules/switch/upstream/eden
git fetch --tags origin
git checkout <NEW_TAG_OR_COMMIT>
cd ../../../
git add modules/switch/upstream/eden
```

---

## 4. Post-Update Verification Checklist

After checking out a new upstream version, always run through this checklist before committing:

1. **Verify JNI Signatures**:
   Inspect `modules/switch/upstream/eden/src/common/android/id_cache.cpp` with `git diff`:
   - Did upstream add new `FindClass`, `GetMethodID`, or `GetFieldID` calls?
   - If new classes or methods were added, add the corresponding stubs under `omnidroid-app/src/main/java/org/yuzu/yuzu_emu/`.

2. **Verify CMake Variables**:
   Check `modules/switch/upstream/eden/CMakeLists.txt`:
   - Ensure `libyuzu-android.so` is still built.
   - Ensure CMake build options in `omnidroid-cores/omnidroid_core_switch/build.gradle.kts` match upstream expectations.

3. **Compile Test**:
   ```bash
   ./gradlew :omnidroid-app:compilePlayDynamicDebugKotlin
   ```

4. **Commit the Bump**:
   ```bash
   git commit -m "switch: bump upstream Eden to <tag/commit>"
   ```

---

## 5. Automated CI Monitoring

Omnidroid includes a scheduled GitHub Actions workflow at `.github/workflows/switch-upstream-check.yml`:
- Runs weekly on Mondays at 04:00 UTC (and can be triggered manually via `workflow_dispatch`).
- Runs `switch-update-upstream.sh` against the latest remote tags.
- Verifies Kotlin compilation if a new tag is found and posts a summary in GitHub Actions.

