#!/usr/bin/env bash
# Fast-forward origin/dev to origin/main (same as post-release CI sync).
set -euo pipefail
cd "$(dirname "$0")/.."
git fetch origin main dev
if [ "$(git rev-parse origin/dev)" = "$(git rev-parse origin/main)" ]; then
  echo "dev already matches main."
  exit 0
fi
if ! git merge-base --is-ancestor origin/dev origin/main; then
  echo "dev is not an ancestor of main — merge main into dev manually." >&2
  exit 1
fi
git push origin origin/main:refs/heads/dev
echo "dev fast-forwarded to $(git rev-parse --short origin/main)."
