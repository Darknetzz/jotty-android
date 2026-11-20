#!/usr/bin/env bash
# Shared GitHub release notes for stable releases (publish-release.sh, ci/publish-github-release.sh).
# Source, then: write_release_notes <repo_root> <version> <out_file>

# Prints the body of "## [VERSION] - date" up to the next "---" separator.
extract_changelog_section() {
  local changelog="$1"
  local version="$2"
  local body
  body="$(tr -d '\r' <"$changelog" | awk -v ver="$version" '
    index($0, "## [" ver "] - ") == 1 { found = 1; next }
    found && /^---$/ { exit }
    found { print }
  ' | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')"
  if [[ -z "$body" ]]; then
    echo "Could not find ## [$version] in $changelog" >&2
    return 1
  fi
  printf '%s\n' "$body"
}

write_release_notes() {
  local repo_root="$1"
  local version="$2"
  local out_file="$3"
  local body
  body="$(extract_changelog_section "$repo_root/CHANGELOG.md" "$version")" || return 1
  cat >"$out_file" <<EOF
## Install

Download **\`jotty-android-${version}.apk\`** from this release.

**Full changelog:** https://github.com/Darknetzz/jotty-android/blob/v${version}/CHANGELOG.md

---

${body}
EOF
}
