# Build a stable release APK locally — mirrors .github/workflows/release-apk.yml build steps.
# Output: jotty-android-{VERSION}.apk (or *-debug.apk without keystore.properties).
# Usage: .\scripts\build-release-apk.ps1

param(
    [string]$OutputDir = "."
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "lib/gradle-env.ps1")
Initialize-JottyGradleEnv -RepoRoot $repoRoot

$versionName = Get-GradleProperty -RepoRoot $repoRoot -Name "VERSION_NAME"
$signed = Test-ReleaseKeystoreConfigured -RepoRoot $repoRoot

if ($signed) {
    Invoke-JottyGradlew -Tasks @("assembleRelease")
    $src = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
    $outName = "jotty-android-$versionName.apk"
} else {
    Write-Host "No keystore.properties - building debug-signed APK." -ForegroundColor Yellow
    Invoke-JottyGradlew -Tasks @("assembleDebug")
    $src = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    $outName = "jotty-android-$versionName-debug.apk"
}

$destDir = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $repoRoot $OutputDir }
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$dest = Join-Path $destDir $outName
Copy-Item -LiteralPath $src -Destination $dest -Force
Write-Host "APK: $dest" -ForegroundColor Green
Write-Output $dest
