# Changelog

All notable changes to Jotty Android are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [1.1.1] - 2026-02-07

### Added

- **Unit tests** — Tests for `NoteEncryption` (frontmatter parsing, body-only detection, BOM handling), `NoteDecryptionSession` (CRUD, clear), and `XChaCha20Encryptor` (frontmatter round-trip).
- **Shared UI components** — Reusable `LoadingState`, `ErrorState`, `EmptyState`, and `SwipeToDeleteContainer` composables in `ui/common/` (DRY refactor).
- **String resources** — All user-visible strings moved to `res/values/strings.xml` for i18n readiness.

### Changed

- **Security: release logging** — HTTP body logging (`HttpLoggingInterceptor`) now only runs in debug builds; release builds no longer log API keys or response bodies.
- **Null safety** — Removed all `!!` (non-null assertions) across `MainScreen`, `ChecklistsScreen`, and `NotesScreen`; replaced with safe-call patterns and local `val` bindings.
- **Deep link handling** — `deepLinkNoteId` is now an Activity-level property updated in both `onCreate` and `onNewIntent`, so deep links work correctly after process death or re-delivery.
- **Composable rename** — Root UI composable renamed from `JottyApp` to `JottyAppContent` to distinguish from the `Application` class.
- **Thread safety** — `NoteDecryptionSession` now uses `ConcurrentHashMap` instead of plain `MutableMap`.

### Technical

- **ProGuard** — Added keep rules for all Jotty API models (`data.api.*`), encryption models (`data.encryption.*`), and `JottyInstance` to prevent Gson serialization issues in minified builds.
- **AGENTS.md** — Updated with new conventions (null safety, string resources, shared composables), test instructions, and clarified dual `JottyApp` files.

---

## [1.1.0] - 2026-02-06

### Added

- **Notes: search & categories** — Search field and category filter chips (from server) on the notes list.
- **Notes: export / share** — Share action in note detail sends title and content (e.g. to other apps).
- **Notes: encrypt in-app** — Encrypt an unencrypted note with a passphrase (min 12 chars); uses XChaCha20 and frontmatter-wrapped body compatible with Jotty.
- **Notes: session decryption cache** — Decrypted content is reused in-session when reopening the same note (no re-entry of passphrase).
- **Notes: swipe-to-delete** — Swipe a note row left to delete (with server sync).
- **Checklists: progress** — “X / Y done” line on checklist detail above the task list.
- **Checklists: swipe-to-delete** — Swipe a checklist row left to delete (with server sync).
- **Checklists & notes: UX** — Pull-to-refresh, empty-state hints (“Tap + to add…”), error state with Retry.
- **Settings: health check** — Connection card shows “Connected” or “Server unreachable” via `api.health()`.
- **Settings: default instance** — “Set as default instance” row (star icon); app opens with this instance when no current session.
- **Setup: default instance** — Star on each instance card (filled when default); tap star to set as default without connecting.
- **Setup: instance color** — Optional color per instance (color dot on card, color picker in add/edit form).
- **Deep links** — `jotty-android://open/note/{noteId}` opens the app and the note.
- **Logging** — Tagged logging via `AppLog` (e.g. notes, checklists).
- **README** — “Releases / Download” section with link to GitHub Releases and APK install note.
- **AGENTS.md** — Updated with encryption encrypt/session cache, search/categories/export, health/default/color, deep links, swipe, logging, ProGuard.

### Changed

- **Current vs default instance** — `currentInstanceId` no longer falls back to default; only `JottyApp` sets current from default when opening with no current instance.
- **Remove instance** — Removing an instance also clears it as default when applicable.

### Technical

- **ProGuard** — Keep rules added for Gson and Bouncy Castle (encryption).

---

## [1.0.2-1] - 2026-02-06 (hotfix)

### Fixed

- **Encryption detection** — Encrypted notes are now detected reliably: support for API `encrypted` flag, relaxed frontmatter parsing (BOM, quoted values, YAML variants), and body-only detection when the server returns just the XChaCha20 JSON payload without frontmatter.

---

## [1.0.2] - 2026-02-06

### Added

- **Encrypted notes** — Notes encrypted with Jotty (XChaCha20-Poly1305) are detected and show a lock in the list. Open an encrypted note to decrypt with your passphrase; decrypted content is shown in-session only. PGP-encrypted notes are detected but decryption is not supported in-app (use the Jotty web app).
- **AGENTS.md** — Guide for AI agents and contributors (codebase layout, conventions, versioning, build).

---

## [1.0.1] - 2025-02-06

### Added

- **Multiple instances** — Save and switch between several Jotty servers. Add, edit (name, URL, API key), and remove instances without losing others.
- **Task projects** — Hierarchical checklists with sub-tasks; add sub-tasks from the row action.
- **To Do / Completed** — Checklist tasks grouped like the Jotty web app (unchecked first, then completed).
- **Edit tasks** — Tap task text to edit inline; checkbox toggles complete/uncomplete only.
- **Delete tasks** — Delete button on each checklist row.
- **Notes view mode** — Rendered Markdown when viewing a note.
- **Notes edit mode** — Improved editor and clear view/edit toggle.
- **Settings: Theme** — System default, light, or dark.
- **Settings: Start screen** — Open directly to Checklists, Notes, or Settings.
- **Settings: Dashboard overview** — Summary from Jotty server (user, counts, completion); admin overview for server admins.
- **Settings: About** — App version and link to GitHub repository.
- **Build** — `build.ps1` script (Gradle wrapper, Java 11+ check, assembleDebug/Release).

### Changed

- **Disconnect** — Only clears current session; saved instances remain so you can switch back.
- Existing server URL and API key are migrated into multi-instance storage as one saved instance (no action required).

---

## [1.0.0] - Initial release

- Checklists: create, view, add items, check/uncheck.
- Notes: create and edit with Markdown support.
- Connect to a self-hosted Jotty instance (server URL + API key).
- Jetpack Compose UI, Retrofit API client, DataStore preferences, Navigation Compose.

[1.1.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.1
[1.1.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.0
[1.0.2-1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.2-1
[1.0.2]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.2
[1.0.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.1
[1.0.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.0
