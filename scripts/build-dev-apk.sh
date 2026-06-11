#!/usr/bin/env bash
# Build a dev APK locally — mirrors .github/workflows/dev-latest.yml build steps.
set -euo pipefail

OUTPUT_DIR="."

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="${2:-.}"; shift 2 ;;
    -h|--help) echo "Usage: $0 [--output-dir DIR]"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/gradle-env.sh
source "$SCRIPT_DIR/lib/gradle-env.sh"
init_jotty_gradle_env "$REPO_ROOT"

VERSION_NAME="$(get_gradle_property "$REPO_ROOT" "VERSION_NAME")"
DEV_CODE="$(compute_dev_version_code "$REPO_ROOT")"
SHA="$(git -C "$REPO_ROOT" rev-parse --short=7 HEAD)"

echo "Dev build: VERSION_NAME=$VERSION_NAME VERSION_CODE=$DEV_CODE SHA=$SHA"

if has_release_keystore "$REPO_ROOT"; then
  invoke_jotty_gradlew "$REPO_ROOT" assembleRelease -PDEV_BUILD_SHA="$SHA" -PVERSION_CODE="$DEV_CODE"
  SRC="$REPO_ROOT/app/build/outputs/apk/release/app-release.apk"
  OUT_NAME="jotty-android-${VERSION_NAME}-dev.apk"
else
  echo "No keystore.properties — building debug-signed dev APK."
  invoke_jotty_gradlew "$REPO_ROOT" assembleDebug -PDEV_BUILD_SHA="$SHA" -PVERSION_CODE="$DEV_CODE"
  SRC="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
  OUT_NAME="jotty-android-${VERSION_NAME}-dev-debug.apk"
fi

DEST_DIR="$OUTPUT_DIR"
[[ "$DEST_DIR" = /* ]] || DEST_DIR="$REPO_ROOT/$DEST_DIR"
mkdir -p "$DEST_DIR"
DEST="$DEST_DIR/$OUT_NAME"
cp "$SRC" "$DEST"
echo "APK: $DEST"
