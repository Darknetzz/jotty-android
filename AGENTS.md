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
│   ├── api/          # JottyApi, models, ApiClient (Retrofit)
│   ├── encryption/   # NoteEncryption (parse frontmatter), XChaCha20Decryptor (Jotty encrypted notes)
│   └── preferences/  # SettingsRepository, JottyInstance (multi-instance, theme, start tab)
├── ui/
│   ├── checklists/  # ChecklistsScreen (lists, items, to-do/completed, edit/delete tasks)
│   ├── main/        # MainScreen (NavHost, bottom nav)
│   ├── notes/       # NotesScreen (list, detail, Markdown view/edit, encrypted note decrypt)
│   ├── settings/    # SettingsScreen (theme, start screen, dashboard, About)
│   ├── setup/       # SetupScreen (instance list, add/edit/delete, connect)
│   └── theme/       # Theme, Type
├── JottyApp.kt      # Root composable, migration, nav to Setup vs Main
└── MainActivity.kt  # Activity, theme from SettingsRepository, Compose setContent
```

## Conventions

- **Kotlin:** Official code style (`gradle.properties`). Prefer `val`, immutable data where possible.
- **Compose:** Material 3; use `@OptIn(ExperimentalMaterial3Api::class)` when needed (e.g. TopAppBar).
- **API:** All Jotty calls go through `JottyApi`; create/update/delete use request/response types in `data/api/models.kt`.
- **State:** `remember` / `mutableStateOf` for local UI state; `SettingsRepository` flows for persisted settings and instances.
- **DRY:** Reuse composables and helpers; avoid duplicating API or encryption logic.

## Version and releases

- **Single source of truth:** `gradle.properties` — `VERSION_NAME` and `VERSION_CODE`. The app reads these; `BuildConfig` is generated from them (`buildConfig = true` in `app/build.gradle.kts`).
- **Releasing:** Bump `VERSION_NAME` and `VERSION_CODE` in `gradle.properties`, add a section to `CHANGELOG.md` (Keep a Changelog style), then build and tag (e.g. `v1.0.2`).

## Build and run

- **Requirements:** JDK 17+, Android SDK 35. Min SDK 26.
- **Commands:** `./gradlew assembleDebug` or `./gradlew assembleRelease`. On Windows use `gradlew.bat` or the `build.ps1` script (handles wrapper and Java check).
- **BuildConfig:** Must be enabled in `app/build.gradle.kts` (`buildFeatures { buildConfig = true }`) for `BuildConfig.VERSION_NAME` / `VERSION_CODE` (e.g. About screen).

## Feature notes

- **Checklists:** Task projects use type `"task"` and `apiPath` (e.g. `"0"`, `"0.0"`) for hierarchy. Checkbox = complete/uncomplete; tap task text = inline edit; delete button per row.
- **Notes:** Plain notes show Markdown in view mode. Encrypted notes (Jotty frontmatter with `encrypted: true`) show a lock and “Decrypt” for XChaCha20; PGP is not supported in-app.
- **Instances:** Stored and managed in `SettingsRepository`; “Disconnect” only clears the current instance; add/edit/delete in SetupScreen.
- **Settings:** Theme (system/light/dark), start screen tab, dashboard from `api/summary`, About (version + GitHub link).

## Where to change what

| Goal                         | Primary location(s)                    |
|-----------------------------|----------------------------------------|
| API endpoints / models      | `data/api/JottyApi.kt`, `models.kt`   |
| Checklist UI / task edit    | `ui/checklists/ChecklistsScreen.kt`   |
| Notes list / detail / encrypt | `ui/notes/NotesScreen.kt`           |
| Note encryption parsing     | `data/encryption/NoteEncryption.kt`   |
| XChaCha20 decrypt           | `data/encryption/XChaCha20Decryptor.kt`|
| Instances / settings storage | `data/preferences/SettingsRepository.kt` |
| Setup / instance CRUD       | `ui/setup/SetupScreen.kt`             |
| App version in UI          | `gradle.properties` + BuildConfig      |
| Release history             | `CHANGELOG.md`                         |

## Tips for agents

- Prefer suggesting better approaches instead of only following instructions literally.
- Keep code DRY: extract reusable composables and helpers, reuse API and encryption logic.
- When adding features that touch the API, check `JottyApi.kt` and `models.kt` for existing patterns and types.
- For encrypted notes, follow the existing frontmatter/body format (see `howto/ENCRYPTION.md` in the Jotty repo) so the app stays compatible with the Jotty web app.
