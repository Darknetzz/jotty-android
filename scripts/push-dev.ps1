# Push to origin/dev (GitHub + GitLab via dual push); GitLab CI publishes dev-latest.
# With git config jotty.autoPublishDev true, the pre-push hook also publishes locally (-SkipPublish skips it).
# Usage: .\scripts\push-dev.ps1 [-SkipPublish] [-- extra git push args]

param(
    [switch]$SkipPublish,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$PushArgs
)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

if ($SkipPublish) {
    $env:JOTTY_SKIP_DEV_PUBLISH = "1"
}

if ($PushArgs.Count -gt 0) {
    git push origin dev @PushArgs
} else {
    git push origin dev
}
