#!/usr/bin/env bash
# GitLab CI: build the release-signed APK for tag vX.Y.Z and publish the GitHub release
# (notes from CHANGELOG.md). Idempotent: re-running updates notes and replaces the APK.
set -euo pipefail

tag="${1:?usage: publish-github-release.sh <vX.Y.Z>}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=scripts/lib/gradle-env.sh
source "$REPO_ROOT/scripts/lib/gradle-env.sh"
# shellcheck source=scripts/lib/release-notes.sh
source "$REPO_ROOT/scripts/lib/release-notes.sh"

version="${tag#v}"
gradle_version="$(get_gradle_property "$REPO_ROOT" "VERSION_NAME")"
if [[ "$version" != "$gradle_version" ]]; then
  echo "Tag $tag does not match VERSION_NAME=$gradle_version in gradle.properties." >&2
  exit 1
fi

notes="$(mktemp)"
trap 'rm -f "$notes"' EXIT
write_release_notes "$REPO_ROOT" "$version" "$notes"

apk="$(bash "$REPO_ROOT/scripts/build-release-apk.sh" --output-dir "$REPO_ROOT" | tail -n 1)"
[[ "$apk" == *"jotty-android-${version}.apk" && -f "$apk" ]] || {
  echo "Expected release-signed jotty-android-${version}.apk, got: $apk" >&2
  exit 1
}

bash "$SCRIPT_DIR/wait-for-github-ref.sh" "$tag" "$(git -C "$REPO_ROOT" rev-parse HEAD)"

if gh release view "$tag" >/dev/null 2>&1; then
  echo "Release $tag exists; updating notes."
  gh release edit "$tag" --title "$tag" --notes-file "$notes"
else
  gh release create "$tag" --verify-tag --title "$tag" --notes-file "$notes"
fi
gh release upload "$tag" "$apk" --clobber
echo "Done: https://github.com/${GH_REPO}/releases/tag/${tag}"
