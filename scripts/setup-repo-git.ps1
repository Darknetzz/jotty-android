# One-time (per clone) Git setup: hooks, and the moving dev-latest tag. dev-latest only exists on
# GitHub (CI recreates it there with gh), not on GitLab (origin), so it is fetched from GitHub.
# Safe to run again (idempotent).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$githubUrl = "https://github.com/Darknetzz/jotty-android.git"
$refspec = "+refs/tags/dev-latest:refs/tags/dev-latest"
# Older clones fetched origin from GitHub and had this refspec on origin; GitLab has no dev-latest.
$existing = @(git config --get-all remote.origin.fetch 2>$null)
if ($existing -contains $refspec) {
    git config --unset-all remote.origin.fetch '^\+refs/tags/dev-latest:refs/tags/dev-latest$'
    Write-Host "Removed the dev-latest refspec from remote.origin.fetch (origin is GitLab)."
}

$hooksDir = ".githooks"
if (Test-Path $hooksDir) {
    git config core.hooksPath $hooksDir
    Write-Host "Set core.hooksPath to $hooksDir (post-merge syncs dev-latest from GitHub on pull; pre-push local dev publish is opt-in via jotty.autoPublishDev=true)."
}

git fetch $githubUrl $refspec
Write-Host "Local dev-latest tag matches GitHub."
Write-Host "You can use: git pull origin dev  (or .\scripts\pull-dev.ps1)"
