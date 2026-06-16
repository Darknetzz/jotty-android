# Project todo / follow-ups

Prioritized backlog after v1.3.5. See also [CHANGELOG.md](../CHANGELOG.md).

## Quick polish

- [x] Per-note pending-sync badge on list cards (`NoteEntity.isDirty`)
- [x] Explicit offline message when opening an encrypted note that was never decrypted online
- [x] Snackbar when an offline category filter chip is unavailable until sync

## Quality / architecture

- [x] `SettingsViewModel` and `SetupViewModel` (detail/settings logic still in composables)
- [ ] Shared offline list scaffold to reduce duplication between notes and checklists screens
- [ ] Extract shared conflict-detection helper in offline repositories
- [x] `ChecklistDetailViewModel` for online checklist detail

## Features (blocked or larger)

- [x] Checklist item reorder ([#29](https://github.com/Darknetzz/jotty-android/issues/29)) — Jotty REST `PUT …/items/reorder`; see [CHECKLIST_REORDER.md](CHECKLIST_REORDER.md)
- [ ] Background sync via WorkManager
- [ ] Advanced conflict merge UI (side-by-side); see [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md)
- [ ] i18n (`values-*` locales)

## Done recently (1.3.5 / this pass)

- [x] Checklist reorder limitation banner in detail UI
- [x] Checklist conflict copies banner (parity with notes)
- [x] `NoteDetailViewModel` + `NoteDetailActions` (no fake `JottyApi` in offline detail)
- [x] Checklist conflict repository tests
- [x] ViewModel unit tests for list screens
- [x] `NotesListDataSource` / `ChecklistsListDataSource` repository interfaces
