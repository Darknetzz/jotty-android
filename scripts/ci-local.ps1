# Local CI — mirrors .github/workflows/ci.yml (without GitHub-hosted runners).
# Usage:
#   .\scripts\ci-local.ps1                 # unit tests, lint, ktlint, release assemble
#   .\scripts\ci-local.ps1 -SkipRelease    # skip assembleRelease
#   .\scripts\ci-local.ps1 -SmokeTest      # also run emulator instrumentation (needs AVD)
#   .\scripts\ci-local.ps1 -DebugOnly      # debug unit tests + lintDebug only (faster)

param(
    [switch]$SkipRelease,
    [switch]$SmokeTest,
    [switch]$SkipLint,
    [switch]$DebugOnly
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "lib/gradle-env.ps1")
Initialize-JottyGradleEnv -RepoRoot $repoRoot

$testTasks = if ($DebugOnly) { @("testDebugUnitTest") } else { @("test") }
$lintTasks = if ($DebugOnly) { @("lintDebug") } else { @("lintDebug", "lintRelease") }

Write-Host "=== Unit tests ===" -ForegroundColor Cyan
Invoke-JottyGradlew -Tasks $testTasks

if (-not $SkipLint) {
    Write-Host "=== Lint ($($lintTasks -join ', ')) ===" -ForegroundColor Cyan
    Invoke-JottyGradlew -Tasks $lintTasks

    Write-Host "=== ktlint ===" -ForegroundColor Cyan
    Invoke-JottyGradlew -Tasks @("ktlintCheck")
}

if (-not $SkipRelease) {
    Write-Host "=== Assemble release ===" -ForegroundColor Cyan
    Invoke-JottyGradlew -Tasks @("assembleRelease")
}

if ($SmokeTest) {
    Write-Host "=== Instrumentation smoke tests (requires running emulator/AVD) ===" -ForegroundColor Cyan
    Invoke-JottyGradlew -Tasks @("connectedDebugAndroidTest") -Properties @{
        "android.testInstrumentationRunnerArguments.class" = "com.jotty.android.MainActivitySmokeTest,com.jotty.android.PerformanceBaselineTest"
    }
}

Write-Host ""
Write-Host "Local CI passed." -ForegroundColor Green
if (-not $SkipLint) {
    Write-Host "Reports: app\build\reports\tests\  app\build\reports\lint-results-*.html"
}
