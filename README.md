# Jotty Android

An Android client for [Jotty](https://jotty.page/) — the self-hosted, file-based checklist and notes app.

## Features

- **Checklists** — Create, view, and manage checklists. Add items, check/uncheck tasks, and track progress.
- **Notes** — Create and edit notes with Markdown support. View and save your content.
- **Connect to your server** — Works with any self-hosted Jotty instance. Configure server URL and API key once.

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
