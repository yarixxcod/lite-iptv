# Vvvion IPTV

Lightweight IPTV player for phones, tablets and Android TV. One universal APK, black-and-white UI, D-pad first, no ads and no analytics.

Package: `player.vvvion.app`. Min SDK 23, target SDK 35.

## Features

- Several playlists at once: M3U/M3U8 URL, local file (Storage Access Framework), Xtream Codes login, clipboard paste
- Phone link: the TV runs a small page on the LAN and shows a QR code, so a phone can paste a URL or upload an M3U without a TV keyboard
- Streaming M3U parser (large lists are inserted in batches) with `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`, `tvg-chno` and catchup attributes, plus duplicate folding into alternate URLs
- Groups, search, sort, hide, favorites with reorder, recently watched, resume last channel
- Direct channel number entry and CH+/CH− / D-pad zapping
- XMLTV guide (plain, `.gz`, `.xz`) from `url-tvg` or a manual URL, stored for −6 h … +48 h, with a per-playlist timezone offset
- Player: fit / fill / zoom / 16:9 / 4:3, audio and subtitle tracks, sleep timer, last channel, picture-in-picture on phones, optional background audio
- Buffer profiles (Auto, Low latency, Stable, Max stability), resolution and bitrate caps, hardware decoder with automatic software fallback
- Retry with backoff, reconnect when the network returns, try the next URL when a channel has alternates
- Offline-first: the UI reads Room immediately; a weak-connection chip replaces blocking error dialogs
- English and Russian, system locale by default. Black theme, optional AMOLED black
- Parental PIN for groups
- Self-update: on launch the app reads `latest.json` from the latest GitHub Release and can download and install a newer APK

Stalker / MAG portals are not implemented.

## Updates

The app compares its `versionCode` with [latest.json](https://github.com/yarixxcod/lite-iptv/releases/latest/download/latest.json). A newer build is offered as a banner, then installed with the system package installer. The device must allow installs from this app.

Upload a new APK from the panel: [cdn.jsdelivr.net/gh/yarixxcod/lite-iptv@main/docs/](https://cdn.jsdelivr.net/gh/yarixxcod/lite-iptv@main/docs/index.html). The page asks for a GitHub token with contents write on this repo, stores it only in that browser, and publishes:

- `VvvionIPTV.apk` — the file the app downloads
- `VvvionIPTV-<version>.apk` — the same file with a versioned name
- `latest.json` — `versionCode`, `versionName`, `apkUrl`, `notes`

A custom manifest URL can be set in the app settings. It must be HTTPS JSON with the same fields. An empty URL uses the GitHub release above. GitHub Pages is optional: enable it in the repository settings and run the Panel workflow if you want `https://yarixxcod.github.io/lite-iptv/` instead of the jsDelivr address.

Tagging `v*` still builds a release APK in GitHub Actions and writes the same `latest.json`.

## Screenshots

Screenshots will be added here after the first device pass.

## Build

Requirements: JDK 17, Android SDK platform 37.2 (compile). Target SDK stays 35.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/VvvionIPTV-debug.apk`.

Release builds are minified with R8 and resource shrinking:

```bash
./gradlew assembleRelease
```

Without signing secrets, release uses the debug keystore so a local release APK is still installable.

## Playlist notes

- M3U header `url-tvg` / `x-tvg-url` is picked up as the guide address
- Xtream live streams use `{server}/live/{user}/{pass}/{id}.m3u8` and `{server}/xmltv.php` for the guide
- Credentials stay in the on-device database. There is no account and no telemetry
- Cleartext HTTP is allowed because many IPTV streams are not HTTPS

## Signing secrets

Create a keystore only on a machine you control:

```bash
keytool -genkeypair -v -keystore vvvion.jks -alias vvvion \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Vvvion IPTV,O=Vvvion"
```

Add these GitHub Actions secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 vvvion.jks` |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

If any secret is missing, the release workflow still builds and signs with the debug keystore.

## CI

- `.github/workflows/build.yml` — push, pull request, or manual: lint, unit tests, debug APK artifact
- `.github/workflows/release.yml` — tag `v*`: release APK attached to a GitHub Release

## Stack

Kotlin, Jetpack Compose Material 3, Android TV Material cards, Media3 ExoPlayer 1.11 (HLS, DASH, progressive, MPEG-TS, RTSP), Room, DataStore, OkHttp, Coil, Koin, Paging 3, WorkManager. AGP 9.5 with the classic DSL opt-out (`android.newDsl=false`, `android.builtInKotlin=false`) so KSP and Kotlin stay explicit. Media3 1.12 and WorkManager 2.12 require minSdk 24, so this project stays on the latest versions that still support API 23.
