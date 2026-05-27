# Proposed REST API: checklist item reorder

Copy this into a [fccview/jotty](https://github.com/fccview/jotty) issue or PR when requesting server support. jotty-android is implemented against this contract ([#29](https://github.com/Darknetzz/jotty-android/issues/29)).

## Endpoint

```
POST /api/checklists/{listId}/items/reorder
```

**Auth:** `x-api-key` (same as other checklist routes via `withApiAuth`).

## Request body (JSON)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `activeItemId` | string | yes | Item being moved |
| `overItemId` | string | yes | Drop target item |
| `position` | string | no | `"before"` (default) or `"after"` relative to `overItemId` |
| `isDropInto` | boolean | no | If `true`, nest `activeItemId` as last child of `overItemId` |
| `category` | string | no | Disambiguates list when multiple categories share ids |

Example:

```json
{
  "activeItemId": "test2-1778329054869",
  "overItemId": "test2-1779857748457",
  "position": "before",
  "isDropInto": false,
  "category": "Uncategorized"
}
```

## Response

**200** — `{ "success": true }` (optionally include updated checklist in `data`).

**404** — checklist or item not found.

**400** — invalid drop (optional; web returns success when dropping into own descendant).

## Server implementation note

Reuse logic from [`app/_server/actions/checklist-item/reorder.ts`](https://github.com/fccview/jotty/blob/main/app/_server/actions/checklist-item/reorder.ts) (`reorderItems` FormData handler): clone tree, splice active item, handle `isDropInto` / `before` / `after`, rewrite order, save markdown, broadcast WebSocket update. Mirror [`app/api/checklists/[listId]/items/route.ts`](https://github.com/fccview/jotty/blob/main/app/api/checklists/%5BlistId%5D/items/route.ts) nested-add pattern (API auth + direct file write).

## Why not the web POST?

The web UI calls a **Next.js Server Action** on the page URL (`POST /checklist/{category}/{listId}`) with RSC-encoded fields (`_1_activeItemId`, `0: ["$K1"]`, `Next-Action` header). That path uses **browser session cookies**, not `x-api-key`, and is not a stable public API.

## OpenAPI

Add path to `public/api/paths/checklists.yaml` and regenerate docs.
