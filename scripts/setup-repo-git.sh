#!/usr/bin/env bash
# One-time (per clone) Git setup: hooks, and the moving dev-latest tag. dev-latest only exists on
# GitHub (CI recreates it there with gh), not on GitLab (origin), so it is fetched from GitHub.
# Safe to run again (idempotent).
set -euo pipefail
cd "$(dirname "$0")/.."

github_url='https://github.com/Darknetzz/jotty-android.git'
refspec='+refs/tags/dev-latest:refs/tags/dev-latest'
# Older clones fetched origin from GitHub and had this refspec on origin; GitLab has no dev-latest.
if git config --get-all remote.origin.fetch | grep -Fxq "$refspec"; then
  git config --unset-all remote.origin.fetch '^\+refs/tags/dev-latest:refs/tags/dev-latest$'
  echo "Removed the dev-latest refspec from remote.origin.fetch (origin is GitLab)."
fi

if [[ -d .githooks ]]; then
  git config core.hooksPath .githooks
  echo "Set core.hooksPath to .githooks (post-merge syncs dev-latest from GitHub on pull; pre-push local dev publish is opt-in via jotty.autoPublishDev=true)."
fi

git fetch "$github_url" "$refspec"
echo "Local dev-latest tag matches GitHub."
echo "You can use: git pull origin dev  (or ./scripts/pull-dev.sh)"
