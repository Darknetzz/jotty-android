# One-time (per clone) Git config so the moving dev-latest tag updates on fetch/pull without
# "would clobber existing tag". Safe to run again (idempotent).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$refspec = "+refs/tags/dev-latest:refs/tags/dev-latest"
$existing = @(git config --get-all remote.origin.fetch 2>$null)
if ($existing -contains $refspec) {
    Write-Host "Already configured: force-fetch dev-latest on origin fetch."
} else {
    git config --add remote.origin.fetch $refspec
    Write-Host "Added remote.origin.fetch entry for dev-latest (force-update tag)."
}

$hooksDir = ".githooks"
if (Test-Path $hooksDir) {
    git config core.hooksPath $hooksDir
    Write-Host "Set core.hooksPath to $hooksDir (post-merge syncs dev-latest on dev)."
}

git fetch origin tag dev-latest --force
Write-Host "Local dev-latest tag matches origin."
Write-Host "You can use: git pull --tags origin dev  (or .\scripts\pull-dev.ps1)"
