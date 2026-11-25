# Local storage and sync

With **Settings → Behavior → Local storage & sync** on (the default), notes and checklists are kept in a Room database on the device. You can read and edit them without a connection; changes are sent to the Jotty server when it can be reached again. With the setting off, every screen reads and writes the server directly and nothing is stored locally.

This page covers how that works: when the app counts as online, how notes and checklists sync, what happens on a conflict, and how to inspect or undo pending changes.

## Online, offline and "server unreachable"

The app counts as **online** only when the device has a network **and** the Jotty server answered the last request (`NetworkConnectivityMonitor.isOnline`). A phone on mobile data with a home-network-only server has a network but no server, so it is treated as offline ([#115](https://github.com/Darknetzz/jotty-android/issues/115)).

- When a request gets no answer at all (DNS failure, timeout, connection refused: an `IOException`, see `Throwable.isServerUnreachable()` in `util/NetworkFailure.kt`), the repositories keep the change locally, mark the server unreachable, and the app switches to offline behaviour.
- An HTTP error (4xx/5xx) means the server answered, so the app stays online and the operation fails normally.
- While unreachable, the app probes `GET api/health` with backoff (15 s, 30 s, 1 min, 2 min, then every 5 min). A network change, the app coming to the foreground, or a sync request probes at once.
- New offline write paths must follow the same rule: on an unreachable server, save locally instead of failing the save.

### Sync status icon

The cloud icon in the top bar of the Notes and Checklists tabs shows the state; tap it for **Sync details**.

| Icon | Meaning |
|------|---------|
| Cloud with check (`CloudDone`) | Online |
| Cloud outline (`CloudQueue`) | Sync in progress |
| Crossed-out cloud (`CloudOff`) | Offline or server unreachable |

The icon is green normally and red when offline or when the last sync failed. While offline, saves show a **Saved locally** snackbar and list cards show a **Pending sync** label.

## When sync runs

- When the app comes back online (network restored, or the server answers a probe).
- On pull-to-refresh or the refresh action in a list.
- After local edits, once no item edit is still in progress (quick repeated taps are batched).
- In the background every 6 hours via WorkManager (`OfflineSyncWorker`, network required), for each saved instance.

UI-triggered and background syncs share one per-instance lock (`OfflineSyncLocks`), so an item created offline is never posted twice.

## Notes

1. **Push:** each changed note (`NoteEntity.isDirty`) is created, updated or deleted on the server. A note created offline has a temporary local id that is replaced by the server id.
2. **Pull:** the full note list is fetched and replaces the local copy.
3. **Conflicts:** if a note was changed locally and its title, content or category differs from the server after the push, the server version is kept and your version is saved as a separate note titled `… (Local copy)`. A snackbar reports the number of conflicts and offers **View copies**.

Related on-device safety nets:

- **Drafts** (`NoteDraftStore`): edits to an unencrypted note are autosaved while you type, until you save or discard. Opening a note with a draft offers to restore it. Plaintext of encrypted notes is never written there.
- **Note backups** (`NoteSnapshotRepository`): before a save overwrites a note, the previous version is kept on the device (last 5 per note) and can be restored from the note's menu (**Restore backup**).
- **Encrypted notes** can be read offline only if they were decrypted while online earlier in the session.

## Checklists

Checklist metadata (title, category) syncs like a note. **Item changes** work differently: each one is recorded as a pending operation on the checklist (`ChecklistEntity.pendingOpsJson`, type `PendingItemOp`), applied to the local copy straight away, and replayed against the REST API on the next sync.

| Op | Created by | Replayed as |
|----|-----------|-------------|
| `ADD` | Adding an item (with parent and Kanban status for task projects) | `POST …/items` |
| `CHECK` / `UNCHECK` | Ticking an item; bulk **Check all** / **Uncheck all** | `PUT …/items/{path}/check` / `uncheck` |
| `UPDATE` | Renaming an item; editing Kanban item fields | `PATCH …/items/{path}` |
| `DELETE` | Deleting an item; bulk **Clear all** | `DELETE …/items/{path}` |
| `REORDER` | Drag or up/down reorder | `PUT …/items/reorder` |

Paths are positional (`0`, `2.1`, …), so the order of the queue matters:

- **Every op is kept, in the order it was made.** Identical ops are not merged: *check, uncheck, check* or *delete 0, delete 0* are legitimate sequences ([#120](https://github.com/Darknetzz/jotty-android/issues/120)).
- **Before each op** the app compares it with the server's current items. An op the server already reflects (item already checked, already gone) is skipped.
- **After a failed op** it is still dropped if the server now reflects it, if it was a `DELETE` that returned 404, or if its path no longer exists on the server (stale). Anything else stays queued and counts as a replay failure, shown in Sync details.
- **If any checklist still has unsent changes after the push, the pull is skipped**, so the server snapshot never overwrites edits that have not been sent. The sync message says how many checklists were kept local.
- Online with an empty queue, item changes go straight to the server. If a checklist already has queued ops, new changes are queued behind them so the order is kept. If the server becomes unreachable part-way through a bulk action, the ops it already applied are mirrored locally and the rest are queued.

**Conflicts:** queued item ops are applied on top of the server's current items, so remote edits and yours are merged and differing items alone are not a conflict. The server version is kept and your pre-sync version is saved as a `… (Local copy)` checklist (as for notes) only when:

- your title or category change is not on the server after the push;
- a queued rename or field edit (`UPDATE`) could not be applied because the item was deleted on the server (a check or delete of a vanished item loses nothing and is just dropped);
- the checklist had no queued item ops and its items differ from the server's.

From v1.3.3 to v1.11.2, any queued op counted as a conflict, so most offline item edits produced a spurious copy ([#126](https://github.com/Darknetzz/jotty-android/issues/126)).

## Pending sync manager

With local storage on, **Manage pending** (from Sync details, or Settings → Behavior) lists every note and checklist with unsent changes. For each one you can:

- compare the local and server versions (text diff);
- **push local** (overwrite the server with your version) or **pull server** (discard your changes);
- restore an automatic **sync backup**: before either action, the version being overwritten is saved on the device (last 10 per item, `SyncBackupRepository`).

## Limitations

- Conflicts keep both versions; there is no side-by-side merge.
- Categories created offline do not appear in the category filter until synced.
- Turning local storage off does not send pending changes first, and there is no warning. They stay on the device until local storage is turned back on and a sync runs, so sync (or use Manage pending) before turning it off.
- Clearing the app's data in Android settings deletes pending changes.

## Testing by hand

1. With local storage on, add, tick and delete checklist items and edit a note, then turn on airplane mode.
2. Make more changes offline. Each shows **Saved locally**, and list cards show **Pending sync**.
3. Turn airplane mode off. The icon shows syncing, then the check. Confirm the changes in the Jotty web app.
4. Unreachable server: use a server address that only resolves on your home network and leave it (or block the host). The app should go offline after one failed request and recover within seconds of the server answering again.
5. Conflict: edit a note offline, edit the same note in the web app, reconnect. A `(Local copy)` note and the conflict snackbar appear.

## Code map

| Area | Files under `app/src/main/java/com/jotty/android/` |
|------|------|
| Online state | `data/local/NetworkConnectivityMonitor.kt`, `data/local/OfflineRepositoryRuntime.kt`, `util/NetworkFailure.kt` |
| Notes sync | `data/local/OfflineNotesRepository.kt`, `NoteEntity.kt`, `NoteDao.kt` |
| Checklist sync and op queue | `data/local/OfflineChecklistsRepository.kt`, `ChecklistEntity.kt` (`PendingItemOp`), `util/ChecklistItemReplay.kt`, `util/ChecklistBulkActions.kt` |
| Background sync, locks | `data/local/OfflineSyncWorker.kt`, `OfflineSyncLocks.kt`, `OfflineInitialSync.kt` |
| Drafts and backups | `data/local/NoteDraftStore.kt`, `NoteSnapshotRepository.kt`, `SyncBackupRepository.kt` |
| Database | `data/local/JottyDatabase.kt` |
| UI | `ui/common/MainTabTopBar.kt` (status icon), `ui/notes/Offline*`, `ui/checklists/OfflineEnabledChecklistsScreen.kt`, `ui/sync/` (pending sync manager) |
