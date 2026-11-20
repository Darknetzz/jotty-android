#!/usr/bin/env bash
# Pull dev branch and tags (dev-latest tag updates safely after setup-repo-git).
set -euo pipefail
cd "$(dirname "$0")/.."

no_setup=false
if [[ "${1:-}" == "--no-setup" ]]; then
  no_setup=true
fi

if [[ "$no_setup" != "true" ]]; then
  "$(dirname "$0")/setup-repo-git.sh"
fi

git pull --tags origin dev
