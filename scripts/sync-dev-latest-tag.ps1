# Updates the local dev-latest tag from origin (safe when pull --tags fails with "would clobber").
# dev-latest is a moving tag recreated by CI on every push to dev.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
git fetch origin tag dev-latest --force
Write-Host "Local tag dev-latest now matches origin."
