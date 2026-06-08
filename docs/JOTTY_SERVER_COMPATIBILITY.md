# Jotty server compatibility (Android client)

This document describes how **jotty-android** behaves against different Jotty server versions. The web app on [Jotty](https://jotty.page/) evolves on `main` and tagged releases; the Android app targets the public REST API.

## Checklist item rename (PATCH)

| Capability | Android behavior |
|------------|------------------|
| `PATCH /api/checklists/{id}/items/{index}` | Preferred: in-place text update via [`updateChecklistItemText`](../app/src/main/java/com/jotty/android/util/ChecklistItemUpdate.kt). |
| PATCH missing (HTTP 404/405) | Falls back to **delete + recreate** for **leaf** items only. |
| Parent / project row with children | Rename throws `UnsupportedOperationException`; user sees **rename_leaf_only** and a dismissible banner when PATCH is unavailable. |

Older stable Jotty installs without PATCH still work for leaf renames; parent renames need a server with PATCH support (Jotty develop / upcoming release).

## Project / Kanban boards

| Capability | Android behavior |
|------------|------------------|
| `GET/POST/PUT/DELETE /api/tasks/{taskId}/statuses` | **Manage statuses** dialog; kanban columns from server statuses. |
| Status API 404/405 | Kanban board hidden; checklist detail uses **list/tree** UI (`kanban_not_supported_fallback`). |
| `PUT /api/tasks/{taskId}/items/{index}/status` | Move card between columns (online; offline shows move hint). |
| `DELETE /api/checklists/{id}/items/{index}` | Delete task from kanban card menu or list rows. |
| Tap Kanban card | Opens **item detail** (title, description, subtasks, status); see Kanban item fields below. |

## Kanban item fields

| Field | REST GET | REST PATCH | Android behavior |
|-------|----------|------------|------------------|
| Title (`text`) | Yes | Yes | Editable in item detail |
| Description | **No** (write-only PATCH) | Yes | Editable; banner when server does not return saved text |
| Subtasks (`children`) | Yes | Via item CRUD + check/uncheck | Full CRUD in item detail |
| Status (column) | Yes | `PUT /api/tasks/…/items/…/status` | Status picker in detail (online) |
| Priority, score, target date, estimated time | **No** | **No** | Disabled placeholders until upstream ships ([proposal](upstream/KANBAN_ITEM_FIELDS_API_PROPOSAL.md)) |
| Item metadata (created/modified, status history) | **No** | **No** | Disabled placeholder until upstream ships |

See [upstream/KANBAN_ITEM_FIELDS_API_PROPOSAL.md](upstream/KANBAN_ITEM_FIELDS_API_PROPOSAL.md) for the requested Jotty server expansion.

## Notes

- Encrypted notes (XChaCha20) match the Jotty web format; PGP notes are view-only in-app.
- Note images: relative URLs such as `/api/image/...` are resolved against the instance base URL (RFC 3986) before Markdown render; HTML `<img src>` attributes are rewritten too. Absolute Jotty media URLs that use a different host than the configured instance (e.g. LAN IP vs hostname) are rewritten to the instance origin.
- **Image authentication:** Jotty’s `/api/image/` and `/api/file/` routes authenticate via **browser session cookies**, not `x-api-key`. The Android app sends `x-api-key` on same-origin media requests, but **standard Jotty server versions return HTTP 401** for private images unless `SERVE_PUBLIC_IMAGES=yes` is set on the server. Notes/checklists API calls are unaffected. Upstream Jotty would need API-key support on media routes for private images to load in-app without that env var. When image auth fails, note detail shows a dismissible banner (same condition as above).
- **Archive (notes/checklists):** Jotty web moves items to category **`Archive`** (`ARCHIVED_DIR_NAME`). The Android app archives via `PUT /api/notes/{id}` or `PUT /api/checklists/{id}` with `category: "Archive"`. Archived items are hidden from the default list and shown under the **Archived** filter chip.
- **Save with HTML/images:** The app saves note body as written (no HTML→Markdown conversion on write). Display-only conversion runs when viewing.

## Sharing

| Capability | Jotty web | Jotty OpenAPI (`public/api/paths`) | Android behavior |
|------------|-----------|-------------------------------------|------------------|
| Share with users / public link | Server Actions (`app/_server/actions/sharing/`) | **Not documented** (no `sharing.yaml` in OpenAPI as of Jotty `main`) | Probes `GET /api/sharing/items/{type}/{id}`; on 404 shows export fallback + web-app hint |
| Text export | N/A | N/A | Checklists/Kanban: plain-text export via Share; notes: existing Share action |

When Jotty adds REST sharing endpoints, the client uses [`ShareServerDialog`](../app/src/main/java/com/jotty/android/ui/common/ShareServerDialog.kt) and [`JottySharingProbe`](../app/src/main/java/com/jotty/android/util/JottySharingProbe.kt).

## Kanban item archive

Kanban **item** archive/unarchive in the web app uses server actions (`archiveItem` / `unarchiveItem` on checklist items). There is **no REST equivalent** in OpenAPI today. Android does not expose per-item archive until upstream adds PATCH fields (e.g. `isArchived`) or a dedicated endpoint.

## In-app signals

When PATCH is unavailable for an instance, the app sets a per-instance flag and may show **server_patch_limited_banner** on checklist detail until dismissed.

## References

- [Jotty API](https://github.com/fccview/jotty/blob/main/howto/API.md)
- [CHECKLIST_REORDER.md](CHECKLIST_REORDER.md)
