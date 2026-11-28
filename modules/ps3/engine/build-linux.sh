#!/usr/bin/env bash
set -euo pipefail
# Docker volume /work holds Linux SDK/sources/builds. Mount the workspace at
# /host-work read-write for staging the validated native library into its module.
export ANDROID_HOME=/work/sdk
python3 /host-work/omnidroid-cores/build_armsx3.py \
    --source /work/armsx3 --build-root /work/omnidroid-ps3-build \
    --jobs "${PS3_BUILD_JOBS:-2}" "$@"
