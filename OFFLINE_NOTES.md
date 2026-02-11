# Offline Notes Support

## Overview

Jotty Android now supports offline note-taking with automatic synchronization when you're back online. This feature allows you to:

- Create, edit, and delete notes without an internet connection
- Have your changes automatically saved locally
- Sync your changes to the Jotty server when connectivity is restored
- Continue working seamlessly across offline and online states

## How It Works

### Local Storage

When offline mode is enabled (which it is by default), the app stores notes locally using Room database. Each note is cached along with metadata indicating whether it needs to be synced to the server.

### Connectivity Monitoring

The app continuously monitors your internet connection status and displays this in the UI:
- **Cloud with checkmark** (🌐✓) - Online and synced
- **Cloud with sync icon** (🌐⟳) - Currently syncing
- **Cloud crossed out** (🌐✗) - Offline

### Sync Strategy

The app uses a **last-write-wins** strategy with automatic conflict resolution:

1. **Creating notes offline**: Notes are created with a temporary local ID and marked as dirty
2. **When coming online**: The app automatically syncs all pending changes
3. **Server reconciliation**: After pushing local changes, the app fetches the latest from the server
4. **Conflict resolution**: Server data takes precedence to maintain consistency

### Sync Operations

- **Push**: Local changes (create/update/delete) are pushed to the server
- **Pull**: Latest notes are fetched from the server and replace local cache
- **Manual sync**: Tap the refresh button when online to force a sync

## User Interface

### Sync Status Indicators

In the Notes screen, you'll see sync status indicators:
- Top-right corner shows connection status icon
- "Saved locally" snackbar appears when saving offline
- Refresh button is disabled when offline

### Settings

Enable or disable offline mode:
1. Go to **Settings**
2. Scroll to **General** section
3. Toggle **Offline mode**
4. When disabled, the app uses the traditional online-only mode

## Technical Details

### Architecture

```
UI Layer (Composables)
    ↓
OfflineNotesRepository (coordinates sync)
    ↓
┌─────────────────┐         ┌──────────────────┐
│  Room Database  │ ←sync→  │  Jotty REST API  │
│  (local cache)  │         │  (remote server) │
└─────────────────┘         └──────────────────┘
```

### Database Schema

Notes are stored in a Room database with the following fields:
- `id`: Note identifier
- `title`, `content`, `category`: Note data
- `createdAt`, `updatedAt`: Timestamps
- `encrypted`: Encryption status
- `isDirty`: Needs sync flag
- `isDeleted`: Soft delete flag
- `instanceId`: Associated Jotty instance

### Sync Process

1. **Connectivity change detected** → Automatic sync triggered
2. **Sync starts** → UI shows syncing indicator
3. **Push phase** → Dirty notes pushed to server
4. **Pull phase** → Latest notes fetched from server
5. **Cache updated** → Local database updated with server data
6. **Sync complete** → UI updated via Flow

### Files

Key implementation files:
- `data/local/JottyDatabase.kt` - Room database
- `data/local/NoteEntity.kt` - Local note model
- `data/local/NoteDao.kt` - Database operations
- `data/local/OfflineNotesRepository.kt` - Sync coordinator
- `ui/notes/OfflineNotesScreen.kt` - Offline UI wrapper
- `ui/notes/OfflineEnabledNotesScreen.kt` - Notes list with sync UI
- `ui/notes/OfflineNoteDetailScreen.kt` - Note editor wrapper

## Limitations

- **Checklists**: Currently only notes support offline mode; checklists require online connection
- **Encryption**: Encrypted notes can be viewed offline but must be decrypted while online first
- **Categories**: New categories created offline won't appear in filters until synced
- **Multi-device**: Last-write-wins means simultaneous edits on multiple devices will result in one change being lost

## Testing

To test offline functionality:

1. **Enable offline mode** in Settings
2. **Create a note** while online - verify it syncs
3. **Turn on airplane mode**
4. **Create/edit notes** - verify "Saved locally" message
5. **Turn off airplane mode**
6. **Wait for auto-sync** or tap refresh - verify changes appear on web app

## Troubleshooting

### Notes not syncing

- Check that offline mode is enabled in Settings
- Verify internet connection (open a web page)
- Check if sync indicator shows syncing or offline
- Manually tap refresh button when online

### Lost changes

- The app uses last-write-wins strategy
- Simultaneous edits on multiple devices may result in one being overwritten
- Always wait for sync to complete before switching devices

### Storage space

- The local database grows as you create notes
- Clear data in Android settings to reset (will lose pending changes)
- Disable offline mode to use online-only mode without local storage

## Future Improvements

Possible enhancements for future versions:
- Conflict resolution UI for simultaneous edits
- Offline support for checklists
- Selective sync (only certain categories)
- Export/backup local database
- Sync status per note (show which notes are pending)
- Background sync using WorkManager
