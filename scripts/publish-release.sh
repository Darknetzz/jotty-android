#!/usr/bin/env bash
# After release.sh: push dev, open dev→main PR, merge, publish GitHub release.
# Use --local-build to build and upload the APK locally (no GitHub Actions).
set -euo pipefail

VERSION=""
DRY_RUN=0
SKIP_PUSH=0
SKIP_PR=0
SKIP_MERGE=0
SKIP_RELEASE=0
WAIT_FOR_CHECKS=0
LOCAL_BUILD=0
CHECK_TIMEOUT_MINUTES=20

usage() {
  echo "Usage: $0 [--version X.Y.Z] [--dry-run] [--skip-push] [--skip-pr] [--skip-merge] [--skip-release] [--wait-for-checks] [--local-build]"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --skip-push) SKIP_PUSH=1; shift ;;
    --skip-pr) SKIP_PR=1; shift ;;
    --skip-merge) SKIP_MERGE=1; shift ;;
    --skip-release) SKIP_RELEASE=1; shift ;;
    --wait-for-checks) WAIT_FOR_CHECKS=1; shift ;;
    --local-build) LOCAL_BUILD=1; shift ;;
    --check-timeout-minutes) CHECK_TIMEOUT_MINUTES="${2:-20}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

GRADLE_FILE="gradle.properties"
CHANGELOG_FILE="CHANGELOG.md"

[[ -f "$GRADLE_FILE" ]] || { echo "Missing $GRADLE_FILE" >&2; exit 1; }
[[ -f "$CHANGELOG_FILE" ]] || { echo "Missing $CHANGELOG_FILE" >&2; exit 1; }

if [[ -z "$VERSION" ]]; then
  VERSION="$(sed -n 's/^VERSION_NAME=\(.*\)$/\1/p' "$GRADLE_FILE" | head -n 1 | tr -d '\r')"
fi
[[ -n "$VERSION" ]] || { echo "Could not read VERSION_NAME" >&2; exit 1; }

TAG="v${VERSION}"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean. Commit release prep first." >&2
  exit 1
fi

BRANCH="$(git branch --show-current)"
[[ "$BRANCH" == "dev" ]] || { echo "Expected branch dev (current: $BRANCH)" >&2; exit 1; }

command -v gh >/dev/null || { echo "gh CLI required" >&2; exit 1; }
gh auth status >/dev/null

extract_changelog() {
  python3 - "$CHANGELOG_FILE" "$VERSION" <<'PY'
import re
import sys

path, version = sys.argv[1:3]
text = open(path, encoding="utf-8").read()
pattern = rf"(?ms)^## \[{re.escape(version)}\] - [^\n]+\n\n(.*?)(?=^---\n\n## |\Z)"
m = re.search(pattern, text)
if not m:
    raise SystemExit(f"Could not find ## [{version}] in CHANGELOG.md")
print(m.group(1).strip())
PY
}

CHANGELOG_BODY="$(extract_changelog)"
NOTES_FILE=".gh-release-${VERSION}.md"
cat > "$NOTES_FILE" <<EOF
## Install

Download **\`jotty-android-${VERSION}.apk\`** from this release.

**Full changelog:** https://github.com/Darknetzz/jotty-android/blob/v${VERSION}/CHANGELOG.md

---

${CHANGELOG_BODY}
EOF

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "[DryRun] version=$VERSION tag=$TAG"
  [[ "$SKIP_PUSH" -eq 1 ]] || echo "[DryRun] git push origin dev"
  [[ "$SKIP_PR" -eq 1 ]] || echo "[DryRun] gh pr create dev -> main"
  [[ "$SKIP_MERGE" -eq 1 ]] || echo "[DryRun] gh pr merge"
  [[ "$SKIP_RELEASE" -eq 1 ]] || echo "[DryRun] gh release create $TAG"
  rm -f "$NOTES_FILE"
  exit 0
fi

if [[ "$SKIP_PUSH" -eq 0 ]]; then
  git push origin dev
fi

PR_NUMBER=""
if [[ "$SKIP_PR" -eq 0 ]]; then
  PR_NUMBER="$(gh pr list --base main --head dev --state open --json number --jq '.[0].number' 2>/dev/null || true)"
  if [[ -z "$PR_NUMBER" || "$PR_NUMBER" == "null" ]]; then
    PR_URL="$(gh pr create --base main --head dev --title "Release v${VERSION}" --body "Stable release v${VERSION}. See CHANGELOG on dev.")"
    PR_NUMBER="$(sed -n 's|.*/pull/\([0-9]*\).*|\1|p' <<<"$PR_URL")"
    echo "Created PR #${PR_NUMBER}"
  else
    echo "Using existing PR #${PR_NUMBER}"
  fi
fi

if [[ "$SKIP_MERGE" -eq 0 && -n "$PR_NUMBER" ]]; then
  if [[ "$WAIT_FOR_CHECKS" -eq 1 && "$LOCAL_BUILD" -eq 0 ]]; then
    deadline=$((SECONDS + CHECK_TIMEOUT_MINUTES * 60))
    while [[ $SECONDS -lt $deadline ]]; do
      if gh pr checks "$PR_NUMBER" 2>/dev/null | grep -qE 'fail|cancel'; then
        echo "PR checks failed" >&2
        exit 1
      fi
      if gh pr checks "$PR_NUMBER" 2>/dev/null | grep -qE 'pending|in_progress'; then
        sleep 20
        continue
      fi
      break
    done
  elif [[ "$WAIT_FOR_CHECKS" -eq 1 && "$LOCAL_BUILD" -eq 1 ]]; then
    echo "Skipping PR check wait (--local-build). Run ./scripts/ci-local.sh before publish if needed."
  fi
  gh pr merge "$PR_NUMBER" --merge --delete-branch=false
  git fetch origin main
fi

if [[ "$SKIP_RELEASE" -eq 0 ]]; then
  if gh release view "$TAG" >/dev/null 2>&1; then
    gh release edit "$TAG" --notes-file "$NOTES_FILE"
  else
    gh release create "$TAG" --target main --title "$TAG" --notes-file "$NOTES_FILE"
  fi
  echo "https://github.com/Darknetzz/jotty-android/releases/tag/${TAG}"
  if [[ "$LOCAL_BUILD" -eq 1 ]]; then
    echo "Building release APK locally..."
    APK_PATH="$("$SCRIPT_DIR/build-release-apk.sh" --output-dir "$REPO_ROOT")"
    echo "Uploading $APK_PATH to $TAG..."
    gh release upload "$TAG" "$APK_PATH" --clobber
    echo "APK attached to release."
  else
    echo "Tip: use --local-build to build and upload the APK without GitHub Actions."
  fi
fi

rm -f "$NOTES_FILE"
if [[ "$LOCAL_BUILD" -eq 1 && "$SKIP_MERGE" -eq 0 ]]; then
  "$SCRIPT_DIR/sync-dev-with-main.sh"
else
  echo "Done. Run ./scripts/sync-dev-with-main.sh after main updates if needed."
fi
