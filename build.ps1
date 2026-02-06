# Build script for Jotty Android - produces .apk file(s)
# Usage: .\build.ps1           # build debug APK (default)
#        .\build.ps1 -Release   # build release APK (requires signing config)

param(
    [switch]$Release
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$task = if ($Release) { "assembleRelease" } else { "assembleDebug" }
$variant = if ($Release) { "release" } else { "debug" }

Write-Host "Building $variant APK..." -ForegroundColor Cyan
& .\gradlew.bat $task --no-daemon

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

$apkDir = "app\build\outputs\apk\$variant"
$apk = Get-ChildItem -Path $apkDir -Filter "*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1

if ($apk) {
    Write-Host ""
    Write-Host "Build succeeded." -ForegroundColor Green
    Write-Host "APK: $($apk.FullName)" -ForegroundColor Green
} else {
    Write-Host "Build completed but APK not found under $apkDir" -ForegroundColor Yellow
}
