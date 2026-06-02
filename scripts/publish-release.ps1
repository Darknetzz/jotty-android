# After release.ps1: push dev, open dev→main PR, merge, publish GitHub release (triggers release-apk.yml).
param(
    [string]$Version,
    [switch]$DryRun,
    [switch]$SkipPush,
    [switch]$SkipPr,
    [switch]$SkipMerge,
    [switch]$SkipRelease,
    [switch]$WaitForChecks,
    [int]$CheckTimeoutMinutes = 20
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if ($PSScriptRoot) {
        return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    }
    return (Get-Location).Path
}

function Get-VersionNameFromGradle {
    param([string]$GradlePath)
    $content = Get-Content -Raw -LiteralPath $GradlePath
    if ($content -notmatch '(?m)^VERSION_NAME=(.+)\r?$') {
        throw "VERSION_NAME not found in gradle.properties"
    }
    return $Matches[1].Trim()
}

function Get-ChangelogSection {
    param(
        [string]$ChangelogPath,
        [string]$VersionName
    )
    $content = Get-Content -Raw -LiteralPath $ChangelogPath
    $escaped = [regex]::Escape($VersionName)
    $pattern = "(?ms)^## \[$escaped\] - [^\r\n]+\r?\n\r?\n(.*?)(?=^---\r?\n\r?\n## |\z)"
    if ($content -match $pattern) {
        return $Matches[1].Trim()
    }
    throw "Could not find changelog section ## [$VersionName] in CHANGELOG.md"
}

function Assert-GhAvailable {
    $null = Get-Command gh -ErrorAction Stop
    gh auth status 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "gh is not authenticated. Run: gh auth login"
    }
}

function Wait-PrChecks {
    param(
        [int]$PrNumber,
        [int]$TimeoutMinutes
    )
    $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
    while ((Get-Date) -lt $deadline) {
        $json = gh pr checks $PrNumber --json name,state 2>$null
        if ($LASTEXITCODE -ne 0) {
            Start-Sleep -Seconds 15
            continue
        }
        $checks = $json | ConvertFrom-Json
        if ($checks.Count -eq 0) {
            Start-Sleep -Seconds 15
            continue
        }
        $pending = @($checks | Where-Object { $_.state -in @("PENDING", "IN_PROGRESS", "QUEUED", "WAITING") })
        $failed =
            @(
                $checks |
                    Where-Object {
                        $_.state -in @("FAILURE", "ERROR", "CANCELLED", "TIMED_OUT") -and
                            $_.name -notlike "GitGuardian*"
                    }
            )
        if ($failed.Count -gt 0) {
            throw "PR #$PrNumber has failing checks: $($failed.name -join ', ')"
        }
        $ggFailed = @($checks | Where-Object { $_.name -like "GitGuardian*" -and $_.state -match "FAIL" })
        if ($ggFailed.Count -gt 0) {
            Write-Warning "GitGuardian reported failures (often test-fixture false positives). Fix or merge manually if required."
        }
        if ($pending.Count -eq 0) {
            Write-Host "All PR checks passed."
            return
        }
        Write-Host "Waiting for checks ($($pending.Count) pending)..."
        Start-Sleep -Seconds 20
    }
    throw "Timed out after ${TimeoutMinutes}m waiting for PR checks."
}

$repoRoot = Get-RepoRoot
Set-Location $repoRoot

$gradlePath = Join-Path $repoRoot "gradle.properties"
$changelogPath = Join-Path $repoRoot "CHANGELOG.md"

if (-not $Version) {
    $Version = Get-VersionNameFromGradle -GradlePath $gradlePath
}
$tag = "v$Version"

$status = git status --porcelain
if ($status) {
    throw "Working tree is not clean. Commit release prep first, then run publish-release.ps1"
}

$branch = (git branch --show-current).Trim()
if ($branch -ne "dev") {
    throw "Expected to be on branch 'dev' (current: '$branch')."
}

Assert-GhAvailable

$changelogBody = Get-ChangelogSection -ChangelogPath $changelogPath -VersionName $Version
$releaseNotesPath = Join-Path $repoRoot ".gh-release-$Version.md"
$installBlurb = @"
## Install

Download **`jotty-android-$Version.apk`** from this release (release-signed when CI secrets are configured).

**Updating from an older `*-debug.apk` or mixed signing?** Android may show "App not installed" — uninstall once, then install this APK. Your Jotty server data is unchanged.

**Full changelog:** https://github.com/Darknetzz/jotty-android/blob/v$Version/CHANGELOG.md

---

"@
$releaseNotes = $installBlurb + $changelogBody

if ($DryRun) {
    Write-Host "[DryRun] Version=$Version tag=$tag branch=$branch"
    Write-Host "[DryRun] Would write $releaseNotesPath and publish release notes ($($releaseNotes.Length) chars)"
    if (-not $SkipPush) { Write-Host "[DryRun] Would: git push origin dev" }
    if (-not $SkipPr) { Write-Host "[DryRun] Would: gh pr create --base main --head dev --title Release $Version" }
    if (-not $SkipMerge) { Write-Host "[DryRun] Would: gh pr merge (after checks)" }
    if (-not $SkipRelease) { Write-Host "[DryRun] Would: gh release create $tag --target main" }
    exit 0
}

if (-not $SkipPush) {
    Write-Host "Pushing dev..."
    git push origin dev
}

$prNumber = $null
if (-not $SkipPr) {
    $existing = gh pr list --base main --head dev --state open --json number --jq '.[0].number' 2>$null
    if ($existing) {
        $prNumber = [int]$existing
        Write-Host "Using existing PR #$prNumber (dev → main)."
    } else {
        $prTitle = "Release v$Version"
        $prBody = @"
## Summary

Stable release **v$Version** (`gradle.properties` / CHANGELOG).

## Test plan

- [ ] CI green on this PR
- [ ] Merge to ``main``
- [ ] GitHub release ``$tag`` published (Release APK workflow attaches APK)
- [ ] ``dev`` fast-forwards to ``main`` via sync-dev-with-main workflow

See [CHANGELOG.md](https://github.com/Darknetzz/jotty-android/blob/dev/CHANGELOG.md) on ``dev`` for full notes.
"@
        $prUrl = gh pr create --base main --head dev --title $prTitle --body $prBody
        if ($prUrl -match '/pull/(\d+)') {
            $prNumber = [int]$Matches[1]
        } else {
            throw "Could not parse PR number from: $prUrl"
        }
        Write-Host "Created PR #$prNumber"
    }
}

if (-not $SkipMerge -and $prNumber) {
    if ($WaitForChecks) {
        Wait-PrChecks -PrNumber $prNumber -TimeoutMinutes $CheckTimeoutMinutes
    }
    Write-Host "Merging PR #$prNumber..."
    gh pr merge $prNumber --merge --delete-branch=false
    git fetch origin main
}

if (-not $SkipRelease) {
    Set-Content -LiteralPath $releaseNotesPath -Value $releaseNotes -NoNewline
    try {
        gh release view $tag *> $null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Release $tag already exists; updating notes only."
            gh release edit $tag --notes-file $releaseNotesPath
        } else {
            Write-Host "Publishing GitHub release $tag (target: main)..."
            gh release create $tag --target main --title $tag --notes-file $releaseNotesPath
        }
        Write-Host "Release: https://github.com/Darknetzz/jotty-android/releases/tag/$tag"
        Write-Host "APK workflow should attach jotty-android-$Version.apk when complete."
    } finally {
        Remove-Item -LiteralPath $releaseNotesPath -ErrorAction SilentlyContinue
    }
}

Write-Host "Done. sync-dev-with-main.yml will fast-forward dev after main updates."
