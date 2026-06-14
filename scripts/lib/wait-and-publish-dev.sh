#!/usr/bin/env bash
# Wait until origin/dev matches TARGET_SHA, then run publish-dev-latest (background helper).
set -euo pipefail

target_sha="${1:?usage: wait-and-publish-dev.sh <commit-sha>}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
log="$repo_root/.git/jotty-dev-publish.log"
lock="$repo_root/.git/jotty-dev-publish.lock"

now() { date -u '+%Y-%m-%dT%H:%M:%SZ'; }

mkdir -p "$(dirname "$log")"

if ! mkdir "$lock" 2>/dev/null; then
  echo "$(now) dev-latest publish already running; skipped duplicate for $target_sha" >>"$log"
  exit 0
fi
trap 'rmdir "$lock" 2>/dev/null || true' EXIT

{
  echo "$(now) waiting for origin/dev -> ${target_sha:0:7}"
  max=120
  for ((i = 0; i < max; i++)); do
    sleep 2
    remote_sha="$(git ls-remote origin refs/heads/dev 2>/dev/null | awk '{print $1}')"
    if [[ "$remote_sha" == "$target_sha" ]]; then
      echo "$(now) origin/dev matched; publishing dev-latest"
      cd "$repo_root"
      bash "$repo_root/scripts/publish-dev-latest.sh"
      echo "$(now) dev-latest publish finished for ${target_sha:0:7}"
      exit 0
    fi
  done
  echo "$(now) timed out waiting for origin/dev; dev-latest not published" >&2
  exit 1
} >>"$log" 2>&1
