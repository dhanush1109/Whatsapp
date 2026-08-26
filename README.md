# Relay

Relay is an original, ad-free Android companion for a WhatsApp Web session. It is not a patched copy of another APK, and it is not affiliated with WhatsApp or Meta.

## Features

- Web session in a desktop-identity WebView, with cookies kept across restarts
- Camera and microphone permission bridge, file picker, and downloads
- Direct chat links via the official `wa.me` URL
- QR scan (CameraX + ML Kit) and QR create/share/save
- Status/media saver through the Storage Access Framework
- PIN lock with optional biometrics
- Second session in a separate `:session2` process (its own WebView data directory)
- Light and dark themes from a Chat & Messaging palette (black text on the green accent)

## Requirements

- Android Studio Ladybug or newer, or JDK 17+
- Android SDK 35 (or 36), Build-Tools, and a device or emulator on API 24+
- `ANDROID_HOME` or `local.properties` `sdk.dir`

## Build

```bat
gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

Package name: `app.relay.companion`.
