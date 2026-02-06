# Changelog

All notable changes to Jotty Android are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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

[1.0.1]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.1
[1.0.0]: https://github.com/Darknetzz/jotty-android/releases/tag/v1.0.0
