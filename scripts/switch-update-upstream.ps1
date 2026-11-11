# Resolve the latest Eden master commit (or an explicit branch/tag/SHA).
param (
    [Alias("TargetTag")]
    [string]$TargetRef = "master"
)
$ErrorActionPreference = "Stop"
$Builder = Join-Path $PSScriptRoot "../omnidroid-cores/build_eden.py"
& python $Builder --update-only --ref $TargetRef
if ($LASTEXITCODE -ne 0) { throw "Eden update failed (exit $LASTEXITCODE)" }
