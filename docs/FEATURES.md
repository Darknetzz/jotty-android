# Feature reference

How each feature behaves and where it lives in the code. For conventions and the "where to change what" table, see [AGENTS.md](../AGENTS.md). Offline storage and sync have their own page: [OFFLINE_SYNC.md](OFFLINE_SYNC.md).

Paths are relative to `app/src/main/java/com/jotty/android/`.

## Checklists

**Types.** Simple lists, and task projects (type `"task"`) that use `apiPath` for hierarchy and can show a Kanban board.

**List screen**
- Cards show progress "X / Y done", counting nested items.
- Pull to refresh, empty and error states, category filter chips including **Archived**.
- Swipe a card left to delete it (off by default; Settings → Behavior).
- Default category for new checklists: Settings → Behavior.

**Simple checklist detail**
- Nested children are flattened with indentation; progress counts all of them.
- Checkbox completes or reopens an item. Tapping the text edits it inline.
- Renaming (including parents and nested paths) is an in-place `PATCH /api/checklists/{id}/items/{index}` via `updateChecklistItemText`; offline it queues an `UPDATE` op.
- Reorder among siblings with up/down buttons or a drag handle when the server exposes item ids (drag handle optional, Settings → Behavior). See [CHECKLIST_REORDER.md](CHECKLIST_REORDER.md).
- Section header ⋮ bulk actions: **To Do** → Check all / Clear all; **Completed** → Uncheck all / Clear all. Jotty has no bulk endpoint, so `util/ChecklistBulkActions.kt` turns each action into per-item ops (clears delete top-level items, highest index first, so paths stay valid). Offline-capable path: `OfflineChecklistsRepository.applyBulkItemOps`. Clearing asks for confirmation; the menus show a spinner while an action runs, because a second action would use stale positions.

**Kanban (task projects)**
- Inline title edit, drag reorder within a column (Settings → Behavior), column height follows content.
- Column status management and moving tasks between columns. Item fields (priority, dates, metadata) when the server supports them (`util/KanbanItemFieldsProbe.kt`).

**Detail and list ⋮ menus:** share (`/api/shares` on Jotty 1.29+, otherwise a text export), **Sort items** (simple lists, Jotty 1.29+), archive / unarchive (moves to category `Archive`), rename, **Change category**, **Clone** (with category picker, `util/JottyCloneHelper.kt`), delete.

## Notes

**List screen**
- Search, category filter chips including **Archived**, pull to refresh, empty and error states.
- **+**, the home-screen widget and the Android share sheet all create an "Untitled" note in the default category and open it in the editor.
- Overflow **New from template** (online, Jotty 1.29+): `GET /api/templates`, then create with `templateId`.
- Swipe a card left to delete (off by default; Settings → Behavior).

**Viewing**
- Markdown rendering with selectable text (`SelectionContainer`), clickable links (open in the browser), GFM tables.
- Jotty's HTML `<table>` blocks are converted to GFM in `util/MarkdownHtmlTables.kt` before rendering (Markwon does not lay out HTML tables).
- Extra blank lines are kept as spacing; see `preserveExtraBlankLinesForDisplay` ([#116](https://github.com/Darknetzz/jotty-android/issues/116)).

**Editing**
- Default: Markdown source editor with toolbar and list continuation on Enter (`NoteEditor.kt`, `MarkdownToolbar.kt`, `MarkdownEditing.kt`).
- Optional rich editor: Settings → Behavior → rich note editor, a bundled WebView WYSIWYG editor (`ui/notes/WysiwygNoteEditor.kt`).
- Editing web-origin HTML or tables shows a save hint (`NoteSaveBehavior.kt`).
- Drafts autosave to `NoteDraftStore` (files in `noBackupFilesDir`, not Room) until saved or discarded; opening a note with a draft offers to restore it. Never store encrypted-note plaintext there.

**Detail and list ⋮ menus:** copy, **Change category**, server share (`/api/shares` when available), archive / unarchive, **Clone** (encrypted bodies are copied as stored), delete (with confirmation), export / share (title and content).

### Encrypted notes

- XChaCha20-Poly1305 only; PGP-encrypted notes show a message pointing to the Jotty web app. Format details in [AGENTS.md → Encryption](../AGENTS.md#encryption-jotty).
- Lock icon on encrypted notes. **Decrypt** asks for the passphrase; the plaintext is cached for the session in `NoteDecryptionSession` (`ConcurrentHashMap`).
- **Encrypt** opens `EncryptNoteDialog` (passphrase of at least 12 characters) and wraps the body in frontmatter via `XChaCha20Encryptor`. If encryption returns null, the dialog shows an error.
- Encrypt and decrypt run on `Dispatchers.Default`; the dialogs show a loading state.
- **Biometric unlock (per note):** after a password decrypt, "Remember with biometric" stores the passphrase in `BiometricPassphraseStore` (Android Keystore, strong biometric). Reopening can auto-prompt, or the decrypt dialog offers the fingerprint (`BiometricNoteUnlock.kt`). Settings → Security controls auto-prompt, the remember offer, and clearing all stored passphrases.

### Images

- Markdown images `![alt](url)` render through compose-markdown with Coil.
- Jotty 1.29+ serves attachments at `/api/attachment/{noteUuid}/{name}`, which works with the API key. Older `/api/image|file|video/{user}/{file}` paths still appear in unmigrated or encrypted notes and need a browser session. `NoteImageUrls.isJottyMediaPath` recognises both for host rewriting.
- Images on the same host as the Jotty server are requested with `x-api-key` and the instance's custom headers: `util/NoteImageLoader.kt`, `createNoteImageLoader(context, baseUrl, apiKey, customHeaders)`; loaders are cached per base URL, key and headers. `MainScreen.kt` creates the loader and passes it to `NotesScreen`.
- Inserting an image from the gallery is not possible: the Jotty API has no upload endpoint yet ([#76](https://github.com/Darknetzz/jotty-android/issues/76), upstream fccview/jotty#674). The editor's image button inserts a URL.

## Instances and setup

- Instances live in `SettingsRepository` (`JottyInstance`): URL, API key, optional accent colour (`colorHex`) and custom HTTP headers.
- **Custom headers** (for example reverse-proxy auth) are encrypted with the instance when the Keystore is available, sent on every API request and on same-host note images (including the WYSIWYG editor), and redacted from debug HTTP logs (`util/CustomHttpHeaders.kt`).
- **Default instance** (`defaultInstanceId`) is opened when the app starts without a current instance; set it with the star in Setup or Settings.
- Settings → **Manage instances** adds, edits and deletes instances without disconnecting. **Disconnect** clears only the current instance.
- **Test connection** (and Save) runs `ConnectionDiagnostics`: `api/health`, then an authenticated `api/summary`. `ConnectionTestResultCard` shows user, version and latency, or a titled failure with likely causes.
- Servers older than Jotty 1.25.0 are refused at setup (`util/JottyServerVersion.kt`); a missing or unparsable version is allowed.

## Settings

- **Overview:** dashboard from `api/summary`, health check (`api/health`).
- **Manage instances**, **Manage categories** (browse-only notes and checklist categories from `GET api/categories`; also in list overflow menus).
- **Appearance:** theme, palettes, accent hex, reader text size, content padding.
- **Behavior:** rich note editor, drag reorder for checklists and Kanban, swipe to delete, default note and checklist categories, **Local storage & sync**, **Manage pending** ([OFFLINE_SYNC.md](OFFLINE_SYNC.md)).
- **Security:** biometric unlock status, auto-prompt on open, remember-passphrase offer, clear all stored passphrases.
- **Troubleshooting:** export debug logs (the `AppLog` ring buffer, `util/DebugLogExporter.kt`).
- **About:** version, changelog, **Check for updates** (see below).

## Entry points

- **Deep link** `jotty-android://open/note/{noteId}` opens a note. `MainActivity` is `singleTask` and reads the id in both `onCreate` and `onNewIntent`; `JottyAppContent` → `MainScreen` → `NotesScreen` pass it down and clear it after opening.
- **Share target:** `ACTION_SEND` with `text/plain` from another app creates a note from the shared subject and text (`MainActivity`).
- **Home-screen widget:** `widget/QuickAddNoteWidget.kt` (Glance) is a single button that launches `MainActivity` with `EXTRA_QUICK_ADD_NOTE`, which reuses the share flow to open a new empty note.

## In-app updates

- Settings → About → **Check for updates** queries the GitHub releases API (`data/updates/UpdateChecker.kt`, `GitHubApi.kt`) on the selected **update channel**: Stable (latest release) or Dev (`dev-latest` pre-release). Results are cached for 5 minutes; errors are not.
- The changelog shown for an update comes from the bundled `CHANGELOG.md` asset (`BundledChangelog.kt`, copied from the repository root at build time) or from GitHub for newer versions.
- The APK is downloaded to the app cache and installed with the system installer (`util/ApkInstallHelper.kt`). Signing-key mismatches and version-code downgrades (for example dev → stable) are detected and explained instead of failing silently.
- Network access is only on demand; see [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Diagnostics

- `util/AppLog.kt`: tagged logging into an in-memory ring buffer; `d` also goes to logcat in debug builds. Exported from Settings → Troubleshooting.
- HTTP logging (`ApiClient`) only in debug builds.
- ProGuard keeps Gson, Bouncy Castle and all data model classes (`app/proguard-rules.pro`).
