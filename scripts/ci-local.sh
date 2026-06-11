#!/usr/bin/env bash
# Local CI — mirrors .github/workflows/ci.yml (without GitHub-hosted runners).
set -euo pipefail

SKIP_RELEASE=0
SMOKE_TEST=0
SKIP_LINT=0

usage() {
  echo "Usage: $0 [--skip-release] [--smoke-test] [--skip-lint]"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-release) SKIP_RELEASE=1; shift ;;
    --smoke-test) SMOKE_TEST=1; shift ;;
    --skip-lint) SKIP_LINT=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=lib/gradle-env.sh
source "$SCRIPT_DIR/lib/gradle-env.sh"
init_jotty_gradle_env "$REPO_ROOT"

echo "=== Unit tests ==="
invoke_jotty_gradlew "$REPO_ROOT" test

if [[ "$SKIP_LINT" -eq 0 ]]; then
  echo "=== Lint (debug + release) ==="
  invoke_jotty_gradlew "$REPO_ROOT" lintDebug lintRelease
  echo "=== ktlint ==="
  invoke_jotty_gradlew "$REPO_ROOT" ktlintCheck
fi

if [[ "$SKIP_RELEASE" -eq 0 ]]; then
  echo "=== Assemble release ==="
  invoke_jotty_gradlew "$REPO_ROOT" assembleRelease
fi

if [[ "$SMOKE_TEST" -eq 1 ]]; then
  echo "=== Instrumentation smoke tests (requires running emulator/AVD) ==="
  invoke_jotty_gradlew "$REPO_ROOT" connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.jotty.android.MainActivitySmokeTest,com.jotty.android.PerformanceBaselineTest
fi

echo ""
echo "Local CI passed."
[[ "$SKIP_LINT" -eq 0 ]] && echo "Reports: app/build/reports/tests/  app/build/reports/lint-results-*.html"
