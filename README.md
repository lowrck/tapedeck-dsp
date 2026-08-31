# TapeDeck DSP

A native Android music player that emulates the sound and feel of a cassette deck. It plays your local music library through a real-time audio DSP chain modeled on analog tape — frequency-response coloration, tape saturation, wow & flutter pitch instability, hiss, and dropouts — all tunable live, layered on top of a skeuomorphic, animated cassette deck UI.

## Features

- **Local playback** of MP3, AAC, FLAC, WAV, and OGG files, decoded via Android's `MediaCodec` (no third-party decoder needed).
- **Real-time tape DSP engine**, written in C++ on top of [Oboe](https://github.com/google/oboe) (Google's low-latency Android audio library):
  - Bandpass EQ modeling tape frequency response, per "Tape Type" (Type I Normal / Type II Chrome / Type IV Metal)
  - Tape saturation (soft-clip harmonic distortion)
  - Wow (slow pitch drift) and flutter (fast pitch jitter) via a modulated delay line
  - Pink-noise tape hiss and momentary dropouts
  - "Tape Age" and "Dust & Dirt" sliders that continuously — and slightly randomly, like a real physical tape — control the above
- **Library**: scan a folder and browse it by Playlists, Songs, Albums, and Artists, sorted and labeled from ID3 tags, with drill-down navigation (Artist → Albums → Songs) and per-album/song cover art. The scanned folder is remembered and automatically re-scanned on every launch.
- **M3U/M3U8 playlist support**, resolved against a picked folder (required for reliable relative-path resolution under Android's scoped storage).
- **Background playback**: a foreground service keeps the tape playing when the app is backgrounded, with a media-style notification (album art, previous/play-pause/next) and full lock-screen / Bluetooth / wired-headset media button support.
- **Bluetooth & USB-C headphone aware**: audio output follows the system's active route automatically; playback pauses when headphones disconnect and resumes automatically when they reconnect.

## Requirements

- [Android Studio](https://developer.android.com/studio) (Ladybug or newer recommended) with the Android SDK.
- Android SDK Platform 36 (the build will auto-download this via Gradle if missing).
- Android NDK **27.2.12479018** and CMake **3.22.1** (also auto-provisioned by Android Studio's SDK Manager the first time you build, if not already installed).
- JDK 17.
- An internet connection for the *first* native build — the C++ DSP engine fetches [Oboe](https://github.com/google/oboe) directly from GitHub via CMake's `FetchContent` at configure time.
- A physical Android device or emulator running **Android 8.0 (API 26)** or newer.

## Building

### Option A — Android Studio (recommended)

1. Open the project's root folder (this directory) in Android Studio.
2. Let Gradle sync — accept any SDK/NDK component downloads it prompts for.
3. Run the `app` configuration on a connected device or emulator (▶ button, or `Shift+F10`).

### Option B — Command line

```sh
# From the project root
./gradlew assembleDebug

# Install directly to a connected/authorized device
./gradlew installDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

There is no release signing configuration checked in, so `assembleRelease` will produce an unsigned APK — sign it yourself before distributing outside of local testing.

## Project layout

```
app/src/main/java/com/tapedeck/dsp/   Kotlin: UI (Jetpack Compose), ViewModel, PlaybackService, library scanning
app/src/main/cpp/                     C++: native DSP engine (Oboe-based), JNI bridge
app/src/main/cpp/dsp/                 Individual DSP stages (filter, saturator, wow/flutter, noise, dropout, wander)
```

## Disclaimer

This application was built with [Claude](https://claude.com), Anthropic's AI model, operating as an autonomous coding agent (Claude Code) — including the native audio DSP engine, the Android application code, and this documentation. It has not been audited or reviewed by a professional Android or audio engineer, and it has been tested manually on a single physical device rather than across a broad device matrix. Use, review, and modify it accordingly.
