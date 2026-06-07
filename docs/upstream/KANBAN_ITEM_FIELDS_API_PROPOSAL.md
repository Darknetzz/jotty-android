# Proposed REST API: Kanban item fields

Copy this into a [fccview/jotty](https://github.com/fccview/jotty) issue or PR when requesting server support. jotty-android tracks this in [#52](https://github.com/Darknetzz/jotty-android/issues/52).

## Problem

The Jotty **web UI** (Kanban item editor) reads and writes rich fields on checklist items: description, priority, score, target date, estimated time, and audit metadata (created/modified by, status-change history). These exist on the internal [`Item`](https://github.com/fccview/jotty/blob/develop/app/_types/checklist.ts) type but are **stripped** from REST responses in `transformItem` ([checklists route](https://github.com/fccview/jotty/blob/develop/app/api/checklists/route.ts), [tasks route](https://github.com/fccview/jotty/blob/develop/app/api/tasks/%5BtaskId%5D/route.ts)).

The Android client can PATCH `description` today, but GET never returns it — so mobile cannot round-trip descriptions or show fields edited on the web.

## Requested changes

### 1. Extend `ChecklistItem` schema (GET responses)

Add to OpenAPI [`schemas.yaml`](https://github.com/fccview/jotty/blob/develop/public/api/components/schemas.yaml) and to `transformItem` for kanban/task types:

| Field | Type | Notes |
|-------|------|-------|
| `description` | string | Item body / notes |
| `priority` | string enum | `critical`, `high`, `medium`, `low`, `none` |
| `score` | number | Numeric score |
| `targetDate` | string (ISO date) | Due / target date |
| `estimatedTime` | number | Estimated hours |
| `createdBy` | string | Username |
| `createdAt` | string (ISO date-time) | Creation timestamp |
| `lastModifiedBy` | string | Username |
| `lastModifiedAt` | string (ISO date-time) | Last edit timestamp |
| `history` | array | Status changes: `{ status, timestamp, user }` |

Recursive `children` should include the same fields.

### 2. Extend PATCH body

**PATCH** `/api/checklists/{listId}/items/{itemIndex}` — accept optional writable fields:

```json
{
  "text": "Updated title",
  "description": "Additional notes",
  "priority": "high",
  "score": 5,
  "targetDate": "2026-06-15",
  "estimatedTime": 2.5
}
```

At least one field required (existing rule). Omit fields to leave unchanged.

### 3. Optional: single-item GET

```
GET /api/tasks/{taskId}/items/{itemIndex}
```

Returns one transformed item (including nested `children`) so clients can refresh a Kanban card without reloading the full checklist. Index paths: `"0"`, `"0.1"`, etc.

**Response:** `{ "item": { …ChecklistItem… } }`

### 4. OpenAPI / API.md

Update `public/api/paths/checklists.yaml` and [howto/API.md](https://github.com/fccview/jotty/blob/main/howto/API.md) with the expanded item schema and PATCH fields.

## Server implementation note

Reuse existing server actions (`updateItem`, checklist markdown read/write). `transformItem` should map from internal `Item` to the public shape — mirror what the web Kanban editor already persists in markdown/JSON.

Reference types: [`app/_types/checklist.ts`](https://github.com/fccview/jotty/blob/develop/app/_types/checklist.ts) (`KanbanPriority`, `StatusChange`, etc.).

## Why not the web Server Action?

The web UI updates items via **Next.js Server Actions** with browser session cookies, not `x-api-key`. Mobile and third-party clients need stable REST endpoints documented in OpenAPI.

## Android client impact

Once shipped upstream, jotty-android will enable Priority, Score, Target date, Estimated time, Metadata, and description read-back in the Kanban item detail screen (currently placeholders or write-only for description).
