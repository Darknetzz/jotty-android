param(
    [string]$Version,
    [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$DevLatestSectionKey = "dev-latest"
$DevLatestUrl = "https://github.com/Darknetzz/jotty-android/releases/tag/dev-latest"

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

function Get-DevSectionHeadingLine {
  return "## [$DevLatestSectionKey]($DevLatestUrl)"
}

function Update-Changelog {
    param(
        [string]$FilePath,
        [string]$VersionName,
        [string]$ReleaseDate
    )

    $content = Get-Content -Raw -LiteralPath $FilePath
    $lineEnding = if ($content.Contains("`r`n")) { "`r`n" } else { "`n" }

    $devHeadingPattern = '(?m)^## \[(?:dev-latest|[^\]]+-dev)\](?:\([^\)]+\))?(?: - \[[^\]]+\]\([^\)]+\))?\s*$'
    $devMatch = [regex]::Match($content, $devHeadingPattern)
    if (-not $devMatch.Success) {
        throw "Could not find a rolling dev changelog section (## [dev-latest] or ## [VERSION-dev]) in CHANGELOG.md"
    }
    if ($content -match "(?m)^## \[$([regex]::Escape($VersionName))\] - ") {
        throw "CHANGELOG.md already contains version $VersionName"
    }

    $afterDevHeading = $devMatch.Index + $devMatch.Length
    $nextStablePattern = '(?m)^## \[[^\]]+\] - \d{4}-\d{2}-\d{2}\s*$'
    $nextMatch = [regex]::Match($content.Substring($afterDevHeading), $nextStablePattern)
    if (-not $nextMatch.Success) {
        throw "Could not find the next dated stable section after the dev section in CHANGELOG.md"
    }

    $devBody = $content.Substring($afterDevHeading, $nextMatch.Index).Trim()
    $devBody = $devBody -replace '^\r?\n---\r?\n', ''
    $devBody = $devBody -replace '\r?\n---\s*$', ''

    $newDevHeading = Get-DevSectionHeadingLine
    $releaseHeader = "## [$VersionName] - $ReleaseDate"
    $promoted = "$newDevHeading$lineEnding$lineEnding---$lineEnding$lineEnding$releaseHeader$lineEnding$lineEnding$devBody$lineEnding$lineEnding---$lineEnding$lineEnding"

    $updated = $content.Substring(0, $devMatch.Index) + $promoted + $content.Substring($afterDevHeading + $nextMatch.Index)

    $releaseUrl = "https://github.com/Darknetzz/jotty-android/releases/tag/v$VersionName"
    $linkLine = "[$VersionName]: $releaseUrl"
    if ($updated -notmatch "(?m)^\[$([regex]::Escape($VersionName))\]:\s+") {
        $linkPattern = '(?m)^\[\d+\.\d+(?:\.\d+)*(?:-[^\]]+)?\]:\s+https://github\.com/Darknetzz/jotty-android/releases/tag/v[^\r\n]+$'
        if ($updated -match $linkPattern) {
            $updated = [regex]::Replace($updated, $linkPattern, "$linkLine$lineEnding`$0", 1)
        } else {
            $updated = $updated.TrimEnd() + $lineEnding + $lineEnding + $linkLine + $lineEnding
        }
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
    Write-Host "[DryRun] Would promote CHANGELOG [dev-latest] to [$Version] ($Date) and reset [dev-latest]"
    exit 0
}

Set-Content -LiteralPath $gradlePath -Value $gradleResult.Content -NoNewline
Set-Content -LiteralPath $changelogPath -Value $updatedChangelog -NoNewline

Write-Host "Release prep complete:"
Write-Host "  VERSION_NAME=$Version"
Write-Host "  VERSION_CODE=$($gradleResult.NextCode)"
Write-Host "  CHANGELOG.md updated with [$Version] - $Date"
