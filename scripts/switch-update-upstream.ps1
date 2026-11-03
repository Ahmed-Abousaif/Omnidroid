# scripts/switch-update-upstream.ps1
# Safely checks and updates the upstream Eden submodule for Omnidroid.

param (
    [string]$TargetTag = ""
)

$ErrorActionPreference = "Stop"
$ModuleDir = "modules/switch/upstream/eden"

Write-Host "=== Omnidroid Switch Submodule Upstream Updater ===" -ForegroundColor Cyan

if (-not (Test-Path $ModuleDir)) {
    Write-Error "Submodule directory '$ModuleDir' does not exist."
}

Push-Location $ModuleDir
try {
    # 1. Verify working tree is clean
    $status = git status --porcelain
    if ($status) {
        Write-Error "Eden submodule has uncommitted changes or untracked files! Upstream must remain pristine."
    }

    $currentCommit = (git rev-parse HEAD).Trim()
    $currentTag = ""
    try {
        $currentTag = (git describe --tags --exact-match 2>$null).Trim()
    } catch {
        $currentTag = "un-tagged"
    }

    Write-Host "Current pinned commit: $currentCommit ($currentTag)"

    # 2. Fetch upstream tags and remote commits
    Write-Host "Fetching latest tags from upstream..."
    git fetch --tags origin

    $target = $TargetTag
    if ([string]::IsNullOrWhiteSpace($target)) {
        $latestTag = (git tag -l "v*" --sort=-v:refname | Select-Object -First 1)
        if ($latestTag) {
            $target = $latestTag.Trim()
        } else {
            git fetch origin master
            $target = "origin/master"
        }
    }

    Write-Host "Target version: $target"

    # 3. Checkout target tag/commit
    git checkout $target
    $newCommit = (git rev-parse HEAD).Trim()

    if ($currentCommit -eq $newCommit) {
        Write-Host "Submodule is already at target version ($currentCommit). No update needed." -ForegroundColor Green
        return
    }

    Write-Host "Updated from $currentCommit to $newCommit ($target)" -ForegroundColor Green

    # 4. Verify integrity and JNI compatibility invariants
    if (-not (Test-Path "CMakeLists.txt")) {
        Write-Error "Root CMakeLists.txt is missing in submodule!"
    }

    if (-not (Test-Path "src/common/android/id_cache.cpp")) {
        Write-Error "src/common/android/id_cache.cpp is missing in submodule!"
    }

    $idCacheContent = Get-Content -Raw "src/common/android/id_cache.cpp"
    if (-not ($idCacheContent -match "org/yuzu/yuzu_emu")) {
        Write-Warning "JNI class path 'org/yuzu/yuzu_emu' not found in id_cache.cpp. Upstream may have renamed JNI packages."
    }
} finally {
    Pop-Location
}

Write-Host "Staging updated submodule in Omnidroid..."
git add $ModuleDir

Write-Host "=== Submodule update complete ===" -ForegroundColor Cyan
Write-Host "Submodule pinned to $newCommit. Run build test before committing."

