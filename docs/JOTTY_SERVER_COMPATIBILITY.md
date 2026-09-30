# Jotty server compatibility (Android client)

This document describes how **jotty-android** behaves against different Jotty server versions. The web app on [Jotty](https://jotty.page/) evolves on `main` and tagged releases; the Android app targets the public REST API.

## Checklist item emojis

| Capability | Android behavior |
|------------|------------------|
| Keyword emoji on item labels | **Client-side display only** — not stored in `ChecklistItem.text` and not exposed by the REST API. Matches Jotty web “Show Emojis” using a bundled keyword dictionary. |
| Manual emoji in `text` | Rendered as part of item text (e.g. `💊 Paracet`); no double prefix when auto-emoji is on. |
| Server `config/emojis.json` | **Not fetched** — no stable public REST endpoint; custom server mappings are out of scope until upstream documents one. |
| Toggle | Settings → Behavior → **Show emojis on checklists** (default on). Applies to **simple checklists only** — not project/Kanban boards or task detail. |

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
| Description | Yes on develop+ | Yes | Editable; full read-back when server exposes item fields |
| Subtasks (`children`) | Yes | Via item CRUD + check/uncheck | Full CRUD in item detail |
| Status (column) | Yes | `PUT /api/tasks/…/items/…/status` | Status picker in detail (online) |
| Priority, score, start/target date, estimated time | Yes on develop+ | Yes on develop+ | Editable when `GET /api/tasks/{id}/items/{path}` probe succeeds or list GET returns rich fields |
| Item metadata (created/modified, status history) | Yes on develop+ | Read-only | Shown when rich fields supported |
| Older stable Jotty | Partial (no rich GET) | Description PATCH only | Disabled placeholders; description save without read-back |

The app probes `GET /api/tasks/{taskId}/items/{itemIndex}` on first item detail open per instance (cached). If the route returns 404/405, legacy placeholders are shown. See [fccview/jotty develop](https://github.com/fccview/jotty) and [#52](https://github.com/Darknetzz/jotty-android/issues/52#issuecomment-4699477716).

## Notes

- Encrypted notes (XChaCha20) match the Jotty web format; PGP notes are view-only in-app.
- Note images: relative URLs such as `/api/image/...` are resolved against the instance base URL (RFC 3986) before Markdown render; HTML `<img src>` attributes are rewritten too. Absolute Jotty media URLs that use a different host than the configured instance (e.g. LAN IP vs hostname) are rewritten to the instance origin.
- **Image authentication:** Jotty’s `/api/image/` and `/api/file/` routes authenticate via **browser session cookies**, not `x-api-key`. The Android app sends `x-api-key` on same-origin media requests, but **standard Jotty server versions return HTTP 401** for private images unless `SERVE_PUBLIC_IMAGES=yes` is set on the server. Notes/checklists API calls are unaffected. Upstream Jotty would need API-key support on media routes for private images to load in-app without that env var. When image auth fails, note detail shows a dismissible banner (same condition as above).
- **Archive (notes/checklists):** Jotty web moves items to category **`Archive`** (`ARCHIVED_DIR_NAME`). The Android app archives via `PUT /api/notes/{id}` or `PUT /api/checklists/{id}` with `category: "Archive"`. Archived items are hidden from the default list and shown under the **Archived** filter chip.
- **Categories:** Public REST exposes only `GET /api/categories`. Assigning a category on create/update of a note or checklist creates the folder as a side effect. Creating empty categories, renaming, or deleting category folders is web-only (Next.js server actions) — not available with `x-api-key`. Android’s **Manage categories** screen is browse-only until folder CRUD is added to the public API.
- **Save with HTML/images:** The app saves note body as written (no HTML→Markdown conversion on write). Display-only conversion runs when viewing.
- **In-note links (Jotty 1.28+):** View mode rewrites `[Title](/note/{uuid})`, `[Title](/checklist/{uuid})`, legacy `[Title](/jotty/{uuid})`, and resolved `[[wikilinks]]` into in-app taps (opens the note or switches to the checklist). Unresolved / ambiguous wikilinks render as plain text. Rewrites are display-only (saved content is unchanged). `GET /api/relations/{id}` is used when available (404 on older servers → unique local note title fallback).

## Search (Jotty 1.28+)

| Capability | Android behavior |
|------------|------------------|
| `GET /api/search` hit `uuid` | Preferred addressable id (1.28 puts the filename slug in deprecated `id`) |
| Hit `id` only | Used on older servers where `id` is still the item uuid |
| `indexing: true` with empty hits | Falls back to list/`q` filtering while the relations search index builds |
| Hits that do not hydrate against list endpoints | Falls back to list/`q` so a slug/uuid mismatch cannot blank results |
| Missing search route (404) | Unchanged list/`q` fallback |

## Server version warning

Settings → About shows `GET /api/health` `version` when available. If the parsed major.minor is **newer than 1.28**, a non-blocking warning explains that this app build has not been fully checked against that server. Connecting is never blocked. Bump [`JottyServerVersion.COMPAT_CEILING`](../app/src/main/java/com/jotty/android/util/JottyServerVersion.kt) when a release is validated against a newer Jotty.

## Sharing

| Capability | Jotty web | Jotty OpenAPI (`public/api/paths`) | Android behavior |
|------------|-----------|-------------------------------------|------------------|
| Share with users / public link | Server Actions (`app/_server/actions/sharing/`) | **Not documented** (no `sharing.yaml` in OpenAPI as of Jotty `main`) | Probes `GET /api/sharing/items/{type}/{id}`; on 404 shows export fallback + web-app hint |
| Text export | N/A | N/A | Checklists/Kanban: plain-text export via Share; notes: existing Share action |

When Jotty adds REST sharing endpoints, the client uses [`ShareServerDialog`](../app/src/main/java/com/jotty/android/ui/common/ShareServerDialog.kt) and [`JottySharingProbe`](../app/src/main/java/com/jotty/android/util/JottySharingProbe.kt).

## Kanban card comments (Jotty 1.27+)

| Capability | Jotty web | Jotty OpenAPI (`public/api/paths`) | Android behavior |
|------------|-----------|-------------------------------------|------------------|
| Comments on Kanban cards | Server Actions (`app/_server/actions/comments/`); stored under `.comments` | **Not documented** (no comments paths as of Jotty [1.27.0](https://github.com/fccview/jotty/releases/tag/1.27.0)) | **Not supported** — parity gap only; does not break REST notes/checklists/tasks |

TipTap for Kanban item descriptions (also 1.27+) is web-only; the server still stores `description` as markdown, which the app already reads and edits via REST.

## Jotty 1.28.0 API rebuild

| Capability | Android behavior |
|------------|------------------|
| Stricter Zod request schemas | Existing create/update bodies still validate; unknown fields (e.g. `originalCategory`) are ignored by the server |
| Checklist type `task` / `kanban` | Create still sends `task` (accepted); UI treats both as project/Kanban |
| `GET /api/admin/overview` | Removed upstream; dashboard already ignores failure and uses `GET /api/summary` |
| Tags / brain / batch / MCP | Not used by the Android client yet |
| Relations / wikilinks | View-mode link taps + optional `GET /api/relations/{id}` (see Notes / Search above) |

## Kanban item archive

Kanban **item** archive/unarchive in the web app uses server actions (`archiveItem` / `unarchiveItem` on checklist items). There is **no REST equivalent** in OpenAPI today. Android does not expose per-item archive until upstream adds PATCH fields (e.g. `isArchived`) or a dedicated endpoint.

## In-app signals

When PATCH is unavailable for an instance, the app sets a per-instance flag and may show **server_patch_limited_banner** on checklist detail until dismissed.

## References

- [Jotty API](https://github.com/fccview/jotty/blob/main/howto/API.md)
- [CHECKLIST_REORDER.md](CHECKLIST_REORDER.md)
