# Conflict Resolution Flow

## Scenario: Simultaneous Edits

```
Device A (Android - Offline)          Server/Web App          Device B (Android - Online)
═══════════════════════════            ══════════════          ═══════════════════════════

1. Note "Meeting Notes"
   Content: "Discussion points"
   ├─ Edit offline
   └─ New content: "Discussion         Note synced              Note unchanged
      points + Action items"           normally

2. Still offline                       Note edited:             OR someone else edits
   Saving locally...                   "Discussion points       via web app
                                        + Decisions made"

3. Connection restored
   Auto-sync triggered
   ↓
   CONFLICT DETECTED! 
   (content differs)
   ↓

4. Resolution:
   ┌──────────────────────────────────────────────────────────┐
   │ PRIMARY NOTE (from server)                               │
   │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
   │ Title: "Meeting Notes"                                   │
   │ Content: "Discussion points + Decisions made"            │
   │ (Server version preserved)                               │
   └──────────────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────────────┐
   │ LOCAL COPY (your offline work)                           │
   │ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
   │ Title: "Meeting Notes (Local copy)"  ← Suffix added     │
   │ Content: "Discussion points + Action items"              │
   │ (Your offline changes preserved)                         │
   └──────────────────────────────────────────────────────────┘

5. Notification:
   ┌──────────────────────────────────────────────────────────┐
   │ ⚠️ 1 conflict(s) detected. Local copies created to      │
   │    preserve your data.                                   │
   │                                      [View copies]       │
   └──────────────────────────────────────────────────────────┘

6. User action:
   - Tap "View copies" → Searches for "(Local copy)"
   - Review both notes side-by-side
   - Manually merge: Copy relevant parts from local copy to primary
   - Delete the local copy when done
```

## Why This Approach?

### ✅ Advantages

1. **No Data Loss**: Both versions are preserved
2. **Simple & Predictable**: Server version is always primary
3. **User Control**: Manual merge gives flexibility
4. **Works Everywhere**: No complex UI needed
5. **Safe**: Even if merge is forgotten, data exists

### 📋 Comparison with Alternatives

| Approach | Data Loss Risk | Complexity | User Effort |
|----------|---------------|------------|-------------|
| **Last-write-wins** (old) | ❌ High | ✅ Low | ✅ Low |
| **Local copy** (current) | ✅ None | ✅ Low | ⚠️ Medium |
| **Auto-merge** | ⚠️ Medium | ❌ High | ✅ Low |
| **Merge UI** | ✅ None | ❌ Very High | ❌ High |

### 🎯 Best for:

- Users who rarely have conflicts (most common case)
- Quick conflict resolution without complex UI
- Maintaining simple codebase
- Mobile app constraints (small screen, limited time)

## Conflict Detection Logic

```kotlin
fun hasConflict(localNote: NoteEntity, serverNote: NoteEntity): Boolean {
    return localNote.title != serverNote.title || 
           localNote.content != serverNote.content ||
           localNote.category != serverNote.category
}
```

Triggers when:
- ✅ Title changed
- ✅ Content changed  
- ✅ Category changed

Does NOT trigger when:
- ✅ Only timestamps differ
- ✅ Only encryption status differs
- ✅ Note was only created offline (no conflict possible)

## User Experience

### Typical Flow (No Conflict)
```
1. Work offline → "Saved locally"
2. Come online → Auto-sync
3. ✓ Synced (no notification)
```

### Conflict Flow (Rare)
```
1. Work offline → "Saved locally"
2. Come online → Auto-sync
3. ⚠️ "1 conflict(s) detected" [View copies]
4. Tap "View copies" → See "(Local copy)" notes
5. Open both notes, compare, merge
6. Delete the copy
7. Done! ✓
```

## Implementation Details

**Sync Process:**
1. Get local notes snapshot (before pushing)
2. Push dirty local notes to server
3. Fetch all notes from server
4. Compare each server note with local snapshot
5. If dirty local + different content → Create copy
6. Replace all notes with server + copies
7. Notify user if conflicts found

**Copy Naming:**
- Original: "Meeting Notes"
- Copy: "Meeting Notes (Local copy)"
- Multiple conflicts: Each gets own copy

**Future Enhancement Ideas:**
- Show conflict badge on note cards
- Auto-merge non-overlapping changes
- Side-by-side merge view
- Conflict resolution history
