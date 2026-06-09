# Changelog

All notable changes to Jotty Android are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

The top section tracks the rolling [`dev-latest`](https://github.com/Darknetzz/jotty-android/releases/tag/dev-latest) pre-release (`[dev-latest]`). Dev APK `versionName` is `VERSION_NAME-dev+<short-sha>` (seven-character commit; see the release **Commit:** line).

## [dev-latest](https://github.com/Darknetzz/jotty-android/releases/tag/dev-latest)

### Added

- **Biometric enrollment changed** — When device fingerprint or face unlock changes, remembered note passphrases are cleared with an explanation dialog; Settings shows an accurate count of still-valid remembered passphrases.
- **Checklist add-item search** — On simple checklists, the add field filters existing items as you type; matching a completed item unchecks it instead of creating a duplicate.
- **Kanban board / list toggle** — Project boards switch between the Kanban board and the tree/list view (inline edit, reorder) without losing Kanban when the status API is available.
- **Kanban per-column add** — Each board column has an add field to create a task in that status.
- **Kanban column reorder** — Card menu includes move up/down within the same column; drag handle reorder when enabled in Settings.
- **Kanban subtask progress** — Board cards show done/total subtask progress with a progress bar.
- **Kanban inline title edit** — Double-tap a card title or choose Edit to rename in place on the board.
- **Checklist share & archive** — Share checklist/Kanban as plain text; archive/unarchive via Jotty’s Archive category; server sharing dialog probes REST API when available.
- **Notes: copy, selection, WYSIWYG** — Copy note from the menu; text selection in view mode; optional rich text editor (Settings → Behavior). Notes with HTML or pipe tables open in the visual editor when you edit; view mode converts WYSIWYG HTML saves back to proper headings and GFM tables.
- **Visual / Markdown edit toggle** — While editing any note, switch between the visual editor and markdown source with the chips above the editor.
- **Archived filter** — Notes and checklists lists hide archived items by default; **Archived** chip shows them.
- **Checklist auto-emoji** — Settings → Behavior → **Show emojis on checklists** prepends keyword-matched emoji on checklist and Kanban item labels (display only; plain text is still saved to the server, matching Jotty web “Show Emojis”).

### Changed

- **Settings** — Export debug logs moved to the bottom of Settings, next to About.
- **Checklist overview progress** — List cards count nested subtasks (matches detail view).
- **Kanban board height** — Columns use viewport-aware height instead of a fixed 420dp.
- **New project tasks** — Top-level add-item and per-column add pass the column status to the server so new cards land in the intended column.
- **Kanban item detail** — Prompts to save or discard unsaved title/description changes when closing.

### Fixed

- **Encrypted notes accidentally decrypted on server** — Archiving an encrypted note while it was open in the app sent session plaintext to the server instead of the stored ciphertext; archive now keeps encryption, and offline sync refuses plaintext over encrypted note bodies.
- **Note view text selection** — Long-press to select and copy note body text in view mode (uses `MarkdownText` native selection; Compose `SelectionContainer` does not apply to the Markwon `TextView`).
- **WYSIWYG editor blank for HTML tables** — Note HTML (including tables from the Jotty web app) loads via the JavaScript bridge instead of `evaluateJavascript`, with theme-matched text and table borders.
- **WYSIWYG editor blank for markdown tables** — Visual mode no longer clears note body before content loads; markdown pipe tables convert to HTML when opening visual edit.
- **WYSIWYG editor always blank** — Note HTML is embedded in the editor page via `loadDataWithBaseURL` instead of a `JavascriptInterface` bridge that failed to load content from `file://` asset pages.
- **Dev → stable updates** — About now blocks in-app install when a dev build cannot be replaced by stable (version code), shows the full explanation in a scrollable dialog, and offers “Open release page” instead of a failing download.

### Documentation

- **Privacy policy** — [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md) for Google Play and users (local storage, Jotty server communication, optional GitHub update checks, no analytics).

---

## [1.5.2] - 2026-06-08

### Fixed

- **Checklists** — Server PATCH compatibility banner lays out message and Close side by side instead of overlapping.
- **Manage instances** — The default-instance star hint is shown only on the instance list, not while adding or editing an instance.
- **Manage instances** — Removed a clipped “Default” chip that appeared beside the instance name when setting the default instance (the filled star already indicates default).
- **Connect / setup** — A wrong API key is now caught when connecting an instance (shown as “Invalid API key”) instead of appearing to connect and only failing with an “Unauthorized” error on the Checklists/Notes screens afterwards.

### Changed

- **New note** — Create dialog asks for title and category only; body is edited after opening the note (shared text into the app still becomes note content).
- **Connect / setup** — Server URL uses an http/https dropdown beside the host field; pasting a full URL into the host field moves the scheme into the dropdown automatically; the scheme menu no longer wraps `https://` across two lines.

### Added

- **Kanban item detail** — Tap a project board card to edit the task title, description (save to server), status, and subtasks (add, rename, check, delete). Subtask edit/delete use icon buttons; cancel or save while renaming. Priority, score, target date, estimated time, and item metadata show as placeholders until the Jotty REST API exposes them ([#52](https://github.com/Darknetzz/jotty-android/issues/52)).
- **Kanban status chips** — Status options in task detail show the same colored dots as the board columns.
- **Note image auth banner** — When a note contains Jotty-hosted images and the server returns HTTP 401/403 (private media without `SERVE_PUBLIC_IMAGES=yes`), note detail shows a dismissible banner explaining the server setting; the banner clears after images load successfully.

### Documentation

- **Kanban item API proposal** — [docs/upstream/KANBAN_ITEM_FIELDS_API_PROPOSAL.md](docs/upstream/KANBAN_ITEM_FIELDS_API_PROPOSAL.md) and compatibility notes for upstream Jotty server fields.


---

## [1.5.1] - 2026-06-03

### Changed

- **Connect / setup** — Connection and validation errors show in a bordered danger alert (tinted background and warning icon) instead of plain red text; URL and API key fields highlight red only for missing required values, not failed connections.
- **Settings** — Removed the footer tagline below About (version and app info remain in the About dialog).

### Documentation

- **README** — Expanded feature list, centered screenshot gallery (no empty table cell), checklist offline notes, and build requirements (min SDK, Gradle version).
- **README screenshots** — Replaced gallery with v1.5.0 demo captures in `images/readme-*.png`; single HTML table for reliable dark/light layout on GitHub; full dark/light pairs for all screens; added [docs/README_SCREENSHOTS.md](docs/README_SCREENSHOTS.md) for capture notes.

### Added

- **Hide empty Kanban columns** — Settings → Behavior toggle to hide status columns with no tasks on project boards.

### Fixed

- **New checklist items** — Adding a second item no longer fails with “Couldn’t save” after the first item syncs a newly created checklist to the server (stale local checklist id in detail view).
- **New project / Kanban** — Project boards show the Kanban layout immediately after creation instead of a plain checklist until you leave and reopen.
- **First note save** — Saving a newly created note no longer fails after the note syncs to the server (stale local note id in the editor).
- **Encrypt new note** — Encrypting a note right after creation no longer fails with “Couldn’t save” when the note has already synced under a server id.
- **Note encryption** — On low-RAM devices, encrypting a note retries Argon2 with 32 MiB when 64 MiB fails; encrypted JSON always includes `t`/`m`/`p` so decrypt uses matching KDF params (Jotty web still decrypts 64 MiB notes; notes encrypted with the 32 MiB fallback decrypt in the app but not yet in the web app, which always uses libsodium INTERACTIVE). Encrypt dialog validates trimmed passphrase length (avoids whitespace-only passphrases). Thanks [@Emilien-Etadam](https://github.com/Emilien-Etadam) ([#22](https://github.com/Darknetzz/jotty-android/pull/22)).
- **Connection errors** — TLS/SSL failures (including hostname mismatch) no longer show raw OpenSSL/BoringSSL diagnostics; users see short guidance instead.
- **Manage instances** — Removing the last saved Jotty instance no longer leaves an infinite “Loading…” screen; instance management and setup work without a connected server, and the app returns to the connect flow when no instance is active.
- **Manage instances** — Settings no longer shows a second back arrow and duplicate title; the main app bar handles navigation and the screen shows only the default-instance hint.
- **Note images** — Jotty media URLs resolve with RFC 3986 rules (fixes root-relative paths when the instance URL includes a subpath), rewrite HTML `<img src>` before conversion, remap absolute `/api/image/` URLs to the configured instance host (e.g. LAN IP vs hostname), and always attach the API key on Jotty media paths. Export debug logs record HTTP failures for media loads. **Note:** standard Jotty servers still require `SERVE_PUBLIC_IMAGES=yes` or upstream API-key support on `/api/image/` for private uploads (see [JOTTY_SERVER_COMPATIBILITY.md](docs/JOTTY_SERVER_COMPATIBILITY.md)).


---

## [1.5.0] - 2026-06-02

### Added

- **Kanban delete task** — Kanban cards include a delete action (with confirmation) using the same checklist item API as list view.
- **Encrypted note session UI** — Decrypted notes show a **Decrypted** indicator, **Lock note** to hide content again, and stale session cache clears when server ciphertext changes.
- **Server PATCH compatibility banner** — Dismissible notice on checklist detail when the server lacks item PATCH (see [JOTTY_SERVER_COMPATIBILITY.md](docs/JOTTY_SERVER_COMPATIBILITY.md)).
- **Kanban status management** — Project/Kanban boards now include **Manage statuses** to add, edit, delete, recolor, and reorder columns using Jotty task status REST endpoints.
- **Kanban move menu status dots** — “Move to …” actions on Kanban cards show a colored dot matching the target column status.
- **Checklist type badge** — Checklist cards now show whether each item is a **Checklist** or **Project / Kanban**.
- **Smarter note editor lists** — Pressing Enter on a bullet, numbered, task, or quote line continues the same prefix; Enter on an empty list item exits the list. Toolbar list/checkbox actions toggle off when tapped again on the same line.
- **Note editor text size** — Settings → Appearance → **Reader text size** now also scales the note editor; added **Extra small** option.
- **Color themes** — Settings → Appearance adds **Lavender**, **Sunset**, and **Graphite** palettes (each with light and dark variants).
- **Custom accent color** — Settings → Appearance → **Custom** lets you pick an accent from swatches or a `#RRGGBB` hex value, with **Neutral** or **Tinted** background styles.
- **Unified search API** — Notes search (online) uses Jotty’s new `GET /api/search` when the query is at least two characters, with relevance ranking and fallback to `GET /api/notes?q=` on older servers.
- **Checklist item reorder ([#29](https://github.com/Darknetzz/jotty-android/issues/29))** — Up/down controls on each checklist row call `PUT /api/checklists/{id}/items/reorder`; works offline with sync replay.
- **Checklist drag-and-drop reorder** — Drag the handle on a checklist row to reorder among siblings (same rules as move up/down); To Do and Completed sections reorder separately. Disable via Settings → Behavior → **Drag to reorder checklists**.
- **Checklist item PATCH** — Inline item edits use `PATCH /api/checklists/{id}/items/{index}` when the server supports it.
- **Background offline sync** — Periodic WorkManager job attempts notes/checklists sync for saved instances when connectivity is available.
- **Per-item sync indicators** — Offline notes and checklists show a `Pending sync` label at card level when local changes have not reached the server yet.
- **Markdown toolbar actions** — Note editor toolbar now includes numbered-list and task-list insert actions.
- **Discard pending sync** — Checklist detail ⋮ menu → **Discard pending sync** when a checklist is stuck pending. Restores the server version when online, or deletes a never-synced local-only checklist.

### Changed

- **Checklist list cards** — Type badge sits on the trailing edge of the title row; note list dates align to the right of the metadata row.
- **Note detail** — Shows an **Updated** date line when the server provides `updatedAt`.
- **Stable updates from dev builds** — About warns when checking stable on a dev APK; install failures explain dev→stable version code limits.
- **Top-bar sync status** — Notes and Checklists show only the cloud icon in the app bar (next to refresh); tap it for last sync time, duration, and errors.
- **Top-bar sync icon colors** — Sync status cloud icon uses fixed semantic colors: green for healthy sync state and red when offline/failed, independent of appearance theme.
- **Checklist type badge styling** — Distinct colors for **Checklist** vs **Project / Kanban** for faster visual scanning.
- **Motion effects opt-in** — Cross-fade, list/detail fade-in, shimmer skeletons, and animated bottom tabs are off by default. Settings → Appearance → **Motion effects** can turn them on, or choose **System** to follow the device accessibility setting.
- **Dashboard overview layout** — Dashboard cards split into clearer sections with grouped stat tiles, section icons, and stronger visual hierarchy.
- **Settings → Appearance** — Theme, colors, padding, reader text size, and reduced motion on a dedicated screen.
- **Settings → Dashboard overview** — Summary stats and admin totals on a dedicated screen with pull-to-refresh.
- **Settings → Behavior** — Start screen, swipe-to-delete, note previews, and offline sync on a dedicated screen.
- **Checklist item row** — Shared `ChecklistItemRow` composable for online and offline checklist detail screens.
- **Stable Jotty compatibility** — Checklist item rename tries PATCH first, then falls back to delete-and-recreate on older servers.
- **Reduced motion behavior** — List/detail navigation uses a subtle fade-in only when reduced motion is off.
- **Checklist inline editing** — Checklist detail allows editing only one item at a time across online and offline screens.
- **Settings tab reselection** — Tapping the Settings bottom-nav item while on a subpage returns to the main Settings screen.
- **Reduced motion on bottom tabs** — Low-motion tab bar when reduced motion is enabled.
- **Bottom navigation behavior** — Bottom tabs hidden on nested Settings subpages.
- **Setup flow guidance** — Setup prioritizes URL/API key first; optional details behind an advanced toggle.
- **Checklist row actions** — Move/reorder/add-subtask actions grouped under an overflow menu.
- **List/detail transitions** — Opening a note or checklist no longer uses a list/detail fade (avoids flicker during refresh/sync).
- **Dependencies** — Updated AndroidX (Compose BOM, Lifecycle, Room, DataStore, Work, Security Crypto), Retrofit 3, Navigation Compose, Fragment KTX, KSP, and CI actions; no intended behavior change.
- **Build toolchain** — Gradle 9.5.1, Android Gradle Plugin 9.2.1, and Kotlin 2.3.21.

### Fixed

- **Kanban status colors** — Column dots and move-menu swatches show default accents when the server omits `color` on standard columns (grey / blue / green for To Do, In Progress, Completed).
- **Manage statuses layout** — Status title fields in the Kanban dialog no longer collapse when color swatches are shown; colors sit on their own row below the title.
- **Manage statuses colors** — Selected status color shows a larger swatch, primary ring, and checkmark.
- **Dev update check** — Dev channel update checks read the commit SHA from `dev-latest` release notes (plain `Commit:` line or markdown table).
- **Note images** — Relative image URLs in note bodies (e.g. `/api/image/...`) resolve against the Jotty instance base URL.
- **Checklist type badges** — Cards classify project/kanban checklists more reliably (including non-`simple`/`regular` server types).
- **Checklist drag gesture stability** — Dragging can move across multiple rows in one gesture; one reorder sync runs on release (invalid drops revert).
- **Note detail top gap** — No empty strip under the tab title when opening a note or checklist in list/detail mode.
- **Custom theme in Neutral mode** — Custom accent applies to chips and containers in Neutral mode, not only Tinted backgrounds.
- **Checklist offline repository churn** — Offline ViewModel factory stops the checklist repository from being recreated on every recomposition.
- **Export debug logs** — **Save** writes to Downloads (or a file picker); **Share** uses a file attachment.
- **Checklist list not opening during sync** — Background sync no longer drives pull-to-refresh “refreshing” state during sync.
- **Checklist sync diagnostics** — Failed checklist pushes log server errors and pending op summaries to exported debug logs.
- **Checklist pending sync while online** — Sync replay skips ops already applied on the server; DELETE 404 treated as done.
- **Checklist sync HTTP 400 / stuck pending** — Item ops replay before metadata push; fewer stuck “Pending sync” states after partial replay.
- **Checklist list not tappable** — Swipe-to-refresh restored; checklist cards use a single click target; separate offline repo/UI ViewModel keys.
- **Checklist stuck after offline edits** — Item changes stay local while pending sync instead of calling the server with stale paths.
- **Checklist refresh UI** — Pull-to-refresh and sync no longer flash a full-list loading overlay; clearer sync error messages.
- **Checklist overview flicker during sync** — List no longer flashes “No checklists yet” while offline sync replaces the local cache.
- **Pull-to-refresh with pending checklist changes** — A failed push for one checklist no longer aborts the whole sync.
- **Deep-link feedback visibility** — `Note not found` feedback appears even when the note list is empty.
- **Update download progress updates** — In-app update progress callbacks throttled during APK download.
- **CI release parity check** — CI assembles the release variant to catch release-only build issues earlier.
- **Bottom-tab safe area in reduced motion** — Low-motion bottom tab bar respects system navigation insets.
- **Checklist card title taps** — Tapping a checklist title opens the checklist (long-press still opens the row menu).
- **Export debug logs snackbar** — Fixed Compose lint failure when showing the Downloads save confirmation.

### Documentation

- **Jotty server compatibility** — [JOTTY_SERVER_COMPATIBILITY.md](docs/JOTTY_SERVER_COMPATIBILITY.md) documents PATCH fallback, Kanban APIs, and older-server behavior.
- **Checklist reorder** — [CHECKLIST_REORDER.md](docs/CHECKLIST_REORDER.md) updated for the shipped Jotty REST API (`PUT …/items/reorder`, `PATCH …/items/{index}`, `GET /api/search`).
- **Changelog layout** — Rolling pre-release notes use a `[dev-latest]` section (replacing `[VERSION-dev]`).

---

## [1.4.0] - 2026-05-29

### Added

- **Home-screen widget** — A **New note** Glance widget opens the app straight into the create-note dialog.
- **Markdown formatting toolbar** — The note editor has a toolbar for bold, italic, code, heading, bullet list, quote, and link that wraps the current selection or inserts at the cursor.
- **Share into Jotty** — Text shared from other apps (and the widget) opens a prefilled new note via an `ACTION_SEND` share target.
- **Category management** — Pick or type a category when creating notes/checklists and change it from the detail editor, with suggestions from existing categories.
- **Sort menu** — Notes and checklists can be sorted by Updated, Created, or Title; the choice is remembered.
- **Material You** — Settings → Appearance → Theme color adds a **Dynamic** option using wallpaper colors on Android 12+.
- **Reader text size** — Settings → Appearance → **Reader text size** (Small / Medium / Large / Extra large) scales note content.
- **Quick instance switcher** — The top bar shows the current instance accent and a menu to switch instances or manage them.
- **Richer dashboard** — Settings → Overview now breaks down checklist items and tasks with totals and completion progress bars.
- **Reduced motion** — Settings → Appearance → **Reduced motion** (System / On / Off). System follows the device accessibility setting; On skips decorative transitions such as the setup ↔ main cross-fade.
- **Note list preview toggle** — Settings → Behavior → **Note preview in list** shows or hides the body excerpt under each title in the notes list (encrypted notes still show the lock label).
- **Update changelog from GitHub** — When checking for updates, the app fetches `CHANGELOG.md` from the matching branch (`main` for stable, `dev` for dev-latest) and uses it for “What’s new” instead of the bundled file on the installed APK.

### Changed

- **Settings UX** — “Offline mode” renamed to **Local storage & sync**; sync status when unreachable shows **Server unreachable** (not “Offline”). **Export debug logs** replaces the debug-logging toggle (in-app ring buffer, share as file). Default instance is set via the star in **Manage instances** only (removed from Settings overview). Settings split into Appearance, Behavior, and Troubleshooting; dashboard moved under Overview. Manage instances from Settings shows back navigation and a default-instance hint.
- **Decrypt errors** — Failed decrypt shows a collapsible **Details** section for technical messages (no debug toggle).
- **About update status** — Check-for-updates results use styled alert banners (success, info, error, and loading) with icons in the About dialog.
- **In-app changelog** — About → View changelog shows the bundled `CHANGELOG.md` for the installed version; when an update is available, View changelog for … opens the matching section (or GitHub release notes as fallback) in a scrollable dialog.
- **Detail top spacing** — Note and checklist detail screens now use a tighter top inset (4dp) so the title/header sits closer to the app bar while preserving a small visual gap.
- **Online checklist parity** — The online-only checklists screen gains the search field and category filter chips already present in the offline-enabled screen.
- **Animated list ↔ detail** — Opening and closing a note or checklist now slides between the list and detail (respecting Reduced motion).
- **Loading skeletons** — List screens show shimmer placeholders while loading instead of a centered spinner.
- **Pull-to-refresh on empty/error** — Empty and error states for notes and checklists can now be pulled to refresh.
- **Category filter persistence** — The selected notes/checklists category filter survives app restarts.
- **Editing encrypted notes** — Saving an edited encrypted note re-uses the passphrase from this session by default (same as when you unlocked it). Tap **Change passphrase** in the save dialog if you want a new one.

### Added

- **Legacy encryption detection** — After decrypting a note encrypted with the old Android payload order (`tag` then `ciphertext`), the app shows a warning that the note should be re-encrypted and saved to restore Jotty web compatibility.

### Documentation

- **Agent guide** — `AGENTS.md` now requires updating `CHANGELOG.md` under `[VERSION-dev]` for user-visible changes; XChaCha20 format notes corrected (`ciphertext` then `tag`, legacy decrypt path).

### Fixed

- **Web decrypt after Android save** — Notes encrypted or re-encrypted in the app now use hex-encoded salt, nonce, and payload (matching the Jotty web app) instead of base64, so the web UI can decrypt them with the same passphrase.
- **Note content artifacts after save** — Invisible Unicode (BOM / zero-width characters) embedded in web-authored HTML is stripped on decrypt and before re-encrypt so it no longer appears as odd symbols in the app or syncs back to the web. Font-family `<span>` tags from the web editor are unwrapped for in-app display instead of showing broken HTML like `pan style=...`.
- **XChaCha20 encryption and Jotty web** — Notes encrypted in the app now use AEAD combined order (`ciphertext` then `tag`), matching the Jotty web app. Previously, web showed “Incorrect password” for the correct passphrase; existing legacy-format notes still decrypt in the app and are flagged for re-encryption.
- **Encrypted notes missing from list** — Notes returned by the API with sparse or null fields (common on some encrypted payloads) are normalized on fetch/sync so they appear in the notes list instead of being dropped.
- **Biometric settings when unavailable** — Settings → Security hides auto-prompt, save-offer, and clear-all options when biometrics are not enrolled or not supported; only the status row is shown.
- **Empty checklists/notes after first setup** — Initial sync no longer runs in a `LaunchedEffect` that could be cancelled when connectivity or settings update (showing “Job was cancelled” with an empty list). Sync is started from the offline ViewModel scope and re-runs while the local cache is empty and the device is online; overlapping sync requests are serialized instead of skipped; cancelled syncs are not recorded as user-visible errors; debounce is skipped while the local checklist cache is empty.
- **Note image rendering from HTML content** — Notes containing HTML image tags now render images in-app: standalone `<img>` plus common wrapper patterns (`<figure>`, `<picture>`) are converted to Markdown image syntax before rendering, matching web-authored note content more reliably.
- **Colored note text from web HTML** — Notes containing inline color spans (e.g. `<span style="color: ...">`) now preserve color in-app by converting them to a renderer-friendly HTML color format before markdown rendering.
- **Bottom-tab reselect in detail** — While viewing a note or checklist, tapping the active bottom tab (Notes/Checklists) now closes detail and returns to the overview list, matching back-button behavior.
- **Edit decrypted notes** — A decrypted note can now be edited; saving re-encrypts the body with a passphrase you confirm.
- **Checklist undo restores items** — Undoing a checklist delete now restores the full item tree (including nested items and completion state), not just the title and type.
- **Duplicate note title removed** — The note title no longer appears twice (it was rendered in both the app bar and the note view).
- **Offline category moves** — Moving an offline note between categories now sends `originalCategory` on sync so the server moves it correctly.
- **Theme palette edge cases** — `sepia`+dark and `midnight`+light now use matching variants instead of falling back to unrelated schemes.
- **Note open crash** — Fixed Compose layout crashes when opening notes: list/detail no longer compose two scrollables at once; detail uses `Scaffold(topBar)` with a bounded body `Box`; online detail fills the list/detail pane; offline detail uses `weight(fill = true)`; encrypted placeholder is centered in a `Box`; biometric auto-unlock waits briefly after navigation. Debug log export now includes uncaught crash stack traces.

---

## [1.3.6] - 2026-05-27

### Added

- **Biometric note unlock (settings)** — Settings → Security: biometric status, auto-prompt on open, offer to remember passphrase after password decrypt, and clear all remembered passphrases. Per-note storage unchanged.
- **Biometric decrypt UX** — Shared unlock helper; decrypt dialog offers fingerprint when a passphrase is saved; auto-prompt on open respects the setting; errors show via snackbar; cancel allows auto-prompt to retry.

- **Checklist conflict copies UX** — Offline checklists list shows the same conflict-copies banner and “View copies” flow as notes (`getConflictCopiesFlow`, `ConflictCopiesBanner`).
- **Note detail architecture** — `NoteDetailViewModel` and `NoteDetailActions` (`ApiNoteDetailActions`, `OfflineNoteDetailActions`); offline detail no longer stubs `JottyApi`.
- **List data sources** — `NotesListDataSource` / `ChecklistsListDataSource` with online and offline implementations (`data/repository/ListDataSources.kt`) as a step toward unified list screens.
- **Tests** — Checklist sync conflict tests; ViewModel tests for offline notes/checklists and `NoteDetailViewModel`.

### Changed

- **Note detail logging** — Encryption debug lines use `AppLog` (gated by Settings → debug logging) instead of unconditional `Log`.
- **Shared offline UI** — `ConflictCopiesBanner` in `ui/common/`; notes and checklists list screens use it.

### Fixed

- **HTML tables in notes** — Notes saved with Jotty’s default HTML table format (Profile → table syntax: HTML) now render as proper tables in note view; GFM pipe tables were already supported.
- **Biometric decrypt empty note body** — Blank session cache or empty decrypt result no longer skips the encrypted placeholder; biometric unlock uses the same `onDecrypted` path as passphrase decrypt, requires a parsed encrypted body before auto-prompt, and surfaces failure when the biometric cipher or ciphertext is missing.
- **Online note delete from detail** — Overflow → Delete now calls `deleteNote` on the server (previously only closed detail and refreshed the list).
- **Checklist item rename save failure ([#33](https://github.com/Darknetzz/jotty-android/issues/33))** — Replaced unsupported checklist text update calls with a leaf-only rename flow (add replacement item, then delete original), including offline replay support and clearer UI hints for parent/project items.

### Documentation

- **Checklist reorder ([#29](https://github.com/Darknetzz/jotty-android/issues/29))** — Expanded [CHECKLIST_REORDER.md](docs/CHECKLIST_REORDER.md) (web server-action capture vs REST, id vs index-path); [upstream/CHECKLIST_REORDER_API_PROPOSAL.md](docs/upstream/CHECKLIST_REORDER_API_PROPOSAL.md) for fccview/jotty. In-app reorder deferred until that API exists.
- **`docs/TODO.md`** — Populated with follow-up backlog; **`docs/OFFLINE_NOTES.md`** — marked offline checklists as implemented in future-improvements list.

---

## [1.3.5] - 2026-05-24

### Fixed

- **Git `dev-latest` tag on pull** — `scripts/setup-repo-git` adds a force-fetch refspec so `git pull --tags` no longer fails with “would clobber existing tag”; `scripts/pull-dev` runs setup and pulls `dev`. Optional `.githooks/post-merge` keeps the tag in sync on `dev` when `core.hooksPath` is set via setup.

- **Dev in-app updates (“App not installed”)** — `dev-latest` CI assigns a monotonic `versionCode` per workflow run (same signing as stable when secrets are set). The app checks APK signing and version code before opening the installer and shows a clear message when the installed build was debug-signed or sideloaded with a different key.
- **Offline checklists stuck offline ([#27](https://github.com/Darknetzz/jotty-android/issues/27))** — Notes and checklists share one app-wide `NetworkConnectivityMonitor` (single network callback) so tab switches no longer desync Online/Offline; offline UI reads the shared online state. Sync runs when connectivity is restored. Homelab/LAN: online when `INTERNET` is available (not only `VALIDATED`); connectivity is re-checked when the app returns to foreground.
- **Offline sync error display** — Reverse-proxy HTML (e.g. nginx `403 Forbidden`) is no longer shown in the UI; users see “Access denied” instead. Sync failures keep local offline items visible (snackbar + last-error row) instead of a full-screen error that hides them.

### Changed

- **Offline connectivity UX** — Prominent error banner and highlighted offline status when the server cannot be reached; **Try sync** on list and detail screens.
- **Branch sync after release** — CI fast-forwards `dev` to `main` after each push to `main`, so release merge commits do not leave `main` ahead of `dev` with identical trees.

---

## [1.3.4] - 2026-05-24

### Fixed

- **Dev in-app updates (“App not installed”)** — `dev-latest` CI now builds a **release-signed** dev APK (same keystore as stable) when secrets are configured; dev version suffix applies to release builds. Update checker prefers non-`debug` APK assets. Settings shows signing hints on the dev channel.

---

## [1.3.3] - 2026-05-24

### Added

- **Note delete from detail** — Open a note → overflow (⋮) → Delete with confirmation; uses the same offline/sync path as list delete (`NoteDetailScreen`, `OfflineNoteDetailScreen`).
- **Checklist reorder documentation** — `docs/CHECKLIST_REORDER.md` explains why item reorder is not in the app (Jotty web uses a server action, not the REST API) and what would unblock Android support.
- **Checklist sync push-failure copy** — `sync_push_failed_kept_local_checklists` when checklist changes could not be pushed and local data was kept.

### Changed

- **Offline checklist sync** — After pushing dirty checklists, sync **aborts the server pull** if any rows stay dirty (mirrors notes). Failed `syncChecklist` / delete push and **pending-op replay failures** no longer proceed to a full local replace. Conflict detection also considers **item trees** and pending ops, not only title/category.
- **Checklist sync races** — Item add/check/delete/update runs under a mutation guard; automatic sync is **deferred** while mutations are in flight and **debounced** (3s). Manual pull-to-refresh uses `syncChecklists(force = true)` (`OfflineChecklistsRepository`, `OfflineEnabledChecklistsScreen`).
- **Setup server URL** — Hint to include `http://` or `https://` for local servers.

### Fixed

- **HTTP (LAN) connections on release builds** — Restored cleartext HTTP for self-hosted servers (e.g. Docker on a NAS) after 1.3.2 blocked it ([#28](https://github.com/Darknetzz/jotty-android/issues/28)); `network_security_config` and manifest align with homelab `http://` URLs while still trusting user-installed CAs for HTTPS.
- **Network error messages** — `ApiErrorHelper` maps cleartext-blocked and connection-refused failures to dedicated strings instead of generic “Network error”.
- **Offline checklist sync data loss** — If push or pending-op replay fails, the repository no longer `deleteAll`s and replaces from a possibly stale server snapshot; local checklist state is kept for retry. Added JVM test `syncChecklists_whenPushFails_doesNotWipeLocalChecklistsWithServerSnapshot`; replay-failure test now expects sync failure with dirty row preserved (`OfflineChecklistsRepositoryTest`).

---

## [1.3.2] - 2026-05-20

### Added

- **Checklist detail actions** — Rename and delete a checklist from the detail screen overflow menu (online and offline).
- **Delete confirmation** — Checklists, tasks, and notes ask for confirmation before delete (including swipe-to-delete); overflow menus use edit/delete icons with red delete text.
- **Performance baseline test** — Added `PerformanceBaselineTest` for startup and Notes-tab timing.
- **Sync diagnostics metadata** — Added last attempt/success time, duration, and error state for offline sync.
- **Sync status copy** — Added strings for last sync timestamp, duration, and last error.
- **Checklist search copy** — Added a dedicated “Search checklists” placeholder.
- **Sync status tests** — Added `SyncStatusStateTest` for sync timing/error transitions.
- **Gradle version catalog** — `gradle/libs.versions.toml` centralizes AGP, Kotlin, KSP, Compose BOM, and ktlint plugin versions; root and `app` build scripts use version-catalog aliases.
- **ktlint** — Android Gradle plugin + project `.editorconfig`; **`ktlintCheck`** runs in CI after unit tests and lint.
- **Dependabot** — `.github/dependabot.yml` for weekly Gradle and GitHub Actions updates.
- **Network & backup XML** — `network_security_config.xml`, `fullBackupContent` / `dataExtractionRules` rulesets to avoid backing up API keys and sensitive prefs where possible.
- **Passphrase buffers** — `PassphraseEncoding.kt` plus `CharArray` entry points for XChaCha20 encrypt/decrypt and biometric passphrase storage, with best-effort zeroization (JVM limits still apply to `String` fields in the UI).
- **Room index (notes)** — `@Index("instanceId")` on `NoteEntity` with migration **3 → 4**.
- **Monochrome adaptive icon** — Themed launcher icon layer in `ic_launcher` / `ic_launcher_round` adaptive XML.
- **CI artifacts** — Unit test HTML reports and lint HTML reports uploaded from the main CI job.
- **Release-signed GitHub APK** — When repository secrets `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` are set, the release workflow builds only a minified **`jotty-android-{version}.apk`** signed with that keystore (in-place updates; [#9](https://github.com/Darknetzz/jotty-android/issues/9)). Without secrets, a debug APK is attached and the workflow summary notes the signing gap.
- **Release keystore helper** — `create-release-keystore.ps1` to create or configure `keystore.properties` for local release builds (see `keystore.properties.example`).

### Changed

- **GitHub Actions** — Updated CI and release workflows to action releases that target the Node 24 runtime (`actions/checkout` v6, `actions/setup-java` v5, `android-actions/setup-android` v4, `softprops/action-gh-release` v3; the instrumentation smoke-test job pins `reactivecircus/android-emulator-runner` v2.37.0, which moved to Node 24).
- **CI smoke coverage** — Instrumentation smoke job now runs `MainActivitySmokeTest` and `PerformanceBaselineTest`.
- **CI unit-tests job** — `setup-java` now enables **`cache: gradle`** like other jobs.
- **DRY list state handling** — Offline Notes and Checklists now share `ListScreenState` and `OfflineSyncStatusRow`.
- **Offline list sync UX** — Notes and Checklists show a subtle “Last sync” timestamp in the status row.
- **Note card rendering** — `NoteCard` now memoizes derived values to reduce recomputation while scrolling.
- **CHANGELOG links** — Deduplicated release reference links in the footer.
- **Lifecycle-aware state** — Main and major screens collect settings and repository `Flow`s with **`collectAsStateWithLifecycle`**.
- **Rotation / navigation setup** — `JottyApp` and `MainScreen` use **`rememberSaveable`** (or equivalent) so resolved setup vs main navigation and start tab do not flash loading on configuration change.
- **Online notes architecture** — Online `NotesScreen` state is driven by **`NotesViewModel`** (search, filters, selection) for clearer rotation and test boundaries.
- **Swipe delete + undo** — Online notes and checklist lists align with undo snackbars where applicable; checklist rows gain clearer delete affordances (e.g. overflow) alongside swipe when enabled; **`SwipeToDeleteContainer`** drops an unused **`CoroutineScope`** parameter.
- **Debug HTTP logging** — OkHttp **`HttpLoggingInterceptor`** uses **`HEADERS`** (not **`BODY`**) and **redacts `x-api-key`**.
- **System bars & back** — `MainActivity` enables **predictive back**; **`JottyTheme`** aligns **navigation bar** appearance with status bar / theme.
- **Accessibility (checklists)** — Task text uses **`Role.Button`** semantics; row icon actions use **48dp** tap targets where needed.
- **API error surfacing** — **`ApiErrorHelper`** reads capped **error-body** JSON (e.g. `message`, `error`, `detail`) when Retrofit returns **`HttpException`**.
- **Coil note images** — **`NoteImageLoader`** uses a **bounded** loader map and tuned cache / size behavior for note Markdown images.
- **Argon2 from note JSON** — **`XChaCha20Decryptor`** prefers **`t` / `m` / `p`** (and legacy keys) from the encrypted payload when present, then falls back to preset brute-force.
- **Offline checklist pending ops** — Pending op lists are **deduplicated** when applying and replaying to reduce duplicate side effects on retries.
- **README** — Gradle wrapper bootstrap example uses **Gradle 9.1.0** to match `gradle-wrapper.properties`.
- **R8** — **`android.r8.strictFullModeForKeepRules=true`** in `gradle.properties` (release minify verified with current keep rules).
- **Stable release APK** — Download **`jotty-android-{version}.apk`** from GitHub Releases (release-signed). Updating from an older **debug-signed** release APK may show “App not installed”; uninstall once, then install the new APK. Uninstall removes **on-device** data (saved instances, API keys, offline cache); notes and checklists on your **Jotty server** are unchanged—you will need to connect again.

### Fixed

- **Checklist search placeholder mismatch** — Checklist search no longer uses notes-specific placeholder text.
- **Offline notes sync data loss** — If any dirty note push fails, the repository **no longer** deletes local notes and replaces from server; replace + conflict copies run inside a **single `withTransaction`**. Added JVM coverage for the “push fails → local row survives” case (`OfflineNotesRepositoryTest`).
- **Biometric unlock on encrypted notes** — After fingerprint auth, the app **decrypts** the note with the stored passphrase instead of treating the passphrase string as the decrypted body (`NoteDetailScreen`, `XChaCha20Decryptor`).
- **Decrypt log hygiene** — XChaCha20 decrypt diagnostics no longer log passphrase length or JSON prefixes at **INFO** unless **Settings → Debug logging** is on (`AppLog.isDebugEnabled()`).

### Security

- **Cleartext traffic** — Removed blanket **`usesCleartextTraffic="true"`**; HTTPS-by-default via **`networkSecurityConfig`** (see XML for debug vs release behavior and homelab notes).
- **Backups** — **`android:fullBackupContent`** and **`android:dataExtractionRules`** exclude DataStore, encrypted shared preferences, and biometric passphrase prefs from cloud/device-transfer backups where configured.

---

## [1.3.1] - 2026-05-07

### Added

- **Update channel (stable vs dev)** — About → “Check for updates” can target either the latest **stable** GitHub release or the rolling **`dev-latest`** pre-release. The choice is persisted in DataStore. Dev checks compare the release `Commit:` line to the app’s `-dev+` SHA suffix; stable checks compare semver using the base version (dev suffix stripped). (`UpdateChecker`, `GitHubApi`, `SettingsRepository`, `SettingsScreen`, `strings.xml`.)
- **Shared offline repository runtime** — Introduced reusable `OfflineRepositoryLifecycle` and `SyncStatusState` to centralize connectivity callback wiring, coroutine scope ownership, sync state tracking, and lifecycle cleanup for offline repositories.
- **Checklist replay failure feedback** — Checklist sync now tracks pending-operation replay failures (e.g. stale item paths) and surfaces a user-facing snackbar so silent skips are visible.
- **Checklist offline repository tests** — Added focused JVM tests for checklist offline sync behavior, including offline failure and replay-failure counting paths.
- **CI hardening** — CI now includes an Android instrumentation smoke-test job (emulator + `MainActivitySmokeTest`) and GitHub dependency vulnerability review on pull requests.

### Changed

- **DRY offline architecture** — Notes and checklists offline repositories now compose shared lifecycle/sync-state infrastructure instead of duplicating connectivity/scope/cleanup logic.
- **CI lint coverage** — CI lint step now runs both debug and release variants (`lintDebug` + `lintRelease`) instead of a single generic lint invocation.

### Fixed

- **Legacy credentials migration** — When encrypted API key storage ([EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)) is unavailable on a device, migrating from legacy `server_url` / `api_key` preferences no longer clears those keys while saving an empty instance API key (which would lock the user out). The key remains in DataStore plaintext in that rare fallback path, consistent with other non-encrypted flows.

### Technical

- **Update-channel parser tests** — `UpdateCheckerTest` covers `parseUpdateChannel`, base-version stripping, dev SHA extraction, dev release-body parsing, and local-vs-remote SHA matching used by the new update channel.

### Credits

- Encrypted API key storage, biometric note passphrase storage, XChaCha20 encryptor fix, and related changes were merged from [#5](https://github.com/Darknetzz/jotty-android/pull/5). Thanks [@Emilien-Etadam](https://github.com/Emilien-Etadam) for driving the contribution.

---

## [1.3.0] - 2026-05-05

### Added

- **App screenshots** — Added current application screenshots to the repository for clearer presentation in docs and project pages.
- **Setup API key guidance** — Setup now includes clearer guidance for generating API keys, plus an action to open Jotty in the browser directly from the form. (`SetupScreen`, `strings.xml`.)
- **Offline conflict review banner** — Notes now show a persistent local-copy warning with a **View copies** action when conflict copies are present, so conflict resolution is not lost after a snackbar disappears. (`OfflineEnabledNotesScreen`, `OfflineNotesRepository`, `strings.xml`.)
- **Offline cleanup coverage** — Repository tests cover conflict-copy filtering and per-instance local note cleanup. (`OfflineNotesRepositoryTest`.)
- **Clearer API error messages** — HTTP **401** and **403** map to dedicated strings (invalid API key / access denied). **SSL/TLS** failures map to a short message about HTTPS and certificates. (`ApiErrorHelper`, `strings.xml`, tests.)
- **Offline repository tests** — JVM tests with in-memory Room and a fake `JottyApi` cover sync, server replace, and conflict “(Local copy)” behavior. (`FakeJottyApi`, `OfflineNotesRepositoryTest`; Robolectric + test dependencies.)
- **CI** — GitHub Actions workflow runs `./gradlew test` and `./gradlew lint` on push/PR (JDK 17 + Android SDK setup).
- **UI smoke test** — Instrumented test checks that `MainActivity` shows a Compose root (`MainActivitySmokeTest`).
- **`build.sh`** — Linux build script mirroring `build.ps1`: Gradle wrapper bootstrap (version read from `gradle-wrapper.properties`), Java 11+ discovery (`java` on `PATH`, `/usr/lib/jvm/*`, `/opt/android-studio/jbr`, etc.), Android SDK detection (`ANDROID_HOME`, `~/Android/Sdk`, `/opt/...`, scan for `platform-tools` under `/opt`), copies debug/release APK to `jotty-android.apk`.
- **`local.properties.example`** — Documents `sdk.dir` and typical Studio vs SDK paths (including IDE under `/opt`).
- **Offline checklists** — Added offline-mode support for checklists, mirroring notes behavior so checklist operations continue while disconnected and sync when connectivity returns.

### Changed

- **UX polish** — Startup and main-tab loading/error states now use shared centered components; tab titles live in the app bar; note detail shows the note title in the app bar. (`JottyApp`, `MainScreen`, `ListScreenComponents`, notes/checklists/settings screens.)
- **Navigation clarity** — Settings → Manage instances keeps the Settings bottom-navigation item selected and uses the main app bar back affordance. (`MainScreen`, `SetupScreen`.)
- **API error messages** — HTTP **404** maps to “Not found” and **429** maps to a rate-limit message. (`ApiErrorHelper`, `strings.xml`, tests.)
- **Checklists & offline notes UI** — `ChecklistsViewModel` and `OfflineEnabledNotesViewModel` hold list/filter/selection state with `StateFlow`; screens collect via `viewModel { … }`. Notes search debouncing uses `Flow.debounce` with no delay for blank queries.
- **Offline sync routing** — Replaced the fragile timestamp heuristic with an explicit `isLocalOnly` flag to correctly route create vs update during offline sync and avoid duplicate/lost notes.
- **README** — Build requirements list Android SDK **36** to match `compileSdk` / `targetSdk`.

### Fixed

- **Account switching on same server** — Offline notes/checklists now recreate their ViewModel/repository when auth changes for the same instance ID, preventing stale API-key sessions from showing another account’s data. (`MainScreen`, `OfflineNotesScreen`, `OfflineChecklistsScreen`.)
- **Removed-instance local data** — Removing a saved instance now clears that instance’s local offline notes from Room so stale data does not remain after credentials are removed. (`SetupScreen`, `OfflineNotesRepository`.)
- **Layout height** — App content now uses the full height between the top and bottom bars; the root AnimatedContent and main NavHost use `fillMaxSize()` so there is no extra margin above or below the content area.
- **Offline sync coroutine scope** — `OfflineNotesRepository` uses `SupervisorJob` + `CoroutineExceptionHandler` so background work is not torn down by a single failure. Optional `initialOnlineOverride` and `registerNetworkCallback` support unit tests without `ConnectivityManager`.
- **`init` block** — Replaced invalid `return@init` with `if (registerNetworkCallback) { … }` so Kotlin compiles cleanly.
- **Lifecycle cleanup** — Offline notes repository lifecycle is now ViewModel-owned with explicit cleanup so network callbacks/scopes are released correctly on destination teardown.
- **Category chips overflow** — Replaced fixed chip rows with scrollable `LazyRow` behavior so all categories remain reachable.

### Technical

- `build.ps1`: renamed `Ensure-AndroidSdk` to `Initialize-AndroidSdk` for naming clarity and consistency.
- `gradle.properties` / wrapper: build scripts download `gradle-wrapper.jar` matching the version in `gradle-wrapper.properties` (e.g. 9.1.0) and validate the JAR manifest; `build.ps1` updated the same way.
- `app/build.gradle.kts`: `testOptions.unitTests.isIncludeAndroidResources`, Room testing, Robolectric, coroutines-test; `androidTest` deps + `testInstrumentationRunner` for Compose UI tests.
- `OfflineNotesRepository.kt`: constructor parameters for tests only (defaults unchanged for app use).

### Credits

- Offline improvements listed above were merged from [#4](https://github.com/Darknetzz/jotty-android/pull/4). Thanks [@Emilien-Etadam](https://github.com/Emilien-Etadam) for the contribution.

---

## [1.2.9] - 2026-02-14

### Added

- **Offline notes** — Create, edit, and delete notes without an internet connection. Changes are saved locally and sync automatically when online.
- **Conflict resolution** — When a note is edited both offline and on the server, the app detects the conflict and creates a local copy with a "(Local copy)" suffix so no data is lost. A snackbar notifies you and offers "View copies" to find conflict copies.
- **Sync status indicators** — Notes screen shows connection status: cloud with checkmark (online), cloud with sync icon (syncing), or cloud off (offline).
- **Settings → Offline mode** — Toggle to enable or disable offline support (General section). Default: on.

### Changed

- **Notes screen** — When offline mode is enabled, notes are stored in a local Room database and synced when connectivity returns. Refresh button is disabled when offline. "Saved locally" snackbar appears when saving offline.

### Fixed

- **Notes section crash** — Opening the Notes tab no longer crashes. The app now declares `ACCESS_NETWORK_STATE` so `ConnectivityManager` is available; `OfflineNotesRepository` handles a missing service safely (treats as offline). When the Notes tab is shown with no current instance, a loading placeholder is shown instead of empty content.
- **Category labels wrapping** — Category names in filter chips (e.g. "Uncategorized") and on note cards no longer break across multiple lines; they stay on one line with ellipsis when space is limited.

### Technical

- `data/local/`: JottyDatabase, NoteEntity, NoteDao, OfflineNotesRepository.
- `ui/notes/`: OfflineNotesScreen (wrapper), OfflineEnabledNotesScreen, OfflineNoteDetailScreen.
- OfflineNotesRepository: connectivity monitoring via ConnectivityManager.NetworkCallback, auto-sync on network available.
- SettingsRepository: `offlineModeEnabled`, KEY_OFFLINE_MODE.
- ProGuard: keep rules for Room entities and DAOs.
- Documentation: OFFLINE_NOTES.md, CONFLICT_RESOLUTION.md, UI_CHANGES.md.
- strings.xml: `saved_locally`, `sync_conflicts_detected`, `view_conflicts`, `online`, `syncing`, `offline`, `offline_mode`, etc.
- AndroidManifest: `ACCESS_NETWORK_STATE` permission.
- OfflineNotesRepository: `ConnectivityManager` obtained with `as?`, callback registered only when non-null; `checkConnectivity()` returns false when service is unavailable.
- MainScreen: Notes composable shows centered "Loading" when `instanceId` is null.
- OfflineEnabledNotesScreen, NotesScreen, NoteCard: category label `Text` uses `maxLines = 1`, `overflow = TextOverflow.Ellipsis`.

---

## [1.2.8] - 2026-02-09

### Fixed

- **Unicode “question marks” in notes** — Note titles and content that contained invisible/special Unicode (e.g. BOM U+FEFF or zero-width spaces from pasting) no longer show as “?” at the start or end. These characters are stripped when loading and when setting decrypted content.

### Technical

- `util/Format.kt`: `stripInvisibleFromEdges()` removes BOM and zero-width chars (U+200B, U+200C, U+200D, U+2060) from the start and end of strings.
- NoteCard: title and content snippet use stripped text for display.
- NoteDetailScreen: title and content stripped when loading from note; decrypted content stripped before storing in session and displaying.
- FormatTest: unit test for `stripInvisibleFromEdges`.

---

## [1.2.7] - 2026-02-09

### Added

- **Markdown links** — Links in note content open in the browser when tapped.
- **Deep link feedback** — Opening the app via `jotty-android://open/note/{id}` for a missing or deleted note shows a “Note not found” snackbar.
- **Content descriptions** — Icon buttons (refresh, add, share, decrypt, encrypt, edit, save) use descriptive content descriptions for TalkBack.
- **Unit test** — `FormatTest` for `formatNoteDate` (util).

### Changed

- **Notes search** — Search input is debounced (400 ms) so the API is not called on every keystroke.
- **Note image loader** — Coil ImageLoader is cached per (baseUrl, apiKey) and built with application context so it survives config changes.
- **Decrypted notes** — `NoteDecryptionSession` is cleared when the app goes to background (onStop) so decrypted content is not kept in memory longer than needed.

### Technical

- Notes UI split into separate files: `NoteCard.kt`, `NoteView.kt`, `NoteEditor.kt`, `NoteDialogs.kt`, `NoteDetailScreen.kt`; `NotesScreen.kt` retains list and create flow.
- `formatNoteDate` moved to `util/Format.kt`.
- NotesScreen: debounced search state; LaunchedEffect for deep-link “note not found” when list loaded and id not in list.
- NoteView: `onLinkClicked` → `LocalUriHandler.openUri`.
- NoteImageLoader: `ConcurrentHashMap` cache keyed by normalized base URL and API key; `applicationContext` for loader.
- JottyApp: `ProcessLifecycleOwner` observer clears `NoteDecryptionSession` in `onStop`.
- AGENTS.md: “Images in notes” subsection; “Where to change what” updated for notes (list vs detail/dialogs).
- strings.xml: `note_not_found`, `cd_refresh`, `cd_add`, `cd_share`, `cd_decrypt`, `cd_encrypt`, `cd_edit`, `cd_save`.

---

## [1.2.6] - 2026-02-08

### Fixed

- **Note decryption (Jotty web)** — Inner 12-byte nonce for XChaCha20-Poly1305 now matches libsodium: bytes 0–3 zero, bytes 4–11 = last 8 bytes of the 24-byte nonce. Previously the layout was reversed, causing "Auth failed (wrong passphrase or tag mismatch)" even with the correct passphrase for notes encrypted in the browser.

### Added

- **Images in notes** — Markdown images `![alt](url)` in note content are now loaded and displayed. A Coil ImageLoader is passed to the markdown renderer; image URLs on the same host as your Jotty server receive the API key so server-hosted images load. External URLs and data URIs also work. The note editor hint now mentions images.

### Technical

- XChaCha20Decryptor: nonce12 built as npub2[0..3]=0, npub2[4..11]=nonce24[16..23]; legacy app nonce layout tried as fallback when decrypting.
- XChaCha20Encryptor: same libsodium nonce layout for new encryptions.
- util/NoteImageLoader.kt: `createNoteImageLoader(context, baseUrl, apiKey)` builds a Coil ImageLoader that adds `x-api-key` for requests to the Jotty host.
- MainScreen: creates note image loader, passes it to NotesScreen.
- NotesScreen / NoteView: pass imageLoader into MarkdownText.
- Coil dependency (2.6.0); strings.xml: markdown_hint includes `![images](url)`.

---

## [1.2.5] - 2026-02-08

### Fixed

- **Note decryption (web compatibility)** — Decryption now tries multiple Argon2 presets (iterations/memory/parallelism) so notes encrypted with different Jotty web settings are more likely to decrypt. Nonces longer than 24 bytes (e.g. 36 from web) use first or last 24 bytes. Tag order: libsodium (tag then ciphertext) is tried first, then BC order. Passphrase is tried trimmed and untrimmed. Empty passphrase returns a clear key-derivation failure. Encrypted body is stripped of markdown code fences before parsing. OOM and invalid UTF-8 during decrypt are handled without crashing.
- **Note encryption (web compatibility)** — Encryptor now outputs libsodium secretbox format (tag then ciphertext) so notes encrypted in the app decrypt correctly in the Jotty web app.

### Added

- **Decrypt dialog** — When decryption fails with an auth error, the dialog shows a short hint: use the exact same passphrase and check for leading/trailing spaces (especially for notes encrypted in the web app). When Settings → Debug logging is on, the specific failure reason (parse, key derivation, or auth) is shown below the main message.
- **Documentation** — README: Troubleshooting section (server SSL wrong version, XChaCha `from_hex` error); Encryption section (XChaCha20-Poly1305 supported, PGP web-only, limitations). AGENTS.md: Encryption (Jotty) section describing both methods and limitations for contributors.

### Technical

- XChaCha20Decryptor: Argon2 presets list; full nonce with 24-byte candidates; `decryptWithReason`/`DecryptResult`; try libsodium order then BC; passphrase variants; code-fence stripping; OOM and exception handling; empty passphrase check.
- XChaCha20Encryptor: reorder output to tag then ciphertext for libsodium compatibility.
- NotesScreen: auth-failed hint and failure-reason detail in Decrypt dialog.
- strings.xml: `decrypt_auth_failed_hint`.
- XChaCha20EncryptorTest: libsodium format and BC-order backward compatibility.

---

## [1.2.4] - 2026-02-07

### Fixed

- **Note decryption (Jotty web)** — Notes encrypted in the Jotty web app now decrypt correctly. The web app uses libsodium secretbox format (tag then ciphertext); the app now tries both tag orderings (BC/IETF: ciphertext then tag; libsodium: tag then ciphertext) so both Android-encrypted and web-encrypted notes work.

### Added

- **Decrypt dialog debug info** — When Settings → Debug logging is on, a failed decryption shows the specific reason in the dialog (e.g. "Parse failed", "Key derivation failed", "Auth failed") below the main message, so you can see why it failed without using logcat.

### Technical

- XChaCha20Decryptor: `DecryptResult` and `decryptWithReason()`; try BC order then libsodium order for ciphertext/tag; `tryDecrypt()` helper; failure reason constants for UI.
- NotesScreen: `decryptWithReason()` in Decrypt dialog; pass `debugLoggingEnabled` into NoteDetailScreen and DecryptNoteDialog; show `decryptErrorDetail` when debug is on.
- Unit test: decrypt accepts libsodium secretbox format (tag then ciphertext).

---

## [1.2.3] - 2026-02-07

### Added

- **Jotty server version in Settings** — Dashboard overview now shows the Jotty server version (from `api/health`) when available, in the same section as the dashboard summary and admin overview.

### Fixed

- **Debug logging** — Debug setting is now synced from MainActivity via `collectAsState` and `LaunchedEffect`, so toggling "Debug logging" in Settings correctly enables or disables `AppLog.d()` output in logcat.
- **Decrypt / encrypted notes** — Decrypt dialog captures passphrase at tap time and runs success/error callbacks on the main dispatcher; encrypted body detection is more lenient (alg value may contain "xchacha" e.g. "XChaCha20-Poly1305", body-only JSON accepted when it has `"data"` and salt/nonce; frontmatter body trimmed). Always-on logcat messages (tag `Jotty/encryption`) for decrypt attempts and failures so you can diagnose issues without enabling Debug logging.

### Technical

- MainActivity: `debugLoggingEnabled` collected with `collectAsState`, `LaunchedEffect(debugLoggingEnabled)` syncs to AppLog; JottyAppContent no longer collects debug flow.
- SettingsScreen: `serverVersion` from health response, shown in Dashboard overview when present.
- XChaCha20Decryptor: `Log.i`/`Log.w` for attempt, parse step details, key/auth failure; parse step logs (GSON null, missing salt/nonce/data, base64 failure, size checks).
- NoteEncryption: body-only regex matches alg containing "xchacha"; body trimmed after frontmatter; fallback for JSON with `"data"` and salt/nonce.
- NotesScreen: decrypt uses `withContext(Dispatchers.Main)` for callbacks; passphrase captured before launch; LaunchedEffect logs note detail when encrypted.

---

## [1.2.2] - 2026-02-07

### Fixed

- **Debug logging** — Logging now works reliably: flag is initialized at app startup from the persisted preference; enabling debug writes an INFO-level confirmation to logcat so you can verify it took effect; Settings description includes logcat filter hint (filter by `Jotty`).
- **Decryption diagnostics** — When debug logging is enabled, decryption failures now log clearer messages (parse failed, key derivation failed, auth failed) to help troubleshoot "Wrong passphrase or invalid format".

### Technical

- JottyApp: early `debugLoggingEnabled` sync in `onCreate()` via ProcessLifecycleOwner.
- AppLog: `setDebugEnabled(true)` logs an INFO message confirming debug is on.
- XChaCha20Decryptor: refined debug failure messages for parse / key derivation / auth steps.

---

## [1.2.1] - 2026-02-07

### Added

- **Debug logging** — Settings → General → "Debug logging": when enabled, extra details (e.g. decryption failure step: parse, key derivation, or auth) are written to logcat for troubleshooting.
- **Accessibility** — Meaningful content descriptions for icons (Search, Link, Encrypt, Decrypt, Disconnect, About, etc.) so TalkBack and other assistive technologies announce them correctly.
- **Encrypt/decrypt loading** — Encrypt and decrypt run on a background thread so the UI stays responsive; dialogs show a spinner and disable inputs while the operation runs.

### Fixed

- **Note decryption** — Base64 decoding now strips whitespace and adds padding when needed; leading BOM in the encrypted body is trimmed so more payloads (including from Jotty web) decrypt correctly.
- **Deprecations** — Replaced deprecated `Icons.Filled.ArrowBack`, `Note`, `Logout` with AutoMirrored variants; SwipeToDismissBox no longer uses deprecated `confirmValueChange` (replaced with `LaunchedEffect` on state).

### Changed

- **Encrypt dialog** — If encryption fails (e.g. returns null), the dialog shows "Encryption failed. Please try again." instead of doing nothing.
- **Settings chips** — Theme mode, theme color, content padding, and start screen chips use FlowRow so they wrap on small screens.
- **Empty state** — Empty-list icon uses the screen title as content description for accessibility.

### Technical

- SettingsRepository: `debugLoggingEnabled`, `setDebugLoggingEnabled`; KEY_DEBUG_LOGGING.
- AppLog: `isDebugEnabled()`, `setDebugEnabled()`; `d()` only logs when debug is enabled.
- XChaCha20Decryptor: step-wise failure logging (parse / key derivation / auth); BOM trim; base64 whitespace strip and padding.
- JottyAppContent: collects `debugLoggingEnabled` and syncs to AppLog.
- NotesScreen: decrypt in `Dispatchers.Default` with loading state; encrypt in `Dispatchers.Default` with `encryptError` state; EncryptNoteDialog accepts `encryptError`, `isEncrypting`.
- Unit tests: encrypt/decrypt round-trip, wrong passphrase, trimmed passphrase, URL-safe base64 decrypt.
- AGENTS.md: debug logging, background encrypt/decrypt, "Where to change what" for logging.

---

## [1.2.0] - 2026-02-07

### Added

- **Content padding setting** — General → "Content padding": Comfortable (16dp vertical) or Compact (8dp). Applied to Checklists, Notes, Settings, and Setup so the app uses screen height the way you prefer.

### Fixed

- **Note decryption** — "Invalid password" when the passphrase was correct: encrypted body is now parsed with Gson (handles key order and formatting), base64 decoding supports both standard and URL-safe (e.g. from Jotty web), and passphrase is trimmed on encrypt and decrypt so accidental spaces no longer cause failure.

### Changed

- **Screen padding** — Default vertical padding reduced; use Settings → Content padding to choose Comfortable or Compact.
- **Encrypt/decrypt** — Passphrase is trimmed when encrypting and decrypting for consistent behavior.

### Technical

- SettingsRepository: `contentPaddingMode`, `setContentPaddingMode`; KEY_CONTENT_PADDING.
- ChecklistsScreen, NotesScreen, SetupScreen: take or use `settingsRepository` for content padding.
- XChaCha20Decryptor: Gson for JSON, `decodeBase64()` for URL-safe base64, passphrase trim; EncryptedBodyJson data class.
- XChaCha20Encryptor: passphrase trim and empty check.

---

## [1.1.7] - 2026-02-07

### Added

- **Settings pull-to-refresh** — Swipe down on Settings to refresh connection status, dashboard summary, and admin overview.
- **Theme: mode + color** — Separate "Light / Dark" (System, Light, Dark) and "Color" (Default, AMOLED, Sepia, Midnight, Rose, Ocean, Forest). Combinations like "Forest Dark" or "Rose Light". Dark variants added for Rose, Ocean, Forest. Legacy single-theme preference is migrated on first launch.
- **Back from detail** — System back button from checklist or note detail returns to the list instead of exiting the app.

### Changed

- **Checklist overview** — Delete is no longer on the list row or swipe; long-press on the checklist title opens a menu with Edit (open) and Delete.
- **JottyTheme** — Now takes `themeMode: String?` and `themeColor: String`; status bar follows resolved dark/light.

### Technical

- SettingsRepository: `themeMode`, `themeColor`, `setThemeMode`, `setThemeColor`; `migrateThemeToModeAndColorIfNeeded()`.
- Checklist list: SwipeToDeleteContainer removed; ChecklistCard has long-press menu and `onDelete`.

---

## [1.1.6] - 2026-02-07

### Added

- **Theme options** — Nine themes: System, Light, Dark, AMOLED, Sepia, Midnight, Rose, Ocean, Forest. Theme chips wrap on small screens (FlowRow).
- **Settings categories** — Settings reorganized into Overview (connection + dashboard), General (theme, start screen, swipe to delete), Account, About. Optional "Dashboard Overview" subtitle when summary/admin data is available.

### Changed

- **JottyTheme** — Now takes `themePreference: String?` instead of `darkTheme: Boolean`; status bar follows resolved scheme for all theme variants.

---

## [1.1.5] - 2026-02-07

### Fixed

- **Markdown code in dark mode** — Inline and block code in markdown (e.g. in About release notes and in note content) now use theme-aware colors so code text stays readable in dark mode.

### Changed

- **DRY** — Centralized API category constant for "Uncategorized" in `data/api/models.kt`; note list uses it when hiding the category chip.

---

## [1.1.4] - 2026-02-07

### Added

- **ApiErrorHelper** — Shared mapping of exceptions to user-friendly messages (no internet, timeout, server error, etc.) via string resources; used for list load errors, setup connection, and update check/install.
- **ListScreenContent** — Reusable composable in `ui/common/` for loading / error / empty / pull-to-refresh list layout (used by Notes and Checklists).
- **Unit tests** — `ApiClientTest` (URL normalization), `ApiErrorHelperTest` (exception → string resource mapping).

### Changed

- **Null safety** — Removed last `!!` in `SettingsRepository.setDefaultInstanceId`.
- **Error messages** — All API/network errors now use string resources and consistent messages (e.g. "No internet connection", "Something went wrong") instead of raw exception text.
- **Update checker** — `checkForUpdate(context)` now takes `Context` for localized error strings; "Unknown error" / "Install failed" moved to strings.xml.
- **Snackbar on failures** — Create/update/delete for notes and checklists (and checklist items) now show a Snackbar on failure instead of failing silently.

### Technical

- New string resources: `unknown_error`, `no_internet_connection`, `connection_timed_out`, `network_error`, `server_error`, `request_failed`, `save_failed`, `delete_failed`, `install_failed_fallback`, `no_apk_in_release`.
- `ApiClient.normalizeBaseUrl()` extracted for testability.
- **Dependencies** — Bumped to latest stable: Kotlin 2.2.21, Compose BOM 2025.12.00, Material3 (from BOM), lifecycle 2.8.7, activity-compose 1.11.0, navigation-compose 2.9.7, Retrofit 2.12.0, Bouncy Castle 1.79; removed redundant material3 version pin.
- **Code** — Removed redundant import in SettingsScreen.

---

## [1.1.3] - 2026-02-07

### Added

- **Check for updates** — Settings → About → "Check for updates" compares the app version with the latest GitHub release.
- **In-app update** — When a newer version is available, "Download and install" downloads the APK and opens the system installer (no need to leave the app).
- **Download progress** — Progress bar during APK download (determinate when size is known, indeterminate otherwise).
- **Release notes** — "What's new" section in the update dialog shows the GitHub release notes (markdown).
- **Install fallback** — If download or install fails, the dialog shows the error and an "Open release page" button to install from the browser.
- **Update-check caching** — Successful check results are cached for 5 minutes; errors are not cached so Retry always hits the API.

### Changed

- **User-friendly update errors** — Update check and install failures show short messages (e.g. "No internet connection", "Connection timed out") instead of raw errors.

### Technical

- **GitHub API** — User-Agent header; separate HTTP client for downloads with longer read timeout.
- **Install intent** — `FLAG_ACTIVITY_CLEAR_TOP` and `FLAG_ACTIVITY_NO_HISTORY` for more reliable install flow on Android 7+.

---

## [1.1.2] - 2026-02-07

### Added

- **Pull-to-refresh** — Swipe down on Checklists and Notes list screens to refresh (in addition to the refresh button).
- **Manage instances without disconnecting** — Settings → "Manage instances" opens the instance list to add, edit, or remove Jotty servers while staying connected to the current one.

### Changed

- **Swipe-to-delete disabled by default** — Swipe-to-delete for checklists and notes is off by default; enable it in Settings → Appearance.

---

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

[1.3.3]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.3.3
[1.3.2]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.3.2
[1.3.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.3.1
[1.3.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.3.0
[1.2.9]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.9
[1.2.8]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.8
[1.2.7]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.7
[1.2.6]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.6
[1.2.5]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.5
[1.2.4]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.4
[1.2.3]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.3
[1.2.2]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.2
[1.2.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.1
[1.2.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.0
[1.1.7]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.7
[1.1.6]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.6
[1.1.5]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.5
[1.1.4]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.4
[1.1.3]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.3
[1.1.2]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.2
[1.1.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.1
[1.1.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.0
[1.0.2-1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.2-1
[1.0.2]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.2
[1.0.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.1
[1.0.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.0

[1.3.4]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.3.4

[1.3.5]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.3.5

[1.3.6]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.3.6

[1.4.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.4.0

[1.5.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.5.0

[1.5.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.5.1

[1.5.2]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.5.2
