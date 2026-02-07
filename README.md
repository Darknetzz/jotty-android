# Jotty Android

An Android client for [Jotty](https://jotty.page/) — the self-hosted, file-based checklist and notes app.

## Features

- **Checklists** — Create, view, and manage checklists. Add items, check/uncheck tasks, and track progress.
- **Notes** — Create and edit notes with Markdown support. View and save your content.
- **Connect to your server** — Works with any self-hosted Jotty instance. Configure server URL and API key once.

## Releases / Download

Pre-built APKs are published on the [Releases](https://github.com/Darknetzz/jotty-android/releases) page. Download the latest `jotty-android-*.apk` and install on your device (enable “Install from unknown sources” if needed).

## Setup

### 1. Get your API key

1. Log into your Jotty instance in a browser.
2. Go to **Profile** → **Settings**.
3. In the **API Key** section, click **Generate**.
4. Copy the generated key (starts with `ck_`).

### 2. Configure the app

1. Open the app.
2. Enter your Jotty server URL (e.g. `https://jotty.example.com`).
3. Enter your API key.
4. Tap **Connect**.

## Releasing

Version is defined in **`gradle.properties`** (single source of truth):

- `VERSION_NAME` — user-visible version (e.g. `1.0.1`)
- `VERSION_CODE` — integer, must increase each release (e.g. `2`)

To cut a new release: update both in `gradle.properties`, add an entry to **`CHANGELOG.md`**, then build and tag (e.g. `v1.0.1`).

## Building

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer, or
- JDK 17+
- Android SDK 35

### Gradle Wrapper

**Recommended:** Open the project in Android Studio. It will download the Gradle wrapper automatically when you sync.

If the wrapper is missing (e.g. `gradle-wrapper.jar`), create it:

```bash
# With Gradle installed:
gradle wrapper --gradle-version 8.9
```

### Build commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (signed)
./gradlew assembleRelease
```

## Architecture

- **Jetpack Compose** — UI
- **Retrofit** — REST API client for Jotty
- **DataStore** — Storing server URL and API key
- **Navigation Compose** — Screen navigation

## API Reference

The app uses the [Jotty REST API](https://github.com/fccview/jotty/blob/main/howto/API.md). Authentication is via the `x-api-key` header.

## License

MIT
