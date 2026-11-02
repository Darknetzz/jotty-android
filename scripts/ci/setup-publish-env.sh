#!/usr/bin/env bash
# GitLab CI publish jobs: install gh, check GH_TOKEN, and write keystore.properties from CI variables.
# Required CI variables: GH_TOKEN, GH_REPO, ANDROID_KEYSTORE_B64, ANDROID_KEYSTORE_PASSWORD,
# ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD. Optional: GH_CLI_VERSION.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

missing=()
for var in GH_TOKEN GH_REPO ANDROID_KEYSTORE_B64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  [[ -n "${!var:-}" ]] || missing+=("$var")
done
if [[ ${#missing[@]} -gt 0 ]]; then
  # Publishing must be release-signed: debug-signed APKs break in-place updates (issue #9).
  echo "Missing CI variables: ${missing[*]} (protected variables need a protected branch/tag)." >&2
  exit 1
fi

if ! command -v gh >/dev/null; then
  version="${GH_CLI_VERSION:?GH_CLI_VERSION not set}"
  arch="$(dpkg --print-architecture 2>/dev/null || echo amd64)"
  tmp="$(mktemp -d)"
  curl -fsSL "https://github.com/cli/cli/releases/download/v${version}/gh_${version}_linux_${arch}.tar.gz" \
    | tar -xz -C "$tmp"
  install -m 0755 "$tmp/gh_${version}_linux_${arch}/bin/gh" /usr/local/bin/gh
  rm -rf "$tmp"
fi
gh --version | head -n 1
gh api "repos/${GH_REPO}" --jq .full_name >/dev/null

keystore="$REPO_ROOT/ci-release.keystore"
printf '%s' "$ANDROID_KEYSTORE_B64" | base64 -d >"$keystore"
{
  echo "storeFile=$keystore"
  echo "storePassword=$ANDROID_KEYSTORE_PASSWORD"
  echo "keyAlias=$ANDROID_KEY_ALIAS"
  echo "keyPassword=$ANDROID_KEY_PASSWORD"
} >"$REPO_ROOT/keystore.properties"
echo "Release keystore configured."
