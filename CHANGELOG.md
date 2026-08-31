# Changelog

All notable changes made to TapeDeck DSP during development, in the order they were requested.

**A note on timestamps:** this session did not expose exact message-send times. The times below are reconstructed from the Android device clock visible in on-device test screenshots taken shortly after each change was implemented, so they mark roughly *when a change was verified working*, not the instant it was requested. All entries are dated **2026-08-31**.

---

## 12:1x PM — Initial project scope

**Request:** Shared a product design document for "TapeDeck DSP," a skeuomorphic cassette-deck audio player with a real-time tape-degradation DSP engine. Confirmed the target platform should be a native Android app.

- Scaffolded a new Gradle/Kotlin Android Studio project (AGP 8.7.3, Kotlin 2.0.21, Jetpack Compose, min/target/compile SDK 26/34/36).
- Installed Android NDK r27c and CMake locally to support native builds.
- Built the native DSP audio engine in C++ on top of [Oboe](https://github.com/google/oboe) (Google's low-latency Android audio library):
  - `BiquadFilter` (RBJ cookbook EQ), `Saturator` (tanh soft-clip), `WowFlutter` (modulated delay line for pitch instability), `NoiseGenerator` (Paul Kellet pink noise for tape hiss), `DropoutSimulator` (momentary volume dips).
  - `TapeEngine` ties the chain together, owns the Oboe stream, and exposes play/pause/stop/seek and the three "Condition" parameters (Tape Age, Dust & Dirt, Tape Type).
- Built the JNI bridge (`AudioEngine.kt`) and a `MediaCodec`-based decoder (`AudioDecoder.kt`) so no third-party decoding library is needed for MP3/AAC/FLAC/WAV.
- Built the Jetpack Compose UI: `CassetteDeckView` (animated spinning reels + VU meters), `TransportControls`, `ConditionPanel` (sliders + tape-type chips), `MainActivity`/`PlayerScreen`.
- Verified the project builds clean end-to-end (`./gradlew assembleDebug`).

## 12:40 PM — Real-device install

**Request:** "my phone is connected via usb"

- Installed and launched the debug build on the connected Samsung Galaxy Z Fold.
- Verified playback, transport controls, and the Condition sliders work on real hardware.

## 12:49–1:07 PM — M3U playlist support

**Request:** "the app should support playlist files in m3u format"

- Added `PlaylistParser` (minimal M3U/M3U8 parser) and `PlaylistResolver` (resolves playlist entries against a picked SAF folder tree, since scoped storage gives no filesystem path for a single picked file).
- Added a playlist queue to the player (`PlaylistTrack`, Next/Previous, `PlaylistBar` UI) and a picker dialog for folders containing multiple playlists.
- **Bug found & fixed during testing:** the device flagged the native library as not 16 KB page-size aligned (a newly-mandatory Android requirement) — added `-Wl,-z,max-page-size=16384` linker flag and switched to static libc++.
- **Bug found & fixed during testing:** tape hiss played continuously even when idle/paused — the native audio stream auto-started on load regardless of play state, and noise generation wasn't gated on active playback. Fixed both.

## ~1:15 PM — Playlist naming, album art, ID3 tags

**Request:** "make the selectable m3u file in the recursive folder search only show the file's name itself not the path or extension. and if possible put the album art in the background of the tape for the song thats currently playing. and put the song name and album name instead of the raw file name according to its id3 tag if available"

- Playlist picker dialog now shows just the clean playlist name (no folder path, no `.m3u` extension).
- Added `AudioMetadataReader` using `MediaMetadataRetriever` to read ID3 title/album tags and embedded cover art without decoding audio.
- Track title/album now prefer the ID3 tag, falling back to the filename when absent.
- Album art renders as a cover-cropped, scrim-darkened background behind the cassette deck illustration.

## 1:31–1:58 PM — Library tab

**Request:** "add a library tab which includes playlist, song, album, and artist. add a button to specify a folder to be scanned each time the app is opened. display album art for albums in the album tab and smaller album art next to each song in the song tab. use id3 tags to sort the albums, artists and songs."

- Added bottom navigation (Player / Library) and a new `LibraryScreen` with Playlists / Songs / Albums / Artists sub-tabs.
- Added `LibraryScanner`: recursively scans a picked folder via `MediaMetadataRetriever`, grouping songs into albums/artists and sorting each list by its ID3 tag (song title, album name, artist name). Album art is decoded and downsampled at most once per album and shared by reference, to keep memory reasonable on large libraries.
- Folder choice persists via `SharedPreferences` and auto-rescans on every app launch.
- Songs show a small album-art thumbnail; Albums render as a grid with large art.

## 2:01–2:26 PM — Drill-down navigation, background playback, notification

**Request:** "when clicking an album it should show the songs within, when clicking an artist it should show the albums from that artist with album art and when clicking the album it should show the songs. also the app needs a notification with play/pause skip forward and skip backwards as well as the song name and to the far left side the album art in modern style. the notification should also double as a way to keep the player working in the background"

- Added in-Library drill-down: Artist → grid of that artist's albums → Album → track-numbered song list → tap plays from that point, with a back stack and system-back support.
- Major architecture change: moved the audio engine out of the (Activity-scoped) ViewModel into a new foreground `PlaybackService`, so playback survives the Activity being destroyed or the app being swiped from Recents. `PlayerViewModel` now binds to the service and mirrors its state.
- Added a `MediaSessionCompat`-backed notification (`NotificationCompat.MediaStyle`) with previous/play-pause/next actions and album art, matching the native "modern" media notification look; ongoing while playing, swipe-dismissible while paused.
- **Mid-request follow-up:** "make the left and right spools size relative to how much has played and how much is left to play" — this was already implemented; verified on-device.
- **Mid-request follow-up:** "the right appears to fill up but the left doesnt seem to deplete" — real bug: radius was interpolated linearly, but perceived circle size tracks area (∝ radius²), so the same pixel change looked dramatic on the small reel and negligible on the large one. Fixed by interpolating area instead of radius.
- **Mid-request follow-up:** "does the app properly support bluetooth headphones with both audio output and skip input? id like it to" — confirmed output/skip-input already worked via standard Android audio routing + the MediaSession.
- **Mid-request follow-up:** "it should be able to smoothly transition between bluetooth headphones/usb-c headphones and speaker with just a track pause when disconnected... but it should be able to recover automatically when one of those two are connected" — added `ACTION_AUDIO_BECOMING_NOISY` receiver (auto-pause on disconnect) and `AudioManager.AudioDeviceCallback` (auto-resume on reconnect), tracking *why* playback paused so a deliberate user pause is never auto-resumed. Verified live with real Bluetooth earbuds (disconnect → auto-pause; reconnect → auto-resume).

## ~2:30 PM — Silent playback after Bluetooth reconnect

**Request:** "the app s ays the music is playing but theres no audio coming out the headphones" (+ "and the vu meters arent moving")

- Root cause: when the output route disconnects, Oboe tears the stream down entirely (`ErrorDisconnected`); simply calling `play()` again on the dead stream object was a no-op, so the UI said "playing" but the audio callback never resumed.
- Fixed in `TapeEngine::onErrorAfterClose`: reopens a fresh stream on `ErrorDisconnected` and restarts it if playback was active. Verified with a real disconnect/reconnect cycle — VU meters and position confirmed genuinely live afterward.

## 2:37 PM — Playlist title / EXTINF fixes

**Request:** "when using playlist workflow from the library it shows file name in the playlist position text 'Slide.mp3'"

- `PlaylistTrack` title fallback (used when an M3U entry has no `#EXTINF` line) now strips the file extension instead of showing the raw filename.

**Follow-up question:** "will it in theory still function fine for playlists with #EXTINF lines?" — confirmed yes; the fix only touches the fallback path, `#EXTINF`-provided titles are used unchanged.

**Follow-up request:** "patch that to fall back to the file name minus extension if the string is malformed in the way you were concerned about" — `PlaylistParser` now normalizes a blank/whitespace-only `#EXTINF` title to `null`, so it correctly falls through to the filename fallback instead of showing an empty title. Verified with a real malformed playlist file pushed to the device.

## Media button (headphone) handling

**Request:** "the app should also accept next song and previous song commands from usb or bluetooth headphones"

- Verified hardware next/previous already routes to the app's `MediaSession` (Bluetooth AVRCP and wired media buttons funnel through the same system key-event path).
- Replaced reliance on the media-compat library's default key-event translation with an explicit `onMediaButtonEvent` override in `PlaybackService`, for deterministic handling across OEM Bluetooth stacks.
- **Follow-up:** "will that still work if the application is in the background or if the screen is off?" — tested directly: turned the screen off and backgrounded the app, then confirmed `KEYCODE_MEDIA_NEXT`/`KEYCODE_MEDIA_PREVIOUS` correctly changed tracks with the screen still off throughout, since the `MediaSession` lives in the foreground service, independent of the Activity/UI.

**Request:** "make the first skip back restart the current track and the second within a couple seconds skip back a song" (+ "unless its within the first 10 seconds of the song" + "then skip back a song")

- `playPreviousTrack()` now: skips back immediately if within the first 10 seconds of the current track; otherwise restarts the current track on the first press, and skips back a track if pressed again within 3 seconds. Applies uniformly to the in-app button, notification, and hardware media keys, since they all funnel through the same service method. Verified all three cases on-device via direct UI taps.

## 2:41 PM — Changelog

**Request:** "create a changelog.MD file in the project folder containing a full list of changes timestamped per request prompted to you"

- Added this `CHANGELOG.md`.

## 2:43 PM — README

**Request:** "create a README.md with a description of the app, a short instructional on how to compile the app as well as a disclaimer that this was created using claude."

- Added `README.md`: app description, feature summary, build requirements and instructions (Android Studio and command-line), project layout, and a disclaimer that the app was built with Claude.

## ~2:47 PM — GitHub repository

**Request:** "create a github project and push the project to it"

- Installed the GitHub CLI (`gh`) to a user-local directory (no root needed) and authenticated.
- Initialized git in the project root and added a `.gitignore` (build outputs, native build intermediates, `local.properties`, IDE/tooling config).
- Created the public repository [`lowrck/tapedeck-dsp`](https://github.com/lowrck/tapedeck-dsp) and pushed the initial commit.

## 2:51 PM — Universal APK

**Request:** "now compile a universal binary apk with support for x86, x86_64, armv7 and arm64"

- Added the missing `x86` ABI to `abiFilters` in `app/build.gradle.kts` (previously `arm64-v8a`, `armeabi-v7a`, `x86_64` only), completing all four requested architectures in a single non-split APK.
- Verified the native DSP engine compiles cleanly for `x86` and that all four `.so` slices are bundled in one APK; reinstalled and confirmed a clean launch on-device.

## Current — This file

**Request:** "update the changelog excluding local device file copy from it, and afterwards push the build.gradle.kts changes as well as the updated changelog to github"

- Updated this `CHANGELOG.md` with the README, GitHub repository, and universal-APK entries above.
