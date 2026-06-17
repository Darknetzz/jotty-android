#!/usr/bin/env bash
# Build dev APK locally and publish to the dev-latest GitHub release (no Actions).
set -euo pipefail

DRY_RUN=0
SKIP_PUBLISH=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    --skip-publish) SKIP_PUBLISH=1; shift ;;
    -h|--help) echo "Usage: $0 [--dry-run] [--skip-publish]"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

if [[ "$SKIP_PUBLISH" -eq 0 ]]; then
  command -v gh >/dev/null || { echo "gh CLI required" >&2; exit 1; }
  gh auth status >/dev/null
fi

APK_PATH="$("$SCRIPT_DIR/build-dev-apk.sh" --output-dir "$REPO_ROOT" | tail -n 1)"
if [[ ! -f "$APK_PATH" ]]; then
  echo "Build did not produce APK at: $APK_PATH" >&2
  exit 1
fi
SHA="$(git rev-parse HEAD)"
SHORT_SHA="$(git rev-parse --short=7 HEAD)"
BASE_CODE="$(grep -E '^VERSION_CODE=' gradle.properties | cut -d= -f2)"
RUN_NUM="$(git rev-list --count HEAD)"
DEV_CODE=$((BASE_CODE * 10000 + RUN_NUM % 10000))
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo "OWNER/REPO")"

BODY="Rolling pre-release build from \`dev\` (built locally).
⚠️ This preview build may contain unstable or breaking bugs.

Commit: ${SHA}
VersionCode: ${DEV_CODE}

| Item | Link |
| --- | --- |
| Commit | [\`${SHORT_SHA}\`](https://github.com/${REPO}/commit/${SHA}) |
| Version code | ${DEV_CODE} |
| Changelog | [CHANGELOG.md](https://github.com/${REPO}/blob/dev/CHANGELOG.md) |"

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "[DryRun] Would publish dev-latest with asset: $APK_PATH"
  exit 0
fi

if [[ "$SKIP_PUBLISH" -eq 1 ]]; then
  echo "Built only (skip publish): $APK_PATH"
  exit 0
fi

gh release delete dev-latest --cleanup-tag --yes 2>/dev/null || true
gh api -X DELETE "repos/${REPO}/git/refs/tags/dev-latest" 2>/dev/null || true

notes_file="$(mktemp)"
trap 'rm -f "$notes_file"' EXIT
printf '%s\n' "$BODY" >"$notes_file"

gh release create dev-latest \
  --target "$(git branch --show-current)" \
  --title "Dev Latest" \
  --notes-file "$notes_file" \
  --prerelease \
  "$APK_PATH"

echo "Done: https://github.com/${REPO}/releases/tag/dev-latest"
