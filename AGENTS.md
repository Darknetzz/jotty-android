# Agent guide — Jotty Android

This file helps AI agents and contributors work effectively on the project.

## Project summary

- **What it is:** Android client for [Jotty](https://jotty.page/) (self-hosted checklists and notes).
- **Stack:** Kotlin, Jetpack Compose, Retrofit, DataStore, Navigation Compose.
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
│   ├── settings/    # SettingsScreen (health check, default instance, theme, start screen, dashboard, About)
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
- **Releasing:** Bump `VERSION_NAME` and `VERSION_CODE` in `gradle.properties`, add a section to `CHANGELOG.md` (Keep a Changelog style), then build and tag (e.g. `v1.0.2`).

## Build and run

- **Requirements:** JDK 17+, Android SDK 35. Min SDK 26.
- **Commands:** `./gradlew assembleDebug` or `./gradlew assembleRelease`. On Windows use `gradlew.bat` or the `build.ps1` script (handles wrapper and Java check).
- **BuildConfig:** Must be enabled in `app/build.gradle.kts` (`buildFeatures { buildConfig = true }`) for `BuildConfig.VERSION_NAME` / `VERSION_CODE` (e.g. About screen).
- **Tests:** `./gradlew test` runs JVM unit tests in `app/src/test/`.

## Feature notes

- **Checklists:** Task projects use type `"task"` and `apiPath` for hierarchy. Progress "X / Y done" on detail. Checkbox = complete/uncomplete; tap task text = inline edit; delete button per row. Swipe row left to delete checklist (disabled by default; enable in Settings). Pull-to-refresh (swipe down), empty/error states.
- **Notes:** List: search, category filter chips, pull-to-refresh (swipe down), empty/error states. Swipe-to-delete disabled by default; enable in Settings. Plain notes show Markdown in view mode; export/share (title + content). Encrypted notes: lock icon, "Decrypt" for XChaCha20; decrypted content cached in session via `NoteDecryptionSession`; "Encrypt" action and `EncryptNoteDialog` (passphrase, min 12 chars) using `XChaCha20Encryptor` and frontmatter-wrapped body. **Encrypt and decrypt run on a background thread** (`Dispatchers.Default`) so the UI stays responsive; dialogs show a loading state during the operation. If encrypt returns null, the dialog shows an error. Swipe row left to delete note. PGP is not supported in-app.
- **Instances:** Stored in `SettingsRepository`; optional `colorHex` per instance. Default instance: `defaultInstanceId` used when opening app with no current instance; star in Setup and Settings to set default. Add/edit/delete instances from Settings → "Manage instances" without disconnecting; "Disconnect" clears current instance only.
- **Settings:** Health check (api.health()), "Set as default instance" row, theme, start screen tab, swipe to delete, content padding, **debug logging** (General; when enabled, `AppLog.d()` writes to logcat, e.g. decryption failure step), dashboard from `api/summary`, About.
- **Deep links:** `jotty-android://open/note/{noteId}` opens the app and the note (MainActivity intent-filter, singleTask; `deepLinkNoteId` state updated in both `onCreate` and `onNewIntent`; JottyAppContent/MainScreen/NotesScreen pass through and clear after open).
- **Technical:** `AppLog` for tagged logging; when Settings → Debug logging is enabled, `AppLog.d()` writes to logcat (e.g. decryption parse/key/auth failure). HTTP logging only in debug builds. ProGuard keep rules for Gson, Bouncy Castle, and all data model classes. `NoteDecryptionSession` uses `ConcurrentHashMap` for thread safety.

## Encryption (Jotty)

Jotty supports two encryption methods; users choose in the web app under **Profile → Encryption → Encryption Method**.

- **XChaCha20-Poly1305 (default, recommended)** — Passphrase-only, 256-bit keys via Argon2id. **Fully supported in the app:** encrypt and decrypt in-app; format matches the Jotty web app (JSON with base64 salt, nonce, data; libsodium-style tag-then-ciphertext).
- **PGP** — RSA-4096, key pairs; for PGP compatibility. **Not supported in the app:** encrypted PGP notes show a message directing users to decrypt in the Jotty web app.

**Limitations (same as Jotty):** Encrypted note content is not searchable (titles/metadata are). Encrypted notes are only decryptable by the key owner; shared encrypted notes stay encrypted for others. No passphrase recovery — lost passphrase means permanent loss of access; users should keep secure backups.

## Images in notes

- Note body supports Markdown images: `![alt](url)`. Rendered via compose-markdown with Coil.
- **Same-host auth:** Image URLs on the same host as the Jotty server get the `x-api-key` header so server-hosted images load. See `util/NoteImageLoader.kt` — `createNoteImageLoader(context, baseUrl, apiKey)`; loaders are cached per (baseUrl, apiKey).
- **Links:** Markdown links are clickable and open in the browser (`onLinkClicked` → `LocalUriHandler.openUri`).
- **Where to change:** `util/NoteImageLoader.kt` for auth/cache; `ui/notes/NoteView.kt` for Markdown/link behaviour; `MainScreen.kt` creates and passes the image loader to `NotesScreen`.

## Where to change what

| Goal                         | Primary location(s)                    |
|-----------------------------|----------------------------------------|
| API endpoints / models      | `data/api/JottyApi.kt`, `models.kt`   |
| Checklist UI / task edit    | `ui/checklists/ChecklistsScreen.kt`   |
| Notes list                  | `ui/notes/NotesScreen.kt`             |
| Note detail / encrypt/decrypt | `ui/notes/NoteDetailScreen.kt`, `NoteDialogs.kt`, `NoteView.kt`, `NoteEditor.kt`, `NoteCard.kt` |
| Note encryption parsing     | `data/encryption/NoteEncryption.kt`   |
| XChaCha20 decrypt/encrypt   | `data/encryption/XChaCha20Decryptor.kt`, `XChaCha20Encryptor.kt` |
| Session decrypted cache     | `data/encryption/NoteDecryptionSession.kt` |
| Instances / settings storage | `data/preferences/SettingsRepository.kt` |
| Setup / instance CRUD       | `ui/setup/SetupScreen.kt`             |
| Deep link handling         | `MainActivity.kt`, `ui/JottyApp.kt`, `MainScreen.kt`, `NotesScreen.kt` |
| Shared list composables     | `ui/common/ListScreenComponents.kt`   |
| API/network error messages | `util/ApiErrorHelper.kt`              |
| Logging / debug logging    | `util/AppLog.kt` (flag); Settings → `SettingsRepository.debugLoggingEnabled`, `SettingsScreen.kt` |
| ProGuard keep rules        | `app/proguard-rules.pro`              |
| App version in UI          | `gradle.properties` + BuildConfig      |
| Strings / i18n             | `res/values/strings.xml`               |
| Release history             | `CHANGELOG.md`                         |

## Tips for agents

- Prefer suggesting better approaches instead of only following instructions literally.
- Keep code DRY: extract reusable composables into `ui/common/` and helpers, reuse API and encryption logic.
- When adding features that touch the API, check `JottyApi.kt` and `models.kt` for existing patterns and types.
- For encrypted notes, follow the existing frontmatter/body format (see `howto/ENCRYPTION.md` in the Jotty repo) so the app stays compatible with the Jotty web app.
- Use string resources (`R.string.…`) for all user-visible text; avoid hardcoded strings in composables.
- Avoid `!!`; prefer safe-call patterns or local `val` bindings that smart-cast to non-null.
