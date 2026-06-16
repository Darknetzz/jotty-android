# Local CI and builds (no GitHub Actions)

Run tests, lint, and APK builds on your machine instead of GitHub-hosted runners. This avoids Actions billing and keeps the same checks under your control.

GitHub Actions workflows in `.github/workflows/` are set to **manual trigger only** (`workflow_dispatch`). Use the scripts below for day-to-day work.

## Quick reference

| GitHub workflow | Local replacement |
|-----------------|-------------------|
| [ci.yml](../.github/workflows/ci.yml) | `.\scripts\ci-local.ps1` / `./scripts/ci-local.sh` |
| [dev-latest.yml](../.github/workflows/dev-latest.yml) | Auto: `setup-repo-git` + `git push origin dev` (pre-push hook). Manual: `publish-dev-latest.ps1` / `.sh` |
| [release-apk.yml](../.github/workflows/release-apk.yml) | `.\scripts\build-release-apk.ps1` + `gh release upload`, or `publish-release.ps1 -LocalBuild` |
| [sync-dev-with-main.yml](../.github/workflows/sync-dev-with-main.yml) | `.\scripts\sync-dev-with-main.ps1` / `./scripts/sync-dev-with-main.sh` |

## Requirements

- **JDK 17+** (Android Studio JBR is fine)
- **Android SDK 36** (`local.properties` or `ANDROID_HOME`)
- **Release signing (optional but recommended):** copy `keystore.properties.example` → `keystore.properties`
- **Publishing releases:** [GitHub CLI](https://cli.github.com/) (`gh auth login`) — uses the API only; no Actions minutes

## CI (tests, lint, ktlint, release assemble)

```powershell
.\scripts\ci-local.ps1
```

```bash
./scripts/ci-local.sh
```

Options:

- `-SkipRelease` / `--skip-release` — skip `assembleRelease` (faster iteration)
- `-SkipLint` / `--skip-lint` — skip lint and ktlint
- `-SmokeTest` / `--smoke-test` — run instrumentation tests (start an AVD first)

Reports land under `app/build/reports/` like on CI.

## Dev APK (rolling dev-latest)

Build only:

```powershell
.\scripts\build-dev-apk.ps1
```

Build and publish to the [dev-latest](https://github.com/Darknetzz/jotty-android/releases/tag/dev-latest) pre-release (no Actions):

```powershell
.\scripts\publish-dev-latest.ps1
```

### Automatic dev-latest on push (local, no Actions)

After **one-time** `.\scripts\setup-repo-git.ps1` (or `./scripts/setup-repo-git.sh`), the **pre-push** hook builds and publishes **dev-latest** in the background after every successful `git push` to `origin/dev` (same end result as the old Actions workflow, but on your machine).

```powershell
git push origin dev
# or
.\scripts\push-dev.ps1
```

- Log: `.git/jotty-dev-publish.log`
- Skip once: `git push --no-verify origin dev` or `JOTTY_SKIP_DEV_PUBLISH=1 git push origin dev`
- Disable: `git config jotty.autoPublishDev false`
- Manual publish still works: `.\scripts\publish-dev-latest.ps1`

Requires `gh auth login`, JDK/Android SDK, and `keystore.properties` for release-signed dev APKs (recommended for in-app updates).

## Stable release APK

**Option A — full publish with local APK** (recommended when avoiding Actions):

```powershell
.\release.ps1
git add gradle.properties CHANGELOG.md
git commit -m "Release vX.Y.Z"
.\scripts\publish-release.ps1 -LocalBuild
```

`-LocalBuild` runs `build-release-apk.ps1` and uploads the APK with `gh release upload` instead of waiting for `release-apk.yml`.

**Option B — build only:**

```powershell
.\scripts\build-release-apk.ps1
gh release upload vX.Y.Z jotty-android-X.Y.Z.apk --clobber
```

After merge to `main`, sync branches locally:

```powershell
.\scripts\sync-dev-with-main.ps1
```

## Disable GitHub Actions entirely (optional)

In the repo on GitHub: **Settings → Actions → General → Disable actions**. Local scripts and `gh` still work for releases.

To re-enable cloud CI later, restore `on: push` / `pull_request` triggers in the workflow YAML files.

## Optional: run workflow YAML with [act](https://github.com/nektos/act)

If you prefer executing the YAML locally:

```bash
act pull_request -j unit-tests
```

Android/emulator jobs need Docker and extra setup; the scripts above are simpler for this project.
