#!/usr/bin/env bash
set -euo pipefail
# Run inside the engine Docker image with the host SDK mounted read-only at
# /host-sdk and a persistent Linux build volume at /work.
export ANDROID_HOME=/work/sdk
mkdir -p "$ANDROID_HOME"
sdkmanager() {
    java -cp '/host-sdk/cmdline-tools/latest/lib/*' \
        com.android.sdklib.tool.sdkmanager.SdkManagerCli --sdk_root="$ANDROID_HOME" "$@"
}
set +o pipefail
yes | sdkmanager --licenses
set -o pipefail
sdkmanager 'ndk;29.0.14206865' 'cmake;3.30.5'
revision="${1:?Supply the pinned ARMSX3 source revision}"
if [ ! -d /work/armsx3/.git ]; then
    git init /work/armsx3
fi
if git -C /work/armsx3 remote get-url origin >/dev/null 2>&1; then
    git -C /work/armsx3 remote set-url origin https://github.com/ARMSX2/ARMSX3.git
else
    git -C /work/armsx3 remote add origin https://github.com/ARMSX2/ARMSX3.git
fi
git -C /work/armsx3 fetch --depth 1 origin "$revision"
git -C /work/armsx3 checkout --detach FETCH_HEAD
git -C /work/armsx3 submodule sync --recursive
git -C /work/armsx3 submodule update --init --recursive --depth 1
