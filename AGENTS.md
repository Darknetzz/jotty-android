# Agent guide — Jotty Android

This file helps AI agents and contributors work effectively on the project.

## Project summary

- **What it is:** Android client for [Jotty](https://jotty.page/) (self-hosted checklists and notes).
- **Stack:** Kotlin, Jetpack Compose, Retrofit, Room, DataStore, Navigation Compose.
- **API:** Jotty REST API; auth via `x-api-key` header. See [Jotty API](https://github.com/fccview/jotty/blob/main/howto/API.md).
- **Minimum server:** Jotty **1.25.0** (`JottyServerVersion.MIN_SUPPORTED`). Setup refuses older servers based on `GET /api/health` `version`; a missing/unparsable version is allowed. Don't add fallbacks for pre-1.25 servers (e.g. checklist item PATCH is always available).

## Codebase layout

```
app/src/main/java/com/jotty/android/
├── data/
│   ├── api/          # JottyApi, models, ApiClient (Retrofit; logging only in debug)
│   ├── encryption/   # NoteEncryption (parse frontmatter), XChaCha20Decryptor, XChaCha20Encryptor,
│   │                 # NoteDecryptionSession (thread-safe in-memory decrypted content per note for session)
│   ├── local/        # Offline Room DB, sync workers, sync backups, pending ops
│   ├── preferences/  # SettingsRepository, JottyInstance (multi-instance, default, theme, colorHex, customHeaders)
│   ├── repository/   # NotesListDataSource / ChecklistsListDataSource (online API or Room behind one list UI)
│   └── updates/      # In-app update check (GitHub releases API), bundled changelog
├── ui/
│   ├── checklists/  # ChecklistsScreen (lists, progress, swipe-to-delete, items, to-do/completed, edit/delete tasks)
│   ├── common/      # Shared composables: LoadingState, ErrorState, EmptyState, ListScreenContent, SwipeToDeleteContainer
│   ├── main/        # MainScreen (NavHost, bottom nav, deep-link note id)
│   ├── notes/       # NotesScreen (list, search, categories, export/share, encrypt, decrypt, swipe-to-delete, deep link)
│   ├── categories/  # ManageCategoriesScreen (browse note/checklist categories from GET api/categories)
│   ├── settings/    # SettingsScreen (health check, manage instances, manage categories, theme, export debug logs, dashboard, About)
│   ├── setup/       # SetupScreen (instance list, default star, instance color, custom headers, add/edit/delete, connect, test connection)
│   ├── sync/        # Pending sync manager (dirty items, diffs, force push/pull, on-device sync backups)
│   └── theme/       # Theme, Type
├── util/            # AppLog, ApiErrorHelper, CustomHttpHeaders, JottyCloneHelper, …
├── widget/          # QuickAddNoteWidget (Glance home-screen widget)
├── JottyApp.kt      # Application class (SettingsRepository singleton)
├── ui/JottyApp.kt   # JottyAppContent composable — root UI (migration, default instance, setup↔main nav)
└── MainActivity.kt  # Activity, theme, deep link intent (jotty-android://open/note/{id})
```

## Conventions

- **Kotlin:** Official code style (`gradle.properties`). Prefer `val`, immutable data where possible.
- **Compose:** Material 3; use `@OptIn(ExperimentalMaterial3Api::class)` when needed (e.g. TopAppBar).
- **API:** All Jotty calls go through `JottyApi`; create/update/delete use request/response types in `data/api/models.kt`.
- **State:** `remember` / `mutableStateOf` for local UI state; `SettingsRepository` flows for persisted settings and instances.
- **DRY:** Reuse composables and helpers (see `ui/common/`); avoid duplicating API or encryption logic.
- **Null safety:** Prefer `?.let { }`, local `val` bindings, or `requireNotNull` over `!!`.
- **Strings:** User-visible text in `res/values/strings.xml`; use `stringResource(R.string.…)` in composables.
- **Line endings:** LF everywhere (`.gitattributes`; only `*.bat` is CRLF). A diff that rewrites a whole file is a line-ending flip: fix it before committing.
- **Generated files:** `app/src/main/assets/CHANGELOG.md` is copied from the root `CHANGELOG.md` at build time (`copyChangelogToAssets`) and is gitignored; never commit it.

## Version and releases

- **Single source of truth:** `gradle.properties` — `VERSION_NAME` and `VERSION_CODE`. The app reads these; `BuildConfig` is generated from them (`buildConfig = true` in `app/build.gradle.kts`).
- **Changelog (required for code changes):** When you add, change, or fix user-visible behavior, **always update `CHANGELOG.md`** in the same work session. Add bullets under the top **`[dev-latest]`** section, using **Added** / **Changed** / **Fixed** / **Documentation** as in [Keep a Changelog](https://keepachangelog.com/). Write concise, user-facing lines (not commit diffs). Skip only trivial refactors with no behavioral impact. Stable releases promote that section via `release.ps1` / `release.sh`.
- **CI/CD:** **GitLab CI** (`.gitlab-ci.yml` on `gitlab.roste.org`) runs tests/lint on every push and publishes to **GitHub** with `gh` (`GH_TOKEN` + release keystore as protected CI variables). `origin` is GitLab `kriss/jotty-android` (the only remote); GitHub `Darknetzz/jotty-android` gets every branch and tag from a GitLab push mirror (mirror id 14, HTTPS + fine-grained PAT, keep divergent refs on, so a GitHub-only commit such as a PR merge is never overwritten, but it blocks that branch until GitLab has it). Never push to GitHub directly; jobs and scripts that need a ref on GitHub wait for the mirror (`scripts/ci/wait-for-github-ref.sh`, up to 10 min). GitHub Actions workflows are **manual-only** fallbacks. See [docs/LOCAL_CI.md](docs/LOCAL_CI.md).
- **Stable releasing:** `release.ps1` / `release.sh` bumps `gradle.properties` and promotes `CHANGELOG.md` `[dev-latest]` to a dated stable section; commit on `dev`, then `scripts/publish-release.ps1` (or `.sh`) pushes, merges `dev` → `main` on GitHub, pushes tag `vX.Y.Z`, and syncs `dev`. The GitLab `github-release` job (`scripts/ci/publish-github-release.sh`) builds the APK and creates the GitHub release (notes via `scripts/lib/release-notes.sh`). `-LocalBuild` / `--local-build` publishes from this machine instead.
- **Dev builds (rolling [dev-latest](https://github.com/Darknetzz/jotty-android/releases/tag/dev-latest)):** **`git push origin dev`** → GitLab `dev-latest` job waits for GitHub `dev` to match (`scripts/ci/wait-for-github-ref.sh`), then runs `scripts/publish-dev-latest.sh` (build via `build-dev-apk`, recreate the GitHub pre-release with the APK via `gh`, then force-move the `dev-latest` tag on GitLab to the same commit with the job token). `dev-latest` must always point at the latest `dev` commit on both hosts. One-time per clone: `scripts/setup-repo-git.ps1` / `.sh` (`core.hooksPath` → `.githooks`, force-fetch for the moving `dev-latest` tag). Local fallback: `publish-dev-latest`, or opt-in pre-push auto-publish with `git config jotty.autoPublishDev true` (log `.git/jotty-dev-publish.log`). Dev APK `versionName` suffix `-dev+<7-char-sha>`; `versionCode` = `VERSION_CODE * 10000 + git rev-list count % 10000` (see `build-dev-apk` and `-PDEV_BUILD_SHA` in `app/build.gradle.kts`; CI clones with `GIT_DEPTH: 0`).
- **GitHub release APK:** **`jotty-android-{VERSION_NAME}.apk`**, release-signed. CI publish jobs fail without the keystore variables; local builds fall back to **`*-debug.apk`** without `keystore.properties`. Same release keystore is required for in-place updates (issue #9).

## Build and run

- **Requirements:** JDK 17+, Android SDK 36. Min SDK 26.
- **Commands:** `./gradlew assembleDebug` or `./gradlew assembleRelease`. On Windows use `gradlew.bat` or the `build.ps1` script (handles wrapper and Java check).
- **BuildConfig:** Must be enabled in `app/build.gradle.kts` (`buildFeatures { buildConfig = true }`) for `BuildConfig.VERSION_NAME` / `VERSION_CODE` (e.g. About screen).
- **Verify before pushing:** `scripts/ci-local.ps1` / `scripts/ci-local.sh` runs what CI runs (unit tests, Android lint, ktlint). Quick loop: `./gradlew testDebugUnitTest lintDebug ktlintCheck`. Unit tests live in `app/src/test/` (JVM, Robolectric where Android classes are needed).

## Feature notes

Full behaviour and code pointers per feature: [docs/FEATURES.md](docs/FEATURES.md). Offline storage, the sync queue and conflicts: [docs/OFFLINE_SYNC.md](docs/OFFLINE_SYNC.md). Update those pages when you change a feature.

Rules that are easy to break:

- **Offline first:** with local storage on, every write goes through an `Offline*Repository`. When the server gives no answer (`Throwable.isServerUnreachable()`, an `IOException`, not an `HttpException`), keep the change locally and call `reportServerUnreachable`; never fail the save. "Online" means the server answered, not just that there is a network.
- **Checklist item ops are positional** (`PendingItemOp.path`). Keep the queue in order and never deduplicate it (#120). Anything that sends several ops (bulk actions, reorder) must stop a second run from starting on stale positions.
- **No bulk REST endpoints:** bulk checklist actions are per-item ops built in `util/ChecklistBulkActions.kt`; clears delete top-level items highest index first.
- **Encrypted notes:** never write plaintext to Room, drafts (`NoteDraftStore`) or logs; the session cache is `NoteDecryptionSession`.
- **Same-host media** (note images, WYSIWYG) must send `x-api-key` and the instance's custom headers (`util/NoteImageLoader.kt`); custom headers are redacted from debug logs.
- **Server features newer than 1.25** (shares, templates, sort items, Kanban item fields: Jotty 1.29+) must degrade on older servers: probe first (`util/JottySharingProbe.kt`, `util/KanbanItemFieldsProbe.kt`) or treat 404/405 as "not supported" with a message (templates, sort items). Never add fallbacks for servers older than 1.25.

## Encryption (Jotty)

Jotty supports two encryption methods; users choose in the web app under **Profile → Encryption → Encryption Method**.

- **XChaCha20-Poly1305 (default, recommended)** — Passphrase-only, 256-bit keys via Argon2id. **Fully supported in the app:** encrypt and decrypt in-app; format matches the Jotty web app (JSON with base64 salt, nonce, data; AEAD combined `ciphertext` then `tag`). The decryptor still accepts legacy Android notes that used `tag` then `ciphertext` and can warn users to re-encrypt for web compatibility.
- **PGP** — RSA-4096, key pairs; for PGP compatibility. **Not supported in the app:** encrypted PGP notes show a message directing users to decrypt in the Jotty web app.

**Limitations (same as Jotty):** Encrypted note content is not searchable (titles/metadata are). Encrypted notes are only decryptable by the key owner; shared encrypted notes stay encrypted for others. No passphrase recovery — lost passphrase means permanent loss of access; users should keep secure backups.

## Where to change what

| Goal                         | Primary location(s)                    |
|-----------------------------|----------------------------------------|
| API endpoints / models      | `data/api/JottyApi.kt`, `models.kt`   |
| Checklist reorder / PATCH   | `util/ChecklistReorder.kt`, `data/local/OfflineChecklistsRepository.kt`, `ui/checklists/ChecklistItemRow.kt` |
| Unified search helper       | `util/JottySearchHelper.kt`           |
| Checklist UI / task edit    | `ui/checklists/ChecklistsScreen.kt`   |
| Notes list                  | `ui/notes/NotesScreen.kt`             |
| Note detail / encrypt/decrypt | `ui/notes/NoteDetailScreen.kt`, `NoteDialogs.kt`, `BiometricNoteUnlock.kt`, `NoteView.kt`, `NoteEditor.kt`, `NoteCard.kt` |
| Note draft autosave         | `data/local/NoteDraftStore.kt`, `ui/notes/NoteDetailViewModel.kt`, `ui/notes/RestoreOpenNoteEffect.kt` |
| Biometric passphrase storage | `data/encryption/BiometricPassphraseStore.kt` |
| Note encryption parsing     | `data/encryption/NoteEncryption.kt`   |
| XChaCha20 decrypt/encrypt   | `data/encryption/XChaCha20Decryptor.kt`, `XChaCha20Encryptor.kt` |
| Session decrypted cache     | `data/encryption/NoteDecryptionSession.kt` |
| Instances / settings storage | `data/preferences/SettingsRepository.kt` |
| Custom HTTP headers         | `util/CustomHttpHeaders.kt`, `SetupScreen.kt`, `ApiClient.kt`, `NoteImageLoader.kt` |
| Clone notes / checklists    | `util/JottyCloneHelper.kt`, list/detail ⋮ menus, `CloneComponents.kt` |
| Change / manage categories  | `ui/common/CategoryComponents.kt`, `ui/categories/ManageCategoriesScreen.kt`, list/detail menus, Settings |
| Pending sync manager        | `ui/sync/PendingSyncScreen.kt`, `PendingSyncDetailScreen.kt`, `PendingSyncViewModel.kt`, `data/local/Offline*Repository.kt`, `SyncBackup*` |
| Setup / instance CRUD       | `ui/setup/SetupScreen.kt`             |
| Connection test / diagnostics | `util/ConnectionDiagnostics.kt`, `ui/setup/ConnectionTestResultCard.kt` |
| Online / server reachability | `data/local/NetworkConnectivityMonitor.kt`, `OfflineRepositoryRuntime.kt`, `util/NetworkFailure.kt` |
| Server version checks       | `util/JottyServerVersion.kt` (minimum 1.25.0, compat ceiling) |
| Deep link handling         | `MainActivity.kt`, `ui/JottyApp.kt`, `MainScreen.kt`, `NotesScreen.kt` |
| Share target / widget       | `MainActivity.kt` (`ACTION_SEND`, `EXTRA_QUICK_ADD_NOTE`), `widget/QuickAddNoteWidget.kt` |
| In-app update check         | `data/updates/UpdateChecker.kt`, `BundledChangelog.kt`, `util/ApkInstallHelper.kt`, Settings → About |
| Note images / tables        | `util/NoteImageLoader.kt`, `NoteImageUrls.kt`, `MarkdownHtmlTables.kt`, `ui/notes/NoteView.kt` |
| Shared list composables     | `ui/common/ListScreenComponents.kt`   |
| API/network error messages | `util/ApiErrorHelper.kt`              |
| Logging / debug log export | `util/AppLog.kt`, `util/DebugLogExporter.kt`; Settings → Troubleshooting, `SettingsScreen.kt` |
| ProGuard keep rules        | `app/proguard-rules.pro`              |
| App version in UI          | `gradle.properties` + BuildConfig      |
| Strings / i18n             | `res/values/strings.xml`               |
| Release history             | `CHANGELOG.md`                         |
| GitLab CI/CD                | `.gitlab-ci.yml`, `scripts/ci/*.sh`, `scripts/lib/release-notes.sh` — [docs/LOCAL_CI.md](docs/LOCAL_CI.md#gitlab-cicd) |
| Local CI / dev APK publish  | `scripts/ci-local.*`, `scripts/build-dev-apk.*`, `scripts/publish-dev-latest.*`, `scripts/push-dev.*`, `scripts/lib/wait-and-publish-dev.sh`, `.githooks/pre-push`, `scripts/setup-repo-git.*` — [docs/LOCAL_CI.md](docs/LOCAL_CI.md) |
| Stable release publish      | `release.ps1` / `release.sh`, `scripts/publish-release.*`, `scripts/build-release-apk.*` |
| Feature behaviour (docs)    | [docs/FEATURES.md](docs/FEATURES.md), [docs/OFFLINE_SYNC.md](docs/OFFLINE_SYNC.md) |
| Project documentation       | `docs/` — see [docs/README.md](docs/README.md) |

## Tips for agents

- **Update `CHANGELOG.md`** whenever you ship meaningful app changes (see **Changelog** under Version and releases); do not leave this for the user to ask.
- **Keep the docs true:** when behaviour changes, update `docs/FEATURES.md` or `docs/OFFLINE_SYNC.md` in the same commit, and `README.md` if the feature list or setup changes.
- **Publishing** runs in GitLab CI, which calls the same bash scripts as the local fallbacks (`publish-dev-latest.sh`, `build-*-apk.sh`). Keep the `.sh` and `.ps1` variants and `.gitlab-ci.yml` in sync; document new CI variables, skip flags, or config keys in `docs/LOCAL_CI.md` and here.
- Prefer suggesting better approaches instead of only following instructions literally.
- Keep code DRY: extract reusable composables into `ui/common/` and helpers, reuse API and encryption logic.
- When adding features that touch the API, check `JottyApi.kt` and `models.kt` for existing patterns and types.
- For encrypted notes, follow the existing frontmatter/body format (see `howto/ENCRYPTION.md` in the Jotty repo) so the app stays compatible with the Jotty web app.
- Use string resources (`R.string.…`) for all user-visible text; avoid hardcoded strings in composables.
- Avoid `!!`; prefer safe-call patterns or local `val` bindings that smart-cast to non-null.
