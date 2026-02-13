# Jotty Android

An Android client for [Jotty](https://jotty.page/) — the self-hosted, file-based checklist and notes app.

## Features

- **Checklists** — Create, view, and manage checklists. Add items, check/uncheck tasks, and track progress.
- **Notes** — Create and edit notes with Markdown support. View and save your content.
- **Offline support** — Take notes without an internet connection. Changes sync automatically when you're back online.
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

**Signed release APK:** Copy `keystore.properties.example` to `keystore.properties`, create a keystore (see the example file for the `keytool` command), then run `.\build.ps1 -Release`. The release APK will be signed and installable. Keep your keystore and passwords safe and never commit them.

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

## Troubleshooting

### Server log: `Session check error` / `ERR_SSL_WRONG_VERSION_NUMBER`

This usually means something is speaking **HTTP** where **TLS (HTTPS)** is expected:

- **If the app talks to your server:** In the app, use an instance URL that starts with `https://` (e.g. `https://jotty.example.com`). If you enter a URL without a scheme, the app adds `https://` by default.
- **If the server does a “session check”** (e.g. an outbound request to validate the session): that request’s URL must use `https://`. Check the Jotty server config (e.g. app URL, callback URL, or any URL used for session validation) and ensure it’s HTTPS, not HTTP.

### Server log: `XChaCha Decryption Error: Error: invalid input` at `from_hex`

The server is decoding encrypted content with **hex** while the Android app (and typically the Jotty web app) store **base64** in the encrypted JSON (`salt`, `nonce`, `data`). So the server is likely using the wrong decoder for that payload.

- **Fix on the Jotty server:** In the Jotty repo, search for XChaCha decryption and `from_hex`. The code that reads the encrypted note body should decode the JSON fields (salt, nonce, data) as **base64**, not hex. If some path expects hex, either switch it to base64 or add a format check (e.g. try base64 first, then hex) so both formats are accepted.

## Architecture

- **Jetpack Compose** — UI
- **Retrofit** — REST API client for Jotty
- **Room** — Local database for offline storage
- **DataStore** — Storing app settings and server credentials
- **Navigation Compose** — Screen navigation

## Offline Support

Jotty Android supports working offline. When enabled (default), notes are stored locally and automatically synced when you have an internet connection. See [OFFLINE_NOTES.md](OFFLINE_NOTES.md) for details.

Key features:
- Create, edit, and delete notes without internet
- Automatic sync when connectivity is restored
- Visual sync status indicators
- Last-write-wins conflict resolution

## Encryption

Jotty supports **XChaCha20-Poly1305** (passphrase-only, recommended) and **PGP**. This app supports only **XChaCha20-Poly1305** in-app: you can encrypt and decrypt notes with a passphrase. Notes encrypted with **PGP** in the Jotty web app must be decrypted there; the app will show a short message and a link to use the web app.

**Limitations:** Encrypted note content cannot be searched (titles and metadata remain searchable). Only the key owner can decrypt; shared encrypted notes stay encrypted for others. There is no passphrase recovery — keep secure backups of your passphrase.

## API Reference

The app uses the [Jotty REST API](https://github.com/fccview/jotty/blob/main/howto/API.md). Authentication is via the `x-api-key` header.

## License

MIT
