#!/usr/bin/env bash
# Build script for Jotty Android — produces .apk file(s)
# Usage: ./build.sh              # build debug APK (default)
#        ./build.sh --release    # build release APK (requires signing config)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RELEASE=0
for arg in "$@"; do
  case "$arg" in
    --release|-release|-Release|--Release|-r)
      RELEASE=1
      ;;
    -h|--help)
      echo "Usage: $0 [--release]"
      echo "  (default)  assembleDebug → jotty-android.apk"
      echo "  --release  assembleRelease (requires keystore / signing in app/build.gradle.kts)"
      exit 0
      ;;
    *)
      echo "Unknown option: $arg (try --help)" >&2
      exit 1
      ;;
  esac
done

# Ensure Gradle wrapper JAR exists and matches gradle-wrapper.properties (often missing when repo is cloned)
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="gradle/wrapper/gradle-wrapper.properties"

gradle_version_from_properties() {
  [[ -f "$WRAPPER_PROPS" ]] || return 1
  sed -n 's/^distributionUrl=.*gradle-\([0-9][0-9.]*\)-.*/\1/p' "$WRAPPER_PROPS" | head -n1
}

wrapper_jar_usable() {
  [[ -f "$WRAPPER_JAR" ]] || return 1
  unzip -p "$WRAPPER_JAR" META-INF/MANIFEST.MF 2>/dev/null | grep -q "^Main-Class:" || return 1
  return 0
}

GRADLE_TAG_VER="$(gradle_version_from_properties)"
GRADLE_TAG_VER="${GRADLE_TAG_VER:-9.1.0}"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_TAG_VER}/gradle/wrapper/gradle-wrapper.jar"

if ! wrapper_jar_usable; then
  echo "Gradle wrapper JAR missing or invalid (re-downloading for Gradle ${GRADLE_TAG_VER})..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$WRAPPER_JAR" "$WRAPPER_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "Need curl or wget to download the wrapper JAR." >&2
    exit 1
  fi
  if ! wrapper_jar_usable; then
    echo "Downloaded wrapper JAR still invalid. Try: rm -f $WRAPPER_JAR && ./build.sh" >&2
    echo "Or install Gradle and run: gradle wrapper" >&2
    exit 1
  fi
  echo "Wrapper JAR installed." >&2
fi

# Returns major Java version (e.g. 8 for 1.8, 17 for 17) from a java binary path
java_major_version() {
  local out
  out="$("$1" -version 2>&1 | head -n1)"
  # Typical: openjdk version "17.0.14" / java version "1.8.0_392"
  if [[ "$out" =~ version\ \"1\.([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  if [[ "$out" =~ version\ \"([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  # Some runtimes omit quotes: "openjdk 21.0.5 2024-10-15"
  if [[ "$out" =~ [[:space:]]([0-9]+)\.[0-9] ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  echo "0"
}

# Derive JAVA_HOME from a java executable (follows symlinks when possible)
java_home_from_binary() {
  local java_bin="$1"
  local java_real dir
  if command -v realpath >/dev/null 2>&1; then
    java_real="$(realpath "$java_bin" 2>/dev/null)" || java_real="$java_bin"
  elif command -v readlink >/dev/null 2>&1; then
    java_real="$(readlink -f "$java_bin" 2>/dev/null)" || java_real="$java_bin"
  else
    java_real="$java_bin"
  fi
  dir="$(dirname "$java_real")"
  if [[ "$(basename "$dir")" == "bin" ]]; then
    dirname "$dir"
  fi
}

try_set_java_home() {
  local home="$1"
  [[ -z "$home" || ! -d "$home" || ! -x "$home/bin/java" ]] && return 1
  local v
  v="$(java_major_version "$home/bin/java")"
  v="${v:-0}"
  if [[ "$v" -ge "$MIN_JAVA" ]]; then
    export JAVA_HOME="$home"
    echo "Using Java ${v} from: $home" >&2
    return 0
  fi
  return 1
}

MIN_JAVA=11
need_java=1

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  if try_set_java_home "${JAVA_HOME}"; then
    need_java=0
  fi
fi

# java on PATH (often /usr/bin/java -> distro alternatives)
if [[ "$need_java" -eq 1 ]] && command -v java >/dev/null 2>&1; then
  jh="$(java_home_from_binary "$(command -v java)")"
  if [[ -n "$jh" ]] && try_set_java_home "$jh"; then
    need_java=0
  fi
fi

if [[ "$need_java" -eq 1 ]]; then
  candidates=(
    "${JAVA_HOME:-}"
    "${HOME}/.sdkman/candidates/java/current"
    "/opt/android-studio/jbr"
    "${HOME}/android-studio/jbr"
    "/usr/lib/jvm/default-java"
    "/usr/lib/jvm/java-25-openjdk-amd64"
    "/usr/lib/jvm/java-25-openjdk"
    "/usr/lib/jvm/java-21-openjdk-amd64"
    "/usr/lib/jvm/java-21-openjdk"
    "/usr/lib/jvm/java-17-openjdk-amd64"
    "/usr/lib/jvm/java-17-openjdk"
    "/usr/lib/jvm/java-11-openjdk-amd64"
    "/usr/lib/jvm/java-11-openjdk"
  )
  # Everything under /usr/lib/jvm (covers Fedora java-17-openjdk, Amazon Corretto paths, etc.)
  if [[ -d /usr/lib/jvm ]]; then
    for d in /usr/lib/jvm/*; do
      [[ -d "$d" ]] || continue
      candidates+=("$d")
    done
  fi
  # JetBrains Toolbox Android Studio bundled JBR (if installed)
  toolbox_base="${HOME}/.local/share/JetBrains/Toolbox/apps/AndroidStudio"
  if [[ -d "$toolbox_base" ]]; then
    while IFS= read -r jbr; do
      [[ -n "$jbr" ]] && candidates+=("$jbr")
    done < <(find "$toolbox_base" -maxdepth 4 -type d -name jbr 2>/dev/null)
  fi
  # De-duplicate paths while preserving order
  declare -A seen=()
  for c in "${candidates[@]}"; do
    [[ -z "$c" ]] && continue
    [[ -n "${seen[$c]:-}" ]] && continue
    seen[$c]=1
    if try_set_java_home "$c"; then
      need_java=0
      break
    fi
  done
fi

if [[ "$need_java" -eq 1 ]] || [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "This build requires Java ${MIN_JAVA} or newer. No suitable JDK found." >&2
  if command -v java >/dev/null 2>&1; then
    echo "Found: $(command -v java) — $(java -version 2>&1 | head -n1)" >&2
  else
    echo "'java' is not on your PATH." >&2
  fi
  echo "Install a JDK (e.g. sudo apt install openjdk-17-jdk) or set JAVA_HOME to it." >&2
  echo "Example: export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64" >&2
  exit 1
fi

# Android SDK: Gradle needs sdk.dir in local.properties and/or ANDROID_HOME
ensure_android_sdk() {
  local props="${SCRIPT_DIR}/local.properties"
  local sdk=""

  if [[ -f "$props" ]]; then
    sdk="$(grep '^sdk.dir=' "$props" | head -1 | sed 's/^sdk.dir=//' | tr -d '\r')"
    # Windows-style path in file: optional
    sdk="${sdk//\\//}"
  fi

  if [[ -n "$sdk" && -d "$sdk" ]]; then
    export ANDROID_HOME="${ANDROID_HOME:-$sdk}"
    export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$sdk}"
    return 0
  fi

  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "${HOME}/Android/Sdk" "${HOME}/.config/Android/Sdk"; do
    [[ -z "$candidate" || ! -d "$candidate" ]] && continue
    if [[ -d "$candidate/platform-tools" || -d "$candidate/build-tools" ]]; then
      sdk="$candidate"
      break
    fi
  done

  if [[ -n "$sdk" ]]; then
    export ANDROID_HOME="$sdk"
    export ANDROID_SDK_ROOT="$sdk"
    if [[ ! -f "$props" ]] || ! grep -q '^sdk.dir=' "$props" 2>/dev/null; then
      printf 'sdk.dir=%s\n' "$sdk" >> "$props"
      echo "Wrote Android SDK path to local.properties: $sdk" >&2
    fi
    return 0
  fi

  echo "" >&2
  echo "Android SDK not found. The build needs platform tools and a compile SDK." >&2
  echo "Install Android Studio (SDK Manager) or Android command-line tools, then either:" >&2
  echo "  • export ANDROID_HOME=\"\$HOME/Android/Sdk\"   # default Studio location on Linux" >&2
  echo "  • or create ${props} with: sdk.dir=/path/to/Android/Sdk" >&2
  echo "See local.properties.example and https://developer.android.com/studio#command-tools" >&2
  exit 1
}

ensure_android_sdk

if [[ "$RELEASE" -eq 1 ]]; then
  task="assembleRelease"
  variant="release"
else
  task="assembleDebug"
  variant="debug"
fi

echo "Building ${variant} APK..." >&2
./gradlew "$task" --no-daemon

apk_dir="app/build/outputs/apk/${variant}"
apk=""
if [[ -d "$apk_dir" ]]; then
  apk="$(find "$apk_dir" -maxdepth 1 -name '*.apk' -print -quit)"
fi

if [[ -n "$apk" && -f "$apk" ]]; then
  out_apk="${SCRIPT_DIR}/jotty-android.apk"
  cp -f "$apk" "$out_apk"
  echo ""
  echo "Build succeeded."
  echo "APK: $out_apk"
else
  echo "Build completed but APK not found under $apk_dir" >&2
fi
