#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 [version] [--date YYYY-MM-DD] [--dry-run]"
  echo "Example: $0 1.3.1 --date 2026-05-05"
}

VERSION=""

DATE="$(date +%F)"
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="${2:-}"
      if [[ -z "$VERSION" ]]; then
        echo "Missing value for --version" >&2
        exit 1
      fi
      shift 2
      ;;
    --date)
      DATE="${2:-}"
      if [[ -z "$DATE" ]]; then
        echo "Missing value for --date" >&2
        exit 1
      fi
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -z "$VERSION" ]]; then
        VERSION="$1"
        shift
      else
        echo "Unknown argument: $1" >&2
        usage
        exit 1
      fi
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

GRADLE_FILE="gradle.properties"
CHANGELOG_FILE="CHANGELOG.md"

[[ -f "$GRADLE_FILE" ]] || { echo "Missing $GRADLE_FILE" >&2; exit 1; }
[[ -f "$CHANGELOG_FILE" ]] || { echo "Missing $CHANGELOG_FILE" >&2; exit 1; }

CURRENT_VERSION_NAME="$(sed -n 's/^VERSION_NAME=\(.*\)$/\1/p' "$GRADLE_FILE" | head -n 1)"
[[ -n "$CURRENT_VERSION_NAME" ]] || { echo "Could not parse VERSION_NAME from $GRADLE_FILE" >&2; exit 1; }

default_bumped_version() {
  local current="$1"
  IFS='.' read -r -a parts <<< "$current"
  local last_index=$(( ${#parts[@]} - 1 ))
  local last_part="${parts[$last_index]}"
  [[ "$last_part" =~ ^[0-9]+$ ]] || {
    echo "Cannot auto-bump version '$current' because last segment is not numeric" >&2
    exit 1
  }
  parts[$last_index]=$(( last_part + 1 ))
  local joined="${parts[0]}"
  for ((i=1; i<${#parts[@]}; i++)); do
    joined="${joined}.${parts[$i]}"
  done
  echo "$joined"
}

if [[ -z "$VERSION" ]]; then
  DEFAULT_VERSION="$(default_bumped_version "$CURRENT_VERSION_NAME")"
  read -r -p "Release version [${DEFAULT_VERSION}]: " input_version
  VERSION="${input_version:-$DEFAULT_VERSION}"
fi

CURRENT_CODE="$(sed -n 's/^VERSION_CODE=\([0-9][0-9]*\)$/\1/p' "$GRADLE_FILE" | head -n 1)"
[[ -n "$CURRENT_CODE" ]] || { echo "Could not parse VERSION_CODE from $GRADLE_FILE" >&2; exit 1; }
NEXT_CODE=$((CURRENT_CODE + 1))

if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "[DryRun] Would set VERSION_NAME=$VERSION"
  echo "[DryRun] Would increment VERSION_CODE $CURRENT_CODE -> $NEXT_CODE"
  echo "[DryRun] Would promote changelog Unreleased to $VERSION ($DATE)"
  exit 0
fi

sed -i.bak -E "s/^VERSION_NAME=.*/VERSION_NAME=${VERSION}/" "$GRADLE_FILE"
sed -i.bak -E "s/^VERSION_CODE=.*/VERSION_CODE=${NEXT_CODE}/" "$GRADLE_FILE"
rm -f "${GRADLE_FILE}.bak"

if grep -q "^## \[${VERSION}\]" "$CHANGELOG_FILE"; then
  echo "CHANGELOG.md already contains version ${VERSION}" >&2
  exit 1
fi

if grep -q "^## \[Unreleased\]$" "$CHANGELOG_FILE"; then
  python3 - "$CHANGELOG_FILE" "$VERSION" "$DATE" <<'PY'
import re
import sys

path, version, date = sys.argv[1], sys.argv[2], sys.argv[3]
text = open(path, "r", encoding="utf-8").read()

header = f"## [Unreleased]\n\n---\n\n## [{version}] - {date}"

new_text = re.sub(
    r"## \[Unreleased\]\n\n---\n\n## \[[^\]]+\] - [^\n]+",
    header,
    text,
    count=1,
    flags=re.M,
)

if new_text == text:
    new_text = re.sub(r"## \[Unreleased\]", header, text, count=1, flags=re.M)

link = f"[{version}]: https://github.com/Darknetzz/jotty-android/releases/tag/v{version}"
if not re.search(rf"^\[{re.escape(version)}\]:\s+", new_text, flags=re.M):
    new_text = new_text.rstrip() + "\n\n" + link + "\n"

open(path, "w", encoding="utf-8", newline="\n").write(new_text)
PY
else
  echo "Could not find '## [Unreleased]' in CHANGELOG.md" >&2
  exit 1
fi

echo "Release prep complete:"
echo "  VERSION_NAME=$VERSION"
echo "  VERSION_CODE=$NEXT_CODE"
echo "  CHANGELOG.md updated with [$VERSION] - $DATE"
