# Build script for Jotty Android - produces .apk file(s)
# Usage: .\build.ps1           # build debug APK (default)
#        .\build.ps1 -Release   # build release APK (requires signing config)

param(
    [switch]$Release
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# Ensure Gradle wrapper JAR exists (often missing when repo is cloned)
$wrapperJar = "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    Write-Host "Gradle wrapper JAR missing. Downloading..." -ForegroundColor Yellow
    $wrapperUrl = "https://github.com/gradle/gradle/raw/v8.13.0/gradle/wrapper/gradle-wrapper.jar"
    try {
        $ProgressPreference = "SilentlyContinue"
        Invoke-WebRequest -Uri $wrapperUrl -OutFile $wrapperJar -UseBasicParsing
        Write-Host "Wrapper JAR installed." -ForegroundColor Green
    } catch {
        Write-Host "Download failed. Install the wrapper manually:" -ForegroundColor Red
        Write-Host "  1. Install Gradle (e.g. winget install Gradle.Gradle)" -ForegroundColor Red
        Write-Host "  2. Run: gradle wrapper" -ForegroundColor Red
        exit 1
    }
}

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
