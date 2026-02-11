# Offline Notes Feature - UI Changes

## Visual Elements Added

### 1. Sync Status Indicators (Notes Screen Header)

Located next to the "Notes" title, showing real-time connection status:

**Online & Synced**
```
Notes [Cloud-Check Icon]
```
- Icon: Cloud with checkmark (✓)
- Color: Primary theme color
- Meaning: Connected to server and fully synced

**Syncing in Progress**
```
Notes [Cloud-Sync Icon]
```
- Icon: Cloud with refresh/circular arrows
- Color: Primary theme color  
- Meaning: Currently syncing changes with server

**Offline**
```
Notes [Cloud-Off Icon]
```
- Icon: Cloud with X/slash
- Color: On-surface variant (dimmed)
- Meaning: No internet connection, working locally

### 2. Refresh Button State

**When Online**
- Enabled (normal color)
- Tap to manually trigger sync
- Useful for forcing an immediate sync

**When Offline**  
- Disabled (dimmed)
- Cannot sync without connection
- Auto-enables when connection restored

### 3. Snackbar Messages

**Offline Save**
```
┌─────────────────────────┐
│ Saved locally           │
└─────────────────────────┘
```
- Appears when creating/editing/deleting notes offline
- Confirms data is safely stored locally
- Auto-dismisses after a few seconds

### 4. Settings Toggle

Location: Settings → General section

```
Offline mode
Save notes locally and sync when online. 
Works when you don't have internet connection.
                                    [ON/OFF]
```

**When Enabled (default)**
- Notes stored locally in Room database
- Automatic sync when online
- Can work completely offline

**When Disabled**
- Original online-only behavior
- No local storage used
- Requires internet for all operations

### 5. Category Filter Chips

```
[All categories]  [Work]  [Personal]  [Ideas]
```
- Works offline with locally cached notes
- Updates when syncing with server
- Shows categories from local notes when offline

## User Flows

### Creating a Note Offline

1. User taps + button
2. Enters note title
3. Taps "Create"
4. **See**: "Saved locally" snackbar
5. **See**: Offline indicator (Cloud-X)
6. Note appears in list immediately
7. When online: Auto-syncs to server

### Editing a Note Offline

1. User taps note to open
2. Taps Edit button
3. Makes changes
4. Taps Save button
5. **See**: "Saved locally" snackbar
6. **See**: Changes saved instantly
7. When online: Auto-syncs to server

### Sync When Reconnecting

1. User was offline (Cloud-X icon)
2. Internet connection restored
3. **See**: Icon changes to Cloud-Sync (syncing)
4. Brief sync process
5. **See**: Icon changes to Cloud-Check (synced)
6. Changes now on server

### Manual Sync

1. User is online
2. Taps Refresh button (circular arrow)
3. **See**: Icon changes to Cloud-Sync briefly
4. Notes list updates with server changes
5. **See**: Icon returns to Cloud-Check

## Color Scheme

All icons use theme-aware colors:
- **Primary color**: Online/syncing states (active)
- **On-surface variant**: Offline state (inactive/dimmed)
- **Surface container**: Snackbar background
- **On-surface**: Snackbar text

## Accessibility

- All icons have proper `contentDescription`
  - "Online" for Cloud-Check
  - "Syncing" for Cloud-Sync  
  - "Offline" for Cloud-Off
- Snackbar messages are announced by TalkBack
- Toggle switch in Settings has proper labels

## Animation

No custom animations added, but:
- Icon changes are instant (state-based)
- Snackbar slides in from bottom (Material3 default)
- List updates are smooth (LazyColumn default)

## Error Handling

When sync fails (rare):
- Icon returns to previous state
- User can retry with Refresh button
- Changes remain saved locally
- Will retry on next connectivity change

## Comparison: Before vs After

**Before (Online-Only)**
- No sync indicators
- Failed silently when offline
- Lost work if connection dropped
- Had to be online to use Notes

**After (Offline-Enabled)**
- Clear sync status always visible
- Works fully offline
- Data never lost
- Seamless online/offline transitions
- User has control (Settings toggle)
