# CI/CD and local builds

Day-to-day CI/CD runs on **GitLab CI** ([`.gitlab-ci.yml`](../.gitlab-ci.yml), `gitlab.roste.org`). GitHub stays the public home: commits reach both remotes through a dual push URL on `origin`, and the GitLab publish jobs create the GitHub tags and releases with `gh`.

GitHub Actions workflows in `.github/workflows/` are **manual-only** (`workflow_dispatch`) fallbacks. The local scripts below still work when GitLab is unavailable.

## Quick reference

| What | GitLab CI job | Local fallback |
|------|---------------|----------------|
| Tests, lint, ktlint | `test` (every push / MR) | `.\scripts\ci-local.ps1` / `./scripts/ci-local.sh` |
| Rolling [dev-latest](https://github.com/Darknetzz/jotty-android/releases/tag/dev-latest) | `dev-latest` (push to `dev`) | `publish-dev-latest.ps1` / `.sh`, or opt-in pre-push hook |
| Stable release `vX.Y.Z` | `github-release` (tag `vX.Y.Z`) | `publish-release.ps1 -LocalBuild` / `.sh --local-build` |
| Sync `dev` with `main` | — | `.\scripts\sync-dev-with-main.ps1` / `.sh` |

## GitLab CI/CD

### Pipeline

- **`test`** — `scripts/ci-local.sh --skip-release` (unit tests, lint, ktlint). On `main` and release tags it checks debug and release variants; on `dev`, other branches and MRs it adds `--debug-only` (debug unit tests + `lintDebug`), roughly halving the time. JUnit results and lint reports are attached to the pipeline.
- **`dev-latest`** — on push to `dev`: waits until GitHub `dev` is at the same commit (skips if a newer commit superseded it), builds the release-signed dev APK (`build-dev-apk.sh`), then recreates the `dev-latest` tag and pre-release on GitHub (`publish-dev-latest.sh`). Serialized with `resource_group`.
- **`github-release`** — on tag `vX.Y.Z`: checks the tag matches `VERSION_NAME`, builds `jotty-android-X.Y.Z.apk`, creates (or updates) the GitHub release with notes from `CHANGELOG.md`, and uploads the APK. Safe to re-run.

Publish jobs refuse to run without the release keystore: debug-signed APKs break in-place updates (issue #9). Image: `ghcr.io/cirruslabs/android-sdk:36` (Docker runner); `gh` version is pinned by `GH_CLI_VERSION`.

### One-time setup

1. **GitLab project:** create `kriss/jotty-android` on `gitlab.roste.org` (blank, no README).
2. **Protect** branches `dev` and `main` and tags `v*` (Settings → Repository → Protected branches / tags), so protected variables reach the publish jobs.
3. **CI/CD variables** (Settings → CI/CD → Variables, *Protected* + *Masked*):

   | Variable | Value |
   |----------|-------|
   | `GH_TOKEN` | GitHub fine-grained PAT for `Darknetzz/jotty-android` with **Contents: read and write** |
   | `ANDROID_KEYSTORE_B64` | base64 of the release keystore (see `keystore.properties.example`) |
   | `ANDROID_KEYSTORE_PASSWORD` | store password |
   | `ANDROID_KEY_ALIAS` | key alias |
   | `ANDROID_KEY_PASSWORD` | key password |

4. **Dual push** (per clone): one `git push origin …` updates GitHub first, then GitLab. Fetches still come from GitHub.

   ```bash
   git remote set-url --add --push origin git@github.com:Darknetzz/jotty-android.git
   git remote set-url --add --push origin git@gitlab.roste.org:kriss/jotty-android.git
   git push origin main dev
   ```

5. Run `.\scripts\setup-repo-git.ps1` / `./scripts/setup-repo-git.sh` once per clone (force-fetch for the moving `dev-latest` tag, `.githooks`).

### Dev builds

```bash
git push origin dev
```

The dev APK `versionName` is `VERSION_NAME-dev+<7-char-sha>`; `versionCode` is `VERSION_CODE * 10000 + (git rev-list --count HEAD) % 10000`, so the pipeline clones with full history (`GIT_DEPTH: 0`).

### Stable releases

```powershell
.\release.ps1
git add gradle.properties CHANGELOG.md
git commit -m "Release vX.Y.Z"
.\scripts\publish-release.ps1
```

`publish-release` pushes `dev`, opens and merges the `dev` → `main` PR on GitHub, tags the merged `main` as `vX.Y.Z`, pushes `main` + the tag to both remotes, and syncs `dev` with `main`. The GitLab `github-release` job then publishes the GitHub release and APK. On Unix, `./release.sh` and `./scripts/publish-release.sh` are equivalent; both release scripts validate changelog promotion with `--dry-run` / `-DryRun`.

## Local fallbacks

### Requirements

- **JDK 17+** (Android Studio JBR is fine)
- **Android SDK 36** (`local.properties` or `ANDROID_HOME`)
- **Release signing:** copy `keystore.properties.example` → `keystore.properties`
- **Publishing:** [GitHub CLI](https://cli.github.com/) (`gh auth login`)

### CI checks

```powershell
.\scripts\ci-local.ps1
```

Options: `-SkipRelease` / `--skip-release` (skip `assembleRelease`), `-SkipLint` / `--skip-lint`, `-SmokeTest` / `--smoke-test` (instrumentation tests; start an AVD first), `-DebugOnly` / `--debug-only` (debug unit tests + `lintDebug` only). Reports land under `app/build/reports/`.

### Dev APK

- Build only: `.\scripts\build-dev-apk.ps1`
- Build and publish dev-latest: `.\scripts\publish-dev-latest.ps1`
- Auto-publish on every push from this machine (opt-in, e.g. while GitLab is down): `git config jotty.autoPublishDev true`. The pre-push hook then publishes in the background after `origin/dev` updates (log: `.git/jotty-dev-publish.log`). Skip once with `git push --no-verify` or `JOTTY_SKIP_DEV_PUBLISH=1`; `push-dev.ps1 -SkipPublish` / `push-dev.sh --skip-publish` does the same.

### Stable release APK

- Full publish without GitLab: `.\scripts\publish-release.ps1 -LocalBuild` (creates the GitHub release and uploads the APK from this machine).
- Build only: `.\scripts\build-release-apk.ps1`, then `gh release upload vX.Y.Z jotty-android-X.Y.Z.apk --clobber`.
