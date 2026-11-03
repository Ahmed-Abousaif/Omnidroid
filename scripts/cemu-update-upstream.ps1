# scripts/cemu-update-upstream.ps1
# Safely checks and updates the upstream Cemu libretro submodule for Omnidroid.

param (
    [string]$TargetRef = ""
)

$ErrorActionPreference = "Stop"
$ModuleDir = "modules/cemu/upstream/cemu-libretro-android"

Write-Host "=== Omnidroid Cemu Submodule Upstream Updater ===" -ForegroundColor Cyan

if (-not (Test-Path $ModuleDir)) {
    Write-Error "Submodule directory '$ModuleDir' does not exist."
}

Push-Location $ModuleDir
try {
    # 1. Verify working tree is clean
    $status = git status --porcelain
    if ($status) {
        Write-Error "Cemu submodule has uncommitted changes or untracked files! Upstream must remain pristine."
    }

    $currentCommit = (git rev-parse HEAD).Trim()
    $currentTag = ""
    try {
        $currentTag = (git describe --tags --exact-match 2>$null).Trim()
    } catch {
        $currentTag = "un-tagged"
    }

    Write-Host "Current pinned commit: $currentCommit ($currentTag)"

    # 2. Fetch upstream remotes and tags
    Write-Host "Fetching latest updates from origin..."
    git fetch --tags origin
    git fetch origin main

    $target = $TargetRef
    if ([string]::IsNullOrWhiteSpace($target)) {
        $latestTag = (git tag -l "v*" --sort=-v:refname | Select-Object -First 1)
        if ($latestTag) {
            $target = $latestTag.Trim()
        } else {
            $target = "origin/main"
        }
    }

    Write-Host "Target: $target"

    # 3. Checkout target
    git checkout $target
    $newCommit = (git rev-parse HEAD).Trim()

    if ($currentCommit -eq $newCommit) {
        Write-Host "Submodule is already at target ($currentCommit). No update needed." -ForegroundColor Green
        return
    }

    Write-Host "Updated from $currentCommit to $newCommit ($target)" -ForegroundColor Green

    # 4. Verify integrity invariants
    if (-not (Test-Path "CMakeLists.txt")) {
        Write-Error "Root CMakeLists.txt is missing in submodule!"
    }

    if (-not (Test-Path "src/libretro/CemuLibretro.cpp")) {
        Write-Error "src/libretro/CemuLibretro.cpp is missing in submodule!"
    }

    if (-not (Test-Path "build_libretro_android.sh")) {
        Write-Error "build_libretro_android.sh is missing in submodule!"
    }
} finally {
    Pop-Location
}

Write-Host "Staging updated submodule in Omnidroid..."
git add $ModuleDir

Write-Host "=== Submodule update complete ===" -ForegroundColor Cyan
Write-Host "Submodule pinned to $newCommit. Run build test before committing."

