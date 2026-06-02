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
  echo "[DryRun] Would promote CHANGELOG [dev-latest] to [$VERSION] ($DATE) and reset [dev-latest]"
  exit 0
fi

sed -i.bak -E "s/^VERSION_NAME=.*/VERSION_NAME=${VERSION}/" "$GRADLE_FILE"
sed -i.bak -E "s/^VERSION_CODE=.*/VERSION_CODE=${NEXT_CODE}/" "$GRADLE_FILE"
rm -f "${GRADLE_FILE}.bak"

if grep -q "^## \\[${VERSION}\\]" "$CHANGELOG_FILE"; then
  echo "CHANGELOG.md already contains version ${VERSION}" >&2
  exit 1
fi

DEV_LATEST_URL="https://github.com/Darknetzz/jotty-android/releases/tag/dev-latest"

python3 - "$CHANGELOG_FILE" "$VERSION" "$DATE" "$DEV_LATEST_URL" <<'PY'
import re
import sys

path, version, date, dev_latest_url = sys.argv[1:5]
text = open(path, "r", encoding="utf-8").read()

dev_heading = re.compile(
    r"^## \[(?:dev-latest|[^\]]+-dev)\](?:\([^\)]+\))?(?: - \[[^\]]+\]\([^\)]+\))?\s*$",
    re.M,
)
dev_match = dev_heading.search(text)
if not dev_match:
    raise SystemExit(
        "Could not find a rolling dev changelog section (## [dev-latest] or ## [VERSION-dev])"
    )

if re.search(rf"^## \[{re.escape(version)}\] - ", text, re.M):
    raise SystemExit(f"CHANGELOG.md already contains version {version}")

after_dev = dev_match.end()
next_stable = re.compile(r"^## \[[^\]]+\] - \d{4}-\d{2}-\d{2}\s*$", re.M)
next_match = next_stable.search(text, after_dev)
if not next_match:
    raise SystemExit("Could not find the next dated stable section after the dev section")

dev_body = text[after_dev:next_match.start()].strip()
dev_body = re.sub(r"^\n---\n", "", dev_body)
dev_body = re.sub(r"\n---\s*$", "", dev_body)

new_dev = f"## [dev-latest]({dev_latest_url})"
release_header = f"## [{version}] - {date}"
promoted = f"{new_dev}\n\n---\n\n{release_header}\n\n{dev_body}\n\n---\n\n"

new_text = text[: dev_match.start()] + promoted + text[next_match.start() :]

link = f"[{version}]: https://github.com/Darknetzz/jotty-android/releases/tag/v{version}"
if not re.search(rf"^\[{re.escape(version)}\]:\s+", new_text, flags=re.M):
    lines = new_text.splitlines()
    insert_at = next(
        (
            i
            for i, line in enumerate(lines)
            if re.match(r"^\[\d+\.\d+(?:\.\d+)*(?:-[^\]]+)?\]:\s+", line)
        ),
        None,
    )
    if insert_at is None:
        new_text = new_text.rstrip() + "\n\n" + link + "\n"
    else:
        lines.insert(insert_at, link)
        new_text = "\n".join(lines) + ("\n" if text.endswith("\n") else "")

open(path, "w", encoding="utf-8", newline="\n").write(new_text)
PY

echo "Release prep complete:"
echo "  VERSION_NAME=$VERSION"
echo "  VERSION_CODE=$NEXT_CODE"
echo "  CHANGELOG.md updated with [$VERSION] - $DATE"
