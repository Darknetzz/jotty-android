# Checklist item reordering (Android)

## Status

**Not supported in the Android app.** Reordering checklist items is only available in the [Jotty web app](https://jotty.page/) (drag and drop).

## Why

The Jotty **REST API** exposed to third-party clients (`/api/checklists/...`) supports creating items, checking/unchecking, updating text, and deleting by index path. It does **not** document or expose a reorder/move endpoint (see [public/api/paths/checklists.yaml](https://github.com/fccview/jotty/blob/main/public/api/paths/checklists.yaml) in the Jotty repo).

The web UI reorders items via an internal Next.js server action (`reorderItems` in `app/_server/actions/checklist-item/reorder.ts`), which writes the checklist markdown file directly. That action is not available over the `x-api-key` REST API that jotty-android uses.

## What would unblock Android support

1. Jotty adds a documented REST endpoint for reorder (e.g. `PUT /api/checklists/{listId}/items/reorder` with `activeItemId`, `overItemId`, `position`, `isDropInto`), matching the web semantics.
2. jotty-android adds the Retrofit method, offline `MOVE` pending op (if needed), and drag-and-drop UI in checklist detail (simple lists first, then nested project items).

Until then, users should reorder items in the web app; order is reflected on the server and will appear in the Android app after the next sync.
