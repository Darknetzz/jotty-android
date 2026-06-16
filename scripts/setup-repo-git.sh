#!/usr/bin/env bash
# One-time (per clone) Git config so the moving dev-latest tag updates on fetch/pull without
# "would clobber existing tag". Safe to run again (idempotent).
set -euo pipefail
cd "$(dirname "$0")/.."

refspec='+refs/tags/dev-latest:refs/tags/dev-latest'
if git config --get-all remote.origin.fetch | grep -Fxq "$refspec"; then
  echo "Already configured: force-fetch dev-latest on origin fetch."
else
  git config --add remote.origin.fetch "$refspec"
  echo "Added remote.origin.fetch entry for dev-latest (force-update tag)."
fi

if [[ -d .githooks ]]; then
  git config core.hooksPath .githooks
  echo "Set core.hooksPath to .githooks (pre-push publishes dev-latest on push to dev; post-merge syncs tag on pull)."
fi

git fetch origin tag dev-latest --force
echo "Local dev-latest tag matches origin."
echo "You can use: git pull --tags origin dev  (or ./scripts/pull-dev.sh)"
