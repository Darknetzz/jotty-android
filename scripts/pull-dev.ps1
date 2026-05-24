# Pull dev branch and tags (dev-latest tag updates safely after setup-repo-git).
param(
    [switch]$NoSetup
)

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

if (-not $NoSetup) {
    & (Join-Path $PSScriptRoot "setup-repo-git.ps1")
}

git pull --tags origin dev
