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

## Notes

- Encrypted notes (XChaCha20) match the Jotty web format; PGP notes are view-only in-app.
- Note images: relative URLs such as `/api/image/...` are resolved against the instance base URL (RFC 3986) before Markdown render; HTML `<img src>` attributes are rewritten too. Absolute Jotty media URLs that use a different host than the configured instance (e.g. LAN IP vs hostname) are rewritten to the instance origin.
- **Image authentication:** Jotty’s `/api/image/` and `/api/file/` routes authenticate via **browser session cookies**, not `x-api-key`. The Android app sends `x-api-key` on same-origin media requests, but **standard Jotty server versions return HTTP 401** for private images unless `SERVE_PUBLIC_IMAGES=yes` is set on the server. Notes/checklists API calls are unaffected. Upstream Jotty would need API-key support on media routes for private images to load in-app without that env var.

## In-app signals

When PATCH is unavailable for an instance, the app sets a per-instance flag and may show **server_patch_limited_banner** on checklist detail until dismissed.

## References

- [Jotty API](https://github.com/fccview/jotty/blob/main/howto/API.md)
- [CHECKLIST_REORDER.md](CHECKLIST_REORDER.md)
