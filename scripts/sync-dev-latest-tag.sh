#!/usr/bin/env bash
# Updates the local dev-latest tag from origin.
# Prefer setup-repo-git.sh once per clone so "git pull --tags" never clobbers dev-latest.
set -euo pipefail
"$(dirname "$0")/setup-repo-git.sh"
