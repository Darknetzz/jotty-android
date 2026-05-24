#!/usr/bin/env bash
# Updates the local dev-latest tag from origin (safe when pull --tags fails with "would clobber").
set -euo pipefail
cd "$(dirname "$0")/.."
git fetch origin tag dev-latest --force
echo "Local tag dev-latest now matches origin."
