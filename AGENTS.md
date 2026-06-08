# Agent guide — Jotty Android

This file helps AI agents and contributors work effectively on the project.

## Project summary

- **What it is:** Android client for [Jotty](https://jotty.page/) (self-hosted checklists and notes).
- **Stack:** Kotlin, Jetpack Compose, Retrofit, Room, DataStore, Navigation Compose.
- **API:** Jotty REST API; auth via `x-api-key` header. See [Jotty API](https://github.com/fccview/jotty/blob/main/howto/API.md).

## Codebase layout

```
app/src/main/java/com/jotty/android/
├── data/
│   ├── api/          # JottyApi, models, ApiClient (Retrofit; logging only in debug)
│   ├── encryption/   # NoteEncryption (parse frontmatter), XChaCha20Decryptor, XChaCha20Encryptor,
│   │                 # NoteDecryptionSession (thread-safe in-memory decrypted content per note for session)
│   └── preferences/  # SettingsRepository, JottyInstance (multi-instance, default instance, theme, start tab, colorHex)
├── ui/
│   ├── checklists/  # ChecklistsScreen (lists, progress, swipe-to-delete, items, to-do/completed, edit/delete tasks)
│   ├── common/      # Shared composables: LoadingState, ErrorState, EmptyState, ListScreenContent, SwipeToDeleteContainer
│   ├── main/        # MainScreen (NavHost, bottom nav, deep-link note id)
│   ├── notes/       # NotesScreen (list, search, categories, export/share, encrypt, decrypt, swipe-to-delete, deep link)
│   ├── settings/    # SettingsScreen (health check, manage instances, theme, export debug logs, dashboard, About)
│   ├── setup/       # SetupScreen (instance list, default star, instance color, add/edit/delete, connect)
│   └── theme/       # Theme, Type
├── util/            # AppLog (tagged logging), ApiErrorHelper (exception → user message via string resources)
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

## Version and releases

- **Single source of truth:** `gradle.properties` — `VERSION_NAME` and `VERSION_CODE`. The app reads these; `BuildConfig` is generated from them (`buildConfig = true` in `app/build.gradle.kts`).
- **Changelog (required for code changes):** When you add, change, or fix user-visible behavior, **always update `CHANGELOG.md`** in the same work session. Add bullets under the top **`[dev-latest]`** section, using **Added** / **Changed** / **Fixed** / **Documentation** as in [Keep a Changelog](https://keepachangelog.com/). Write concise, user-facing lines (not commit diffs). Skip only trivial refactors with no behavioral impact. Stable releases promote that section via `release.ps1` / `release.sh`.
- **Releasing:** `release.ps1` / `release.sh` bumps `gradle.properties` and promotes `CHANGELOG.md` `[dev-latest]` to a dated stable section, then resets an empty `[dev-latest]` header; commit on `dev`, then `scripts/publish-release.ps1` (or `.sh`) pushes `dev`, merges `dev`→`main` via PR, and `gh release create vX.Y.Z` (triggers APK workflow). `sync-dev-with-main.yml` fast-forwards `dev` after `main` updates. Dev APKs use `VERSION_NAME` + `-dev+<short-sha>` (`DEV_BUILD_SHA` in CI).
- **GitHub release APK:** The `release-apk` workflow attaches **`jotty-android-{VERSION_NAME}.apk`** when signing secrets are set (`ANDROID_KEYSTORE_*` in `keystore.properties.example`); otherwise **`jotty-android-{VERSION_NAME}-debug.apk`**. Same release keystore is required for in-place updates (issue #9). Local release builds use `keystore.properties`.

## Build and run

- **Requirements:** JDK 17+, Android SDK 36. Min SDK 26.
- **Commands:** `./gradlew assembleDebug` or `./gradlew assembleRelease`. On Windows use `gradlew.bat` or the `build.ps1` script (handles wrapper and Java check).
- **BuildConfig:** Must be enabled in `app/build.gradle.kts` (`buildFeatures { buildConfig = true }`) for `BuildConfig.VERSION_NAME` / `VERSION_CODE` (e.g. About screen).
- **Tests:** `./gradlew test` runs JVM unit tests in `app/src/test/`.

## Feature notes

- **Checklists:** Task projects use type `"task"` and `apiPath` for hierarchy. Progress "X / Y done" on list cards and Kanban cards (nested items). Checkbox = complete/uncomplete; tap task text = inline edit (PATCH); drag handle and up/down reorder among siblings when the server exposes item ids (drag handle optional via Settings → Behavior); Kanban board supports inline title edit, in-column drag reorder (Behavior), and dynamic column height. Swipe row left to delete checklist (disabled by default; enable in Settings). Pull-to-refresh (swipe down), empty/error states, **Archived** category filter chip. Detail ⋮ menu: share (server link when REST available, else text export), archive/unarchive (category `Archive`), rename, delete. Offline mode supports local checklist edits with sync on reconnect; sync aborts pull if push/replay fails (local data kept). See [docs/CHECKLIST_REORDER.md](docs/CHECKLIST_REORDER.md).
- **Notes:** List: search, category filter chips (including **Archived**), pull-to-refresh (swipe down), empty/error states. Swipe-to-delete disabled by default; enable in Settings. Note detail: ⋮ menu → copy, server share (when API available), archive/unarchive, delete (confirm). View mode: selectable text (`SelectionContainer`); top-bar export share. **Rich editor (optional):** Settings → Behavior → rich note editor uses bundled WebView WYSIWYG (`WysiwygNoteEditor.kt`); markdown source editor remains default. Plain notes show Markdown in view mode; export/share (title + content). Encrypted notes: lock icon, "Decrypt" for XChaCha20; decrypted content cached in session via `NoteDecryptionSession`; "Encrypt" action and `EncryptNoteDialog` (passphrase, min 12 chars) using `XChaCha20Encryptor` and frontmatter-wrapped body. **Biometric unlock (per note):** after password decrypt, optional "Remember with biometric" stores the passphrase in `BiometricPassphraseStore` (Keystore + strong biometric); reopening can auto-prompt or use fingerprint in the decrypt dialog (`BiometricNoteUnlock.kt`). Settings → Security controls auto-prompt, save offer, and clear all. **Encrypt and decrypt run on a background thread** (`Dispatchers.Default`) so the UI stays responsive; dialogs show a loading state during the operation. If encrypt returns null, the dialog shows an error. Swipe row left to delete note. HTML/table save hint when editing web-origin content (`NoteSaveBehavior.kt`). PGP is not supported in-app.
- **Instances:** Stored in `SettingsRepository`; optional `colorHex` per instance. Default instance: `defaultInstanceId` used when opening app with no current instance; star in Setup and Settings to set default. Add/edit/delete instances from Settings → "Manage instances" without disconnecting; "Disconnect" clears current instance only.
- **Settings:** Health check (api.health()), manage instances (default instance via star), Appearance/Behavior sections (**rich note editor**, checklist/Kanban drag reorder, etc.), **local storage & sync** toggle, **Security** (biometric unlock status, auto-prompt on open, remember-passphrase offer, clear all stored passphrases), dashboard from `api/summary` under Overview, **export debug logs** near About (Troubleshooting; shares `AppLog` ring buffer), About.
- **Deep links:** `jotty-android://open/note/{noteId}` opens the app and the note (MainActivity intent-filter, singleTask; `deepLinkNoteId` state updated in both `onCreate` and `onNewIntent`; JottyAppContent/MainScreen/NotesScreen pass through and clear after open).
- **Technical:** `AppLog` for tagged logging with an in-memory ring buffer (`util/AppLog.kt`, export via `util/DebugLogExporter.kt`); `d` mirrors to logcat in debug builds. HTTP logging only in debug builds. ProGuard keep rules for Gson, Bouncy Castle, and all data model classes. `NoteDecryptionSession` uses `ConcurrentHashMap` for thread safety.

## Encryption (Jotty)

Jotty supports two encryption methods; users choose in the web app under **Profile → Encryption → Encryption Method**.

- **XChaCha20-Poly1305 (default, recommended)** — Passphrase-only, 256-bit keys via Argon2id. **Fully supported in the app:** encrypt and decrypt in-app; format matches the Jotty web app (JSON with base64 salt, nonce, data; AEAD combined `ciphertext` then `tag`). The decryptor still accepts legacy Android notes that used `tag` then `ciphertext` and can warn users to re-encrypt for web compatibility.
- **PGP** — RSA-4096, key pairs; for PGP compatibility. **Not supported in the app:** encrypted PGP notes show a message directing users to decrypt in the Jotty web app.

**Limitations (same as Jotty):** Encrypted note content is not searchable (titles/metadata are). Encrypted notes are only decryptable by the key owner; shared encrypted notes stay encrypted for others. No passphrase recovery — lost passphrase means permanent loss of access; users should keep secure backups.

## Images in notes

- Note body supports Markdown images: `![alt](url)`. Rendered via compose-markdown with Coil. **Tables:** GFM pipe tables render natively; Jotty’s default HTML `<table>` blocks are converted to GFM in `util/MarkdownHtmlTables.kt` before `MarkdownText` (Markwon does not layout HTML tables).
- **Same-host auth:** Image URLs on the same host as the Jotty server get the `x-api-key` header so server-hosted images load. See `util/NoteImageLoader.kt` — `createNoteImageLoader(context, baseUrl, apiKey)`; loaders are cached per (baseUrl, apiKey).
- **Links:** Markdown links are clickable and open in the browser (`onLinkClicked` → `LocalUriHandler.openUri`).
- **Where to change:** `util/NoteImageLoader.kt` for auth/cache; `ui/notes/NoteView.kt` for Markdown/link behaviour; `MainScreen.kt` creates and passes the image loader to `NotesScreen`.

## Where to change what

| Goal                         | Primary location(s)                    |
|-----------------------------|----------------------------------------|
| API endpoints / models      | `data/api/JottyApi.kt`, `models.kt`   |
| Checklist reorder / PATCH   | `util/ChecklistReorder.kt`, `data/local/OfflineChecklistsRepository.kt`, `ui/checklists/ChecklistItemRow.kt` |
| Unified search helper       | `util/JottySearchHelper.kt`           |
| Checklist UI / task edit    | `ui/checklists/ChecklistsScreen.kt`   |
| Notes list                  | `ui/notes/NotesScreen.kt`             |
| Note detail / encrypt/decrypt | `ui/notes/NoteDetailScreen.kt`, `NoteDialogs.kt`, `BiometricNoteUnlock.kt`, `NoteView.kt`, `NoteEditor.kt`, `NoteCard.kt` |
| Biometric passphrase storage | `data/encryption/BiometricPassphraseStore.kt` |
| Note encryption parsing     | `data/encryption/NoteEncryption.kt`   |
| XChaCha20 decrypt/encrypt   | `data/encryption/XChaCha20Decryptor.kt`, `XChaCha20Encryptor.kt` |
| Session decrypted cache     | `data/encryption/NoteDecryptionSession.kt` |
| Instances / settings storage | `data/preferences/SettingsRepository.kt` |
| Setup / instance CRUD       | `ui/setup/SetupScreen.kt`             |
| Deep link handling         | `MainActivity.kt`, `ui/JottyApp.kt`, `MainScreen.kt`, `NotesScreen.kt` |
| Shared list composables     | `ui/common/ListScreenComponents.kt`   |
| API/network error messages | `util/ApiErrorHelper.kt`              |
| Logging / debug log export | `util/AppLog.kt`, `util/DebugLogExporter.kt`; Settings → Troubleshooting, `SettingsScreen.kt` |
| ProGuard keep rules        | `app/proguard-rules.pro`              |
| App version in UI          | `gradle.properties` + BuildConfig      |
| Strings / i18n             | `res/values/strings.xml`               |
| Release history             | `CHANGELOG.md`                         |
| Project documentation       | `docs/` — see [docs/README.md](docs/README.md) |

## Tips for agents

- **Update `CHANGELOG.md`** whenever you ship meaningful app changes (see **Changelog** under Version and releases); do not leave this for the user to ask.
- Prefer suggesting better approaches instead of only following instructions literally.
- Keep code DRY: extract reusable composables into `ui/common/` and helpers, reuse API and encryption logic.
- When adding features that touch the API, check `JottyApi.kt` and `models.kt` for existing patterns and types.
- For encrypted notes, follow the existing frontmatter/body format (see `howto/ENCRYPTION.md` in the Jotty repo) so the app stays compatible with the Jotty web app.
- Use string resources (`R.string.…`) for all user-visible text; avoid hardcoded strings in composables.
- Avoid `!!`; prefer safe-call patterns or local `val` bindings that smart-cast to non-null.
