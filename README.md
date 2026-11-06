# Resonance – Android MP3 player

Kotlin + Jetpack Compose + Media3 (ExoPlayer). UI modelled on the supplied screenshots.

## Build & run
1. Install Android Studio (Ladybug or newer) and open this folder (`Resonance`). Let Gradle sync.
2. Plug in a phone (USB debugging on) or start an emulator, press Run.
3. Release APK: Build > Build Bundle(s)/APK(s) > Build APK(s).
4. On first launch allow "Music and audio" and notifications.

## What it does
- **MP3 only**: queries MediaStore for `audio/mpeg` files ending in `.mp3`, flagged as music, and skips
  recordings (Android 12+ flag + recordings/voice-recorder/call-recording folders), ringtones, alarms and notifications. WAV/other formats are never listed.
- **Album details**: title, artist, album, year, track, bitrate and cover art are read from the MP3's ID3 tags (cover via system thumbnail, falling back to the embedded picture).
- Tabs: For you, Songs, Playlists, Folders, Albums. Mini player, full Now Playing screen (pill seek bar, ±10 s, shuffle, repeat, favourite, queue, theme switch), song options sheet.
- **Equalizer** (Now Playing > sliders icon, or options > Playback settings): presets Normal, Classical, Dance, Flat, Folk, Heavy Metal, Hip Hop, Jazz, Pop, Rock + **Custom**; one slider per device band; **Bass boost** and **Treble boost** sliders;
  **Reverb**: None, Small room, Medium room, Large room, Medium hall, Large hall.

## Notes
- Treble boost has no native Android effect; it adds up to +6 dB to the bands at 2 kHz and above.
- Reverb/bass boost depend on the device's audio effect support.
- Edit tags stores changes inside the app (the MP3 file is not rewritten). Lyrics tab is a placeholder.
- The script-style font is `FontFamily.Cursive`; put a .ttf in `res/font` and edit `Theme.kt` for the exact look.
- No `gradlew` is bundled: Android Studio downloads Gradle 8.9 itself from `gradle/wrapper/gradle-wrapper.properties`.
- This code has not been compiled yet (no Android SDK in the build sandbox). If Android Studio reports an error on first sync/build, send it over and it can be fixed quickly.

## Building the APK with GitHub (no Android Studio needed)
Push this project to a GitHub repo. The workflow in `.github/workflows/build-apk.yml` builds `Resonance.apk` on every push to main.
Download it from the run's "Artifacts" section (Actions tab), or tick "publish" on a manual run / push a tag like `v1.0` to get it as a Release.
The APK is debug-signed, which installs fine on any phone (allow "install unknown apps").
