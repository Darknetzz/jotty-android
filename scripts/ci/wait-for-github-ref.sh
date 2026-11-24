#!/usr/bin/env bash
# Wait until a GitHub ref (branch or tag) resolves to SHA. GitHub gets commits from the GitLab push
# mirror, which can lag a few minutes (GitLab waits at least 5 minutes between mirror runs).
# Exit: 0 = matched, 3 = superseded (GitHub ref is ahead of SHA), 1 = timed out / diverged.
set -euo pipefail

ref="${1:?usage: wait-for-github-ref.sh <branch-or-tag> <sha> [timeout-seconds]}"
sha="${2:?usage: wait-for-github-ref.sh <branch-or-tag> <sha> [timeout-seconds]}"
timeout="${3:-600}"
repo="${GH_REPO:?GH_REPO not set}"

deadline=$((SECONDS + timeout))
while :; do
  remote="$(gh api "repos/${repo}/commits/${ref}" --jq .sha 2>/dev/null || true)"
  if [[ "$remote" == "$sha" ]]; then
    echo "GitHub ${ref} is at ${sha:0:7}."
    exit 0
  fi
  if [[ -n "$remote" ]]; then
    status="$(gh api "repos/${repo}/compare/${sha}...${remote}" --jq .status 2>/dev/null || true)"
    if [[ "$status" == "ahead" ]]; then
      echo "GitHub ${ref} moved on to ${remote:0:7}; ${sha:0:7} is superseded."
      exit 3
    fi
  fi
  if [[ $SECONDS -ge $deadline ]]; then
    echo "Timed out waiting for GitHub ${ref} = ${sha:0:7} (currently ${remote:-missing}). Check the GitLab push mirror (Settings > Repository > Mirroring repositories)." >&2
    exit 1
  fi
  sleep 5
done
