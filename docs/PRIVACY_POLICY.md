# Privacy Policy — Jotty Android

**Last updated:** June 9, 2026

**App:** Jotty Android (`com.jotty.android`)

**Publisher:** Independent community project maintained at [github.com/Darknetzz/jotty-android](https://github.com/Darknetzz/jotty-android). This app is **not** an official Jotty product and is **not** affiliated with or endorsed by the [Jotty](https://jotty.page/) project.

This privacy policy describes how the Jotty Android app handles information when you use it. It applies to the app distributed via Google Play, GitHub Releases, and other channels.

---

## Summary

Jotty Android is a client for **your own** (or your organization’s) self-hosted Jotty server. The app maintainer **does not** operate a backend service for your notes or checklists and **does not** collect, sell, or use your personal data for advertising or analytics.

Almost all data the app processes is data **you choose** to store on a Jotty server you configure, or data kept **locally on your device** to support offline use and app settings.

---

## Information stored on your device

The app may store the following on your phone or tablet:

| Data | Purpose |
|------|---------|
| **Jotty server URL(s)** and **instance names** | Connect to one or more Jotty servers you add |
| **API key(s)** | Authenticate with your Jotty server (stored in Android **EncryptedSharedPreferences** when available) |
| **Notes and checklists** | Offline cache and sync when “local storage & sync” is enabled (Room database) |
| **App preferences** | Theme, behavior toggles, filters, and similar settings (DataStore) |
| **Note decryption passphrases (optional)** | Only if you enable “Remember with biometric” after decrypting a note; encrypted with a biometric-protected key in the Android Keystore |
| **Decrypted note content (temporary)** | Held in memory for the current session after you decrypt a note; cleared when the session ends |
| **Debug log buffer** | Recent app log lines in memory (up to ~1,000 lines) for optional export from Settings → Troubleshooting |

Sensitive items (API keys, biometric passphrases, settings, and the local database) are **excluded** from Android automatic backup and device-to-device transfer where the platform supports those exclusions.

---

## Information sent over the network

### Your Jotty server

When you connect an instance, the app communicates **only with the server URL you provide**, using your API key in the `x-api-key` header. Depending on how you use the app, this can include:

- Fetching and saving notes and checklists
- Health checks and dashboard summary data
- Loading images from URLs in note content when those URLs point at your Jotty host

**Your Jotty server operator** (often you) controls that server and its data. Their privacy practices are separate from this policy. The app does not send your data to the app developer’s servers.

Connections may use **HTTPS** or **HTTP** (for example on a home LAN). You are responsible for securing the network path and server you use.

### GitHub (optional — update checks)

If you use **Settings → About → Check for updates**, the app contacts **GitHub’s public API** (`api.github.com`) and may download release assets or `CHANGELOG.md` from the [jotty-android repository](https://github.com/Darknetzz/jotty-android). Requests include a `User-Agent` header with the app version (for example `Jotty-Android/1.5.2`). No account login is required. This does **not** run automatically in the background unless you trigger an update check (or related in-app update flow you start).

### Other URLs

- Links in note content open in your **browser** when you tap them.
- The optional rich note editor uses a **bundled** WebView page; it does not load your note content from external editor hosts.

---

## What we do not do

- **No ads** and no ad identifiers
- **No analytics**, crash reporting, or telemetry SDKs (no Firebase, Crashlytics, or similar)
- **No selling or sharing** of your data with data brokers
- **No account** with the app developer — authentication is between the app and **your** Jotty server

---

## Permissions

The app requests permissions needed for its features:

| Permission | Why |
|------------|-----|
| **Internet** | Connect to your Jotty server, GitHub (update checks), and URLs you open |
| **Access network state** | Detect online/offline status for sync |
| **Install packages** | Optional in-app install when you download an update APK from GitHub Releases (not used for Play Store updates) |
| **Biometric** (when available) | Optional unlock for stored note passphrases |

The app does not access contacts, SMS, phone identity, location, microphone, or camera for core functionality.

---

## Debug log export

You may **manually** export debug logs from Settings. An export can include app version, device model, Android version, instance name, **server URL**, and recent log lines (which may mention API paths or errors). **API keys and passphrases are not intentionally included**, but you should review exports before sharing them. Export is **user-initiated** only; logs are not uploaded automatically.

---

## Data retention and deletion

- **Uninstalling** the app removes local app data from your device (subject to Android behavior).
- **Disconnect** or **remove an instance** in the app stops using that server’s stored credentials.
- **Clear biometric passphrases** in Settings → Security removes stored note passphrases protected by biometrics.
- Data on your **Jotty server** remains until you delete it on the server or with another Jotty client.

---

## Security

The app uses industry-standard practices where applicable: encrypted storage for API keys, Android Keystore for optional biometric passphrases, and local encryption for notes when you use the in-app XChaCha20 encrypt feature (compatible with Jotty web). No security measure is perfect; protect your device, API keys, and encryption passphrases.

---

## Children’s privacy

Jotty Android is a general productivity app and is not directed at children under 13. We do not knowingly collect personal information from children.

---

## International users

Your Jotty server may be located anywhere you or your administrator chooses. GitHub (update checks) is operated by GitHub, Inc. (Microsoft). By using optional update checks, you interact with GitHub under their terms and policies.

---

## Changes to this policy

We may update this policy when app behavior changes. The **“Last updated”** date at the top will change. For material changes, the updated policy will be published in the same location (this document in the project repository).

---

## Contact

Questions or privacy requests about **Jotty Android** (this app):

- **GitHub Issues:** [github.com/Darknetzz/jotty-android/issues](https://github.com/Darknetzz/jotty-android/issues)

For questions about data stored on a **Jotty server**, contact whoever operates that server.

---

## Play Console URL

If you need a stable link for Google Play, use the version on the default branch:

**https://github.com/Darknetzz/jotty-android/blob/main/docs/PRIVACY_POLICY.md**
