# Checklist item reordering (Android)

## Status

**Supported** since Jotty **1.25.0** (endpoints from [PR #523](https://github.com/fccview/jotty/pull/523)), which is the app's minimum supported server version:

| Feature | Jotty 1.25.0+ |
|---------|---------------|
| `GET /api/search` | Relevance-ranked search (falls back to `GET /api/notes?q=` / local filter if unavailable) |
| `PUT …/items/reorder` | Up/down reorder buttons |
| `PATCH …/items/{index}` | Inline rename of any item, including parents and nested paths like `0.1` |

The Android app uses:

- `PUT /api/checklists/{listId}/items/reorder` — move items before/after siblings or nest as a child (`isDropInto`)
- `PATCH /api/checklists/{listId}/items/{itemIndex}` — update item text (including nested paths like `0.1`)

Track UI polish in [#29](https://github.com/Darknetzz/jotty-android/issues/29).

## Android behaviour

- **Reorder:** Each checklist row shows a **drag handle** (and **move up** / **move down** in the overflow menu) when the item has a stable server `id`. Drag-and-drop and up/down only reorder among **siblings** (same parent); To Do and Completed are separate lists. Turn off the drag handle in Settings → Behavior → **Drag to reorder checklists** (menu reorder still works). Offline edits queue a `REORDER` pending op replayed on sync.
- **Rename:** Tap item text to edit inline; saves via PATCH (no delete-and-recreate).
- **Sync:** Order changed in the web app still syncs on pull (`GET /api/checklists`).

## Server contract

See [fccview/jotty `public/api/paths/checklists.yaml`](https://github.com/fccview/jotty/blob/develop/public/api/paths/checklists.yaml) and [`search.yaml`](https://github.com/fccview/jotty/blob/develop/public/api/paths/search.yaml).

Reorder body (JSON):

```json
{
  "activeItemId": "item-being-moved",
  "overItemId": "reference-item",
  "position": "before",
  "isDropInto": false
}
```

Semantics match the web UI server action [`reorder.ts`](https://github.com/fccview/jotty/blob/main/app/_server/actions/checklist-item/reorder.ts).

## Older / stable servers

The app requires Jotty **1.25.0** or newer. Connecting an instance whose `GET /api/health` reports an older version is refused with an error; a missing or unparsable version does not block the connection.

## References

- Android issue: [#29](https://github.com/Darknetzz/jotty-android/issues/29)
- Historical API proposal: [upstream/CHECKLIST_REORDER_API_PROPOSAL.md](upstream/CHECKLIST_REORDER_API_PROPOSAL.md)
- Jotty reorder implementation: [`app/_server/actions/checklist-item/reorder.ts`](https://github.com/fccview/jotty/blob/main/app/_server/actions/checklist-item/reorder.ts)
