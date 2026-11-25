#!/usr/bin/env bash
# Build dev APK locally and publish to the dev-latest GitHub release (no Actions).
set -euo pipefail
# origin is GitLab, so gh can't infer the GitHub repo from the remotes.
export GH_REPO="${GH_REPO:-Darknetzz/jotty-android}"

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

SHA="$(git rev-parse HEAD)"
SHORT_SHA="$(git rev-parse --short=7 HEAD)"
BASE_CODE="$(grep -E '^VERSION_CODE=' gradle.properties | cut -d= -f2)"
RUN_NUM="$(git rev-list --count HEAD)"
DEV_CODE=$((BASE_CODE * 10000 + RUN_NUM % 10000))

APK_PATH="$("$SCRIPT_DIR/build-dev-apk.sh" --output-dir "$REPO_ROOT" | tail -n 1)"
if [[ ! -f "$APK_PATH" ]]; then
  echo "Build did not produce APK at: $APK_PATH" >&2
  exit 1
fi
REPO="$GH_REPO"
BUILT_BY="built locally"
[[ -n "${CI_PIPELINE_URL:-}" ]] && BUILT_BY="built by [GitLab CI](${CI_PIPELINE_URL})"

BODY="Rolling pre-release build from \`dev\` (${BUILT_BY}).
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

# The release targets SHA on GitHub, which gets commits from the GitLab push mirror.
bash "$SCRIPT_DIR/ci/wait-for-github-ref.sh" dev "$SHA" 600 || [[ $? -eq 3 ]]

gh release delete dev-latest --cleanup-tag --yes 2>/dev/null || true
# The tag is usually gone already (--cleanup-tag); gh prints the 422 body on stdout, so silence both.
gh api -X DELETE "repos/${REPO}/git/refs/tags/dev-latest" >/dev/null 2>&1 || true

notes_file="$(mktemp)"
trap 'rm -f "$notes_file"' EXIT
printf '%s\n' "$BODY" >"$notes_file"

gh release create dev-latest \
  --target "$SHA" \
  --title "Dev Latest" \
  --notes-file "$notes_file" \
  --prerelease \
  "$APK_PATH"

# GitLab (origin) is the source of truth: move its dev-latest tag to the same commit, after GitHub,
# so the push mirror finds both tags equal. In CI this pushes with the job token ("Allow Git push
# requests" under Settings > CI/CD > Job token permissions); locally it uses your origin credentials.
if [[ -n "${CI_JOB_TOKEN:-}" && -n "${CI_SERVER_HOST:-}" ]]; then
  gitlab_url="${CI_SERVER_PROTOCOL:-https}://gitlab-ci-token:${CI_JOB_TOKEN}@${CI_SERVER_HOST}:${CI_SERVER_PORT:-443}/${CI_PROJECT_PATH}.git"
else
  gitlab_url=origin
fi
if git push --quiet --force "$gitlab_url" "${SHA}:refs/tags/dev-latest"; then
  echo "GitLab tag dev-latest -> ${SHORT_SHA}"
else
  echo "ERROR: could not move the GitLab dev-latest tag (job token push not allowed?)" >&2
  exit 1
fi

echo "Done: https://github.com/${REPO}/releases/tag/dev-latest"
