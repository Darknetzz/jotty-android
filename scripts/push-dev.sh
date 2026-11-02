#!/usr/bin/env bash
# Push to origin/dev (GitHub + GitLab via dual push); GitLab CI publishes dev-latest.
# With git config jotty.autoPublishDev true, the pre-push hook also publishes locally (--skip-publish skips it).
set -euo pipefail

skip_publish=0
push_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-publish) skip_publish=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--skip-publish] [-- extra git push args]"
      exit 0
      ;;
    --)
      shift
      push_args+=("$@")
      break
      ;;
    *) push_args+=("$1"); shift ;;
  esac
done

cd "$(dirname "$0")/.."

if [[ "$skip_publish" -eq 1 ]]; then
  export JOTTY_SKIP_DEV_PUBLISH=1
fi

git push origin dev "${push_args[@]}"
