# Fast-forward origin/dev to origin/main (same as post-release CI sync).
# Use when main is "ahead" only due to merge commits from release PRs.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
git fetch origin main dev
$main = git rev-parse origin/main
$dev = git rev-parse origin/dev
if ($main -eq $dev) {
    Write-Host "dev already matches main ($($main.Substring(0,7)))."
    exit 0
}
git merge-base --is-ancestor origin/dev origin/main 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "dev is not an ancestor of main - merge main into dev manually before syncing."
}
git push origin "origin/main:refs/heads/dev"
Write-Host "dev fast-forwarded to main ($($main.Substring(0,7)))."
