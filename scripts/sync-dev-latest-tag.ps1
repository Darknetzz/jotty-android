# Updates the local dev-latest tag from origin.
# Prefer setup-repo-git.ps1 once per clone so "git pull --tags" never clobbers dev-latest.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
& (Join-Path $PSScriptRoot "setup-repo-git.ps1")
