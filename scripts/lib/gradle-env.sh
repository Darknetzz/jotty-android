#!/usr/bin/env bash
# Shared Java, Android SDK, and Gradle wrapper setup for local build/CI scripts.
# Source from repo scripts: source "$(dirname "$0")/lib/gradle-env.sh"

init_jotty_gradle_env() {
  local repo_root="$1"
  cd "$repo_root" || exit 1

  local min_java=17
  if [[ -z "${JAVA_HOME:-}" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE '"1[7-9]|"[2-9][0-9]'; then
    for candidate in \
      "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
      "${LOCALAPPDATA}/Programs/Android Studio/jbr" \
      "/c/Program Files/Android/Android Studio/jbr" \
      "/mnt/c/Program Files/Android/Android Studio/jbr" \
      "${HOME}/.local/share/JetBrains/Toolbox/apps/AndroidStudio/jbr" \
      "/usr/lib/jvm/java-17-openjdk" \
      "/usr/lib/jvm/java-21-openjdk"; do
      if [[ -x "${candidate}/bin/java" ]]; then
        export JAVA_HOME="$candidate"
        break
      fi
    done
  fi
  if [[ -z "${JAVA_HOME:-}" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE '"1[7-9]|"[2-9][0-9]'; then
    echo "JDK 17+ required. Set JAVA_HOME or install OpenJDK 17." >&2
    exit 1
  fi

  local props="$repo_root/local.properties"
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -f "$props" ]]; then
    local from_props
    from_props="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' "$props" | head -n 1 | tr -d '\r')"
    if [[ -n "$from_props" ]]; then
      # Gradle escapes Windows paths (C\:\\Users\\...); normalize for bash.
      local unescaped="${from_props//\\:/:}"
      unescaped="${unescaped//\\//}"
      for try in "$from_props" "$unescaped"; do
        if [[ -d "$try" ]]; then
          sdk="$try"
          break
        fi
      done
    fi
  fi
  if [[ -z "$sdk" ]]; then
    local win_local="${LOCALAPPDATA:-${HOME}/AppData/Local}/Android/Sdk"
    for candidate in "$win_local" "${HOME}/Android/Sdk" "${HOME}/Library/Android/sdk"; do
      if [[ -d "$candidate/platform-tools" || -d "$candidate/build-tools" ]]; then
        sdk="$candidate"
        break
      fi
    done
  fi
  if [[ -z "$sdk" ]]; then
    echo "Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties." >&2
    exit 1
  fi
  export ANDROID_HOME="$sdk"
  export ANDROID_SDK_ROOT="$sdk"
  if [[ ! -f "$props" ]] || ! grep -q '^sdk\.dir=' "$props" 2>/dev/null; then
    echo "sdk.dir=$sdk" >> "$props"
  fi

  chmod +x "$repo_root/gradlew" 2>/dev/null || true
}

invoke_jotty_gradlew() {
  local repo_root="$1"
  shift
  cd "$repo_root" || exit 1
  ./gradlew --no-daemon "$@"
}

get_gradle_property() {
  local repo_root="$1"
  local name="$2"
  sed -n "s/^${name}=//p" "$repo_root/gradle.properties" | head -n 1 | tr -d '\r'
}

has_release_keystore() {
  local repo_root="$1"
  local keystore_props="$repo_root/keystore.properties"
  [[ -f "$keystore_props" ]] || return 1
  grep -q 'your-keystore-password\|your-key-password' "$keystore_props" && return 1
  local store_file
  store_file="$(sed -n 's/^[[:space:]]*storeFile[[:space:]]*=[[:space:]]*//p' "$keystore_props" | head -n 1 | tr -d '\r')"
  [[ -n "$store_file" ]] || return 1
  if [[ "$store_file" = /* ]]; then
    [[ -f "$store_file" ]]
  else
    [[ -f "$repo_root/$store_file" ]]
  fi
}

compute_dev_version_code() {
  local repo_root="$1"
  local base
  base="$(get_gradle_property "$repo_root" "VERSION_CODE")"
  local run_num
  run_num="$(git -C "$repo_root" rev-list --count HEAD 2>/dev/null || echo 1)"
  echo $(( base * 10000 + run_num % 10000 ))
}

find_aapt_executable() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n "$sdk" && -d "$sdk/build-tools" ]] || return 1
  local dir
  for dir in $(ls -1 "$sdk/build-tools" 2>/dev/null | sort -V -r); do
    if [[ -x "$sdk/build-tools/$dir/aapt" ]]; then
      echo "$sdk/build-tools/$dir/aapt"
      return 0
    fi
  done
  return 1
}

get_apk_version_info() {
  local apk_path="$1"
  local aapt
  aapt="$(find_aapt_executable)" || { echo "Android build-tools aapt not found" >&2; return 1; }
  local line
  line="$("$aapt" dump badging "$apk_path" 2>/dev/null | grep -m1 '^package:')" || {
    echo "Could not read APK metadata from: $apk_path" >&2
    return 1
  }
  local version_code version_name
  version_code="$(sed -n "s/.*versionCode='\([0-9][0-9]*\)'.*/\1/p" <<<"$line")"
  version_name="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$line")"
  if [[ -z "$version_code" || -z "$version_name" ]]; then
    echo "APK metadata missing versionCode/versionName: $apk_path" >&2
    return 1
  fi
  printf '%s\n%s\n' "$version_code" "$version_name"
}

assert_dev_apk_matches_expected() {
  local apk_path="$1"
  local expected_short_sha="$2"
  local expected_version_code="$3"
  local info version_code version_name
  info="$(get_apk_version_info "$apk_path")" || return 1
  version_code="$(sed -n '1p' <<<"$info")"
  version_name="$(sed -n '2p' <<<"$info")"
  if [[ "$version_code" != "$expected_version_code" ]]; then
    echo "Dev APK versionCode mismatch: expected $expected_version_code, got $version_code ($apk_path)" >&2
    return 1
  fi
  if [[ "$version_name" != *"-dev+${expected_short_sha}"* ]]; then
    echo "Dev APK versionName mismatch: expected suffix -dev+${expected_short_sha}, got ${version_name} ($apk_path)" >&2
    return 1
  fi
}
