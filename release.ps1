param(
    [string]$Version,
    [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if ($PSScriptRoot) {
        return $PSScriptRoot
    }
    return (Get-Location).Path
}

function Update-GradleProperties {
    param(
        [string]$FilePath,
        [string]$VersionName
    )

    $content = Get-Content -Raw -LiteralPath $FilePath

    if ($content -notmatch '(?m)^VERSION_NAME=') {
        throw "VERSION_NAME not found in gradle.properties"
    }
    if ($content -notmatch '(?m)^VERSION_CODE=(\d+)\r?$') {
        throw "VERSION_CODE not found in gradle.properties"
    }

    $currentCode = [int]$Matches[1]
    $nextCode = $currentCode + 1

    $updated = $content -replace '(?m)^VERSION_NAME=.*$', "VERSION_NAME=$VersionName"
    $updated = $updated -replace '(?m)^VERSION_CODE=.*$', "VERSION_CODE=$nextCode"

    return @{
        Content = $updated
        PreviousCode = $currentCode
        NextCode = $nextCode
    }
}

function Get-CurrentVersionName {
    param([string]$FilePath)

    $content = Get-Content -Raw -LiteralPath $FilePath
    if ($content -notmatch '(?m)^VERSION_NAME=(.+)\r?$') {
        throw "VERSION_NAME not found in gradle.properties"
    }
    return $Matches[1].Trim()
}

function Get-DefaultBumpedVersion {
    param([string]$CurrentVersion)

    $parts = $CurrentVersion -split '\.'
    if ($parts.Length -lt 1) {
        throw "Could not parse current version '$CurrentVersion'"
    }
    $lastIndex = $parts.Length - 1
    $lastPart = $parts[$lastIndex]
    $parsed = 0
    if (-not [int]::TryParse($lastPart, [ref]$parsed)) {
        throw "Last part of current version '$CurrentVersion' is not numeric"
    }
    $parts[$lastIndex] = ($parsed + 1).ToString()
    return ($parts -join '.')
}

function Update-Changelog {
    param(
        [string]$FilePath,
        [string]$VersionName,
        [string]$ReleaseDate
    )

    $content = Get-Content -Raw -LiteralPath $FilePath
    $lineEnding = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }

    if ($content -notmatch '## \[Unreleased\]') {
        throw "Could not find '## [Unreleased]' in CHANGELOG.md"
    }
    if ($content -match "## \[$([regex]::Escape($VersionName))\]") {
        throw "CHANGELOG.md already contains version $VersionName"
    }

    $releaseHeader = "## [$VersionName] - $ReleaseDate"
    $replacement = "## [Unreleased]$lineEnding$lineEnding---$lineEnding$lineEnding$releaseHeader"
    $updated = [regex]::Replace($content, '## \[Unreleased\]\r?\n\r?\n---\r?\n\r?\n## \[[^\]]+\] - [^\r\n]+', $replacement, 1)

    if ($updated -eq $content) {
        $updated = [regex]::Replace($content, '## \[Unreleased\]', $replacement, 1)
    }

    $releaseUrl = "https://github.com/Darknetzz/jotty-android/releases/tag/v$VersionName"
    $linkLine = "[$VersionName]: $releaseUrl"
    if ($updated -notmatch "(?m)^\[$([regex]::Escape($VersionName))\]:\s+") {
        $updated = $updated.TrimEnd() + $lineEnding + $lineEnding + $linkLine + $lineEnding
    }

    return $updated
}

$repoRoot = Get-RepoRoot
$gradlePath = Join-Path $repoRoot "gradle.properties"
$changelogPath = Join-Path $repoRoot "CHANGELOG.md"

if (-not (Test-Path -LiteralPath $gradlePath)) {
    throw "gradle.properties not found at $gradlePath"
}
if (-not (Test-Path -LiteralPath $changelogPath)) {
    throw "CHANGELOG.md not found at $changelogPath"
}

$currentVersion = Get-CurrentVersionName -FilePath $gradlePath
if (-not $Version) {
    $defaultVersion = Get-DefaultBumpedVersion -CurrentVersion $currentVersion
    $enteredVersion = Read-Host "Release version [$defaultVersion]"
    $Version = if ([string]::IsNullOrWhiteSpace($enteredVersion)) { $defaultVersion } else { $enteredVersion.Trim() }
}

$gradleResult = Update-GradleProperties -FilePath $gradlePath -VersionName $Version
$updatedChangelog = Update-Changelog -FilePath $changelogPath -VersionName $Version -ReleaseDate $Date

if ($DryRun) {
    Write-Host "[DryRun] Would set VERSION_NAME=$Version"
    Write-Host "[DryRun] Would increment VERSION_CODE $($gradleResult.PreviousCode) -> $($gradleResult.NextCode)"
    Write-Host "[DryRun] Would promote changelog Unreleased to $Version ($Date)"
    exit 0
}

Set-Content -LiteralPath $gradlePath -Value $gradleResult.Content -NoNewline
Set-Content -LiteralPath $changelogPath -Value $updatedChangelog -NoNewline

Write-Host "Release prep complete:"
Write-Host "  VERSION_NAME=$Version"
Write-Host "  VERSION_CODE=$($gradleResult.NextCode)"
Write-Host "  CHANGELOG.md updated with [$Version] - $Date"
