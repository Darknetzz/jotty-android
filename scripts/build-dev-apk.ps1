# Build a dev APK locally — mirrors the build steps in .github/workflows/dev-latest.yml.
# Output: jotty-android-{VERSION}-dev.apk (or *-dev-debug.apk without keystore.properties).
# Usage: .\scripts\build-dev-apk.ps1

param(
    [string]$OutputDir = "."
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "lib/gradle-env.ps1")
Initialize-JottyGradleEnv -RepoRoot $repoRoot

$versionName = Get-GradleProperty -RepoRoot $repoRoot -Name "VERSION_NAME"
$baseCode = [int](Get-GradleProperty -RepoRoot $repoRoot -Name "VERSION_CODE")
$runNum = [int](git rev-list --count HEAD)
$devCode = $baseCode * 10000 + ($runNum % 10000)
$sha = (git rev-parse HEAD).Substring(0, 7)

Write-Host "Dev build: VERSION_NAME=$versionName VERSION_CODE=$devCode SHA=$sha" -ForegroundColor Cyan

$props = @{
    DEV_BUILD_SHA = $sha
    VERSION_CODE  = $devCode
}

$signed = Test-ReleaseKeystoreConfigured -RepoRoot $repoRoot
if ($signed) {
    Invoke-JottyGradlew -Tasks @("assembleRelease") -Properties $props
    $src = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
    $outName = "jotty-android-$versionName-dev.apk"
} else {
    Write-Host "No keystore.properties — building debug-signed dev APK." -ForegroundColor Yellow
    Invoke-JottyGradlew -Tasks @("assembleDebug") -Properties $props
    $src = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    $outName = "jotty-android-$versionName-dev-debug.apk"
}

$destDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $repoRoot $OutputDir }
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$dest = Join-Path $destDir $outName
Copy-Item -LiteralPath $src -Destination $dest -Force
Write-Host "APK: $dest" -ForegroundColor Green
Write-Output $dest
