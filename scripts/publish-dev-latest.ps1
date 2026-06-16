# Build dev APK locally and publish to the dev-latest GitHub release (no Actions).
# Requires: gh CLI authenticated, keystore.properties recommended for in-place updates.
# Usage: .\scripts\publish-dev-latest.ps1

param(
    [switch]$DryRun,
    [switch]$SkipPublish
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $repoRoot

if (-not $SkipPublish) {
    $null = Get-Command gh -ErrorAction Stop
    gh auth status 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "gh is not authenticated. Run: gh auth login"
    }
}

$apkPath = & (Join-Path $PSScriptRoot "build-dev-apk.ps1") -OutputDir $repoRoot | Select-Object -Last 1
if (-not (Test-Path -LiteralPath $apkPath)) {
    throw "Build did not produce APK at: $apkPath"
}

if ($DryRun) {
    Write-Host "[DryRun] Would publish dev-latest with asset: $apkPath"
    exit 0
}

if ($SkipPublish) {
    Write-Host "Built only (skip publish): $apkPath"
    exit 0
}

$sha = git rev-parse HEAD
$shortSha = $sha.Substring(0, 7)
$repo = gh repo view --json nameWithOwner -q .nameWithOwner

$body = @"
Rolling pre-release build from ``dev`` (built locally).
⚠️ This preview build may contain unstable or breaking bugs.

Commit: $sha

| Item | Link |
| --- | --- |
| Commit | [``$shortSha``](https://github.com/$repo/commit/$sha) |
| Changelog | [CHANGELOG.md](https://github.com/$repo/blob/dev/CHANGELOG.md) |
"@

Write-Host "Resetting dev-latest release..."
gh release delete dev-latest --cleanup-tag --yes 2>$null
gh api -X DELETE "repos/$repo/git/refs/tags/dev-latest" 2>$null

Write-Host "Publishing dev-latest..."
$notesFile = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText($notesFile, $body)
    gh release create dev-latest `
        --target (git branch --show-current) `
        --title "Dev Latest" `
        --notes-file $notesFile `
        --prerelease `
        $apkPath
    if ($LASTEXITCODE -ne 0) {
        throw "gh release create failed (exit $LASTEXITCODE)"
    }
} finally {
    Remove-Item -LiteralPath $notesFile -Force -ErrorAction SilentlyContinue
}

Write-Host "Done: https://github.com/$repo/releases/tag/dev-latest"
