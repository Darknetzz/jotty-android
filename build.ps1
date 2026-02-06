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

# Android Gradle Plugin requires Java 11+. Ensure we use a suitable JVM.
function Get-JavaVersion {
    param([string]$JavaHome)
    $env:JAVA_HOME = $JavaHome
    $out = & "$JavaHome\bin\java.exe" -version 2>&1
    if ($out -match '"(\d+)\.(\d+)') {
        $major = [int]$matches[1]
        $minor = [int]$matches[2]
        if ($major -eq 1) { return $minor }  # 1.8 -> 8
        return $major
    }
    return 0
}

$minJava = 11
$needJava = $true
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $v = Get-JavaVersion -JavaHome $env:JAVA_HOME
    if ($v -ge $minJava) { $needJava = $false }
}

if ($needJava) {
    $candidates = @(
        "${env:LOCALAPPDATA}\Programs\Android Studio\jbr",
        "C:\Program Files\Android\Android Studio\jbr",
        "${env:ProgramFiles}\Android\Android Studio\jbr",
        "${env:ProgramFiles(x86)}\Android\Android Studio\jbr",
        "${env:LOCALAPPDATA}\Microsoft\jbr-17",
        "C:\Program Files\Microsoft\jdk-17*",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        "C:\Program Files\Java\jdk-17*",
        "C:\Program Files\Java\jdk-21*",
        "C:\Program Files\Java\jdk-11*"
    )
    foreach ($c in $candidates) {
        $dir = $null
        if ($c -match '\*') { $dir = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1 }
        else { $dir = Get-Item $c -ErrorAction SilentlyContinue }
        if ($dir -and $dir.PSIsContainer -and (Test-Path "$($dir.FullName)\bin\java.exe")) {
            $v = Get-JavaVersion -JavaHome $dir.FullName
            if ($v -ge $minJava) {
                $env:JAVA_HOME = $dir.FullName
                Write-Host "Using Java $v from: $($dir.FullName)" -ForegroundColor Green
                break
            }
        }
    }
    if (-not $env:JAVA_HOME -or (Get-JavaVersion -JavaHome $env:JAVA_HOME) -lt $minJava) {
        Write-Host "This build requires Java $minJava or newer. Current Java is too old or not set." -ForegroundColor Red
        Write-Host "Set JAVA_HOME to a JDK 11+ installation, or install one (e.g. Android Studio or winget install Microsoft.OpenJDK.17)." -ForegroundColor Red
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
