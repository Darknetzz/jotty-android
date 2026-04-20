# Changelog

All notable changes to Jotty Android are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- **Clearer API error messages** — HTTP **401** and **403** map to dedicated strings (invalid API key / access denied). **SSL/TLS** failures map to a short message about HTTPS and certificates. (`ApiErrorHelper`, `strings.xml`, tests.)
- **Offline repository tests** — JVM tests with in-memory Room and a fake `JottyApi` cover sync, server replace, and conflict “(Local copy)” behavior. (`FakeJottyApi`, `OfflineNotesRepositoryTest`; Robolectric + test dependencies.)
- **CI** — GitHub Actions workflow runs `./gradlew test` and `./gradlew lint` on push/PR (JDK 17 + Android SDK setup).
- **UI smoke test** — Instrumented test checks that `MainActivity` shows a Compose root (`MainActivitySmokeTest`).
- **`build.sh`** — Linux build script mirroring `build.ps1`: Gradle wrapper bootstrap (version read from `gradle-wrapper.properties`), Java 11+ discovery (`java` on `PATH`, `/usr/lib/jvm/*`, `/opt/android-studio/jbr`, etc.), Android SDK detection (`ANDROID_HOME`, `~/Android/Sdk`, `/opt/...`, scan for `platform-tools` under `/opt`), copies debug/release APK to `jotty-android.apk`.
- **`local.properties.example`** — Documents `sdk.dir` and typical Studio vs SDK paths (including IDE under `/opt`).

### Changed

- **Checklists & offline notes UI** — `ChecklistsViewModel` and `OfflineEnabledNotesViewModel` hold list/filter/selection state with `StateFlow`; screens collect via `viewModel { … }`. Notes search debouncing uses `Flow.debounce` with no delay for blank queries.
- **README** — Build requirements list Android SDK **36** to match `compileSdk` / `targetSdk`.

### Fixed

- **Layout height** — App content now uses the full height between the top and bottom bars; the root AnimatedContent and main NavHost use `fillMaxSize()` so there is no extra margin above or below the content area.
- **Offline sync coroutine scope** — `OfflineNotesRepository` uses `SupervisorJob` + `CoroutineExceptionHandler` so background work is not torn down by a single failure. Optional `initialOnlineOverride` and `registerNetworkCallback` support unit tests without `ConnectivityManager`.
- **`init` block** — Replaced invalid `return@init` with `if (registerNetworkCallback) { … }` so Kotlin compiles cleanly.

### Technical

- `gradle.properties` / wrapper: build scripts download `gradle-wrapper.jar` matching the version in `gradle-wrapper.properties` (e.g. 9.1.0) and validate the JAR manifest; `build.ps1` updated the same way.
- `app/build.gradle.kts`: `testOptions.unitTests.isIncludeAndroidResources`, Room testing, Robolectric, coroutines-test; `androidTest` deps + `testInstrumentationRunner` for Compose UI tests.
- `OfflineNotesRepository.kt`: constructor parameters for tests only (defaults unchanged for app use).

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

[1.2.9]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.9
[1.2.4]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.4
[1.2.3]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.2.3
[1.1.2]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.2
[1.1.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.1
[1.1.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.1.0
[1.0.2-1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.2-1
[1.0.2]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.2
[1.0.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.1
[1.0.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.0
