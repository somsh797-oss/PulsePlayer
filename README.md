# Pulse Player — Android Music App

A clean, dark-themed music player for Android (Redmi 9A / Android 10+).

## Features
- Auto-scans ALL songs from internal storage + SD card on launch
- Songs sorted A–Z by name
- Previous / Next song buttons
- ⏪ -5 sec / +5 sec ⏩ seek buttons
- Playback progress bar (draggable)
- Volume slider
- Album art display
- Search/filter songs
- Plays in background (foreground service)
- Lock screen / notification controls
- Supports MP3, FLAC, WAV, OGG, AAC, M4A, and all Android-supported formats

---

## How to Build the APK (Free, takes ~5 minutes)

### Option 1 — Android Studio (Recommended)

1. Download & install **Android Studio** (free):
   https://developer.android.com/studio

2. Extract this ZIP somewhere on your computer.

3. Open Android Studio → **Open** → select the `MusicPlayer` folder.

4. Wait for Gradle sync to finish (downloads dependencies automatically).

5. Click **Build → Build Bundle(s)/APK(s) → Build APK(s)**

6. APK will be at:
   `app/build/outputs/apk/debug/app-debug.apk`

7. Transfer the APK to your Redmi 9A via USB/WhatsApp/etc.

8. On the phone: tap the APK → Install (enable "Install from unknown sources" if prompted).

---

### Option 2 — Command Line (if you have JDK installed)

```bash
cd MusicPlayer
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## First Launch
- The app will ask for **Storage permission** — tap Allow.
- Songs will auto-load within a few seconds.
- Tap any song to start playing.

---

## Permissions Used
- `READ_EXTERNAL_STORAGE` — to read music files from storage & SD card
- `FOREGROUND_SERVICE` — to keep music playing when screen is off
- `WAKE_LOCK` — prevents audio interruption
