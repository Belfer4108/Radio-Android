# Radio Polska

Radio Polska is an Android internet radio application built with Kotlin, Jetpack Compose, and AndroidX Media3. It provides Polish radio station playback, background media controls, recording tools, a radio alarm, timer, equalizer, lock-screen controls, and utilities for managing recorded audio.

## Features

- Internet radio playback with Media3/ExoPlayer.
- Background playback with persistent media notification and lock-screen controls.
- Station browser with search, favorites, history, filters, regions, list/grid view, and sorting.
- Recording of currently playing radio streams.
- Recordings manager with playback, multi-select, sharing, deletion, MP3 copy, ringtone export, and MP3 trimming.
- MP3 trim editor with waveform, start/end handles, preview, keep/remove selection modes, and fade in/out.
- Radio alarm with station selection, repeat days, snooze, auto stop, wake volume, and optional volume ramp.
- Sleep timer.
- Transfer usage estimates split by Wi-Fi, mobile data, and other connections.
- Equalizer presets and manual band control.
- Colorofon/torch effect based on audio analysis.
- Car mode with large playback controls.
- Backup export for app settings.
- Multiple visual skins.
- In-app Help / FAQ.

## Screenshots

No screenshots are currently included in this repository. Add images later under `docs/screenshots/` and reference them here, for example:

```markdown
![Main screen](docs/screenshots/main.png)
```

## Requirements

- Android Studio with Android Gradle Plugin support.
- JDK 17 or the JBR bundled with Android Studio.
- Android SDK with compile SDK 36 installed.
- Android device or emulator running Android 6.0+ (minSdk 23).

The project uses the Gradle wrapper, so a separate Gradle installation is not required.

For command-line builds, make sure Android SDK is available through Android Studio or `ANDROID_HOME`.

Windows example:

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
```

## Build

Clone the repository and build a debug APK:

```powershell
.\gradlew.bat assembleDebug
```

On macOS/Linux:

```bash
./gradlew assembleDebug
```

The debug APK will be generated under:

```text
app/build/outputs/apk/debug/
```

## Release signing

Release signing keys are intentionally not committed.

To build a signed release APK locally:

1. Create a private keystore.
2. Copy `keystore.properties.example` to `keystore.properties`.
3. Update `keystore.properties` with your local keystore path, alias, and passwords.
4. Build:

```powershell
.\gradlew.bat assembleRelease
```

Never commit `keystore.properties`, `keystore/`, `.jks`, `.keystore`, `.p12`, or private key files.

If no `keystore.properties` file exists, the project remains suitable for public source sharing and debug builds.

## Permissions

The app declares permissions needed for radio playback, background media service, alarms, notifications, recording helpers, colorofon, and ringtone export:

- Internet and network state for streaming and transfer analysis.
- Foreground media playback service and wake lock for stable background playback.
- Notifications for playback controls and lock-screen media controls.
- Exact alarms for the radio alarm.
- Camera and microphone for the colorofon feature.
- Legacy external storage write permission for older Android versions.

Some Android devices require manual battery settings for stable background playback:

- Allow notifications for the app.
- Allow lock-screen notifications and media controls.
- Disable battery optimization or set the app to unrestricted/background allowed.
- Allow exact alarms if prompted.

Avoid using Android's "Force stop" action if you expect the radio alarm to wake the app later.

## Project structure

```text
app/src/main/java/com/radiopolska/
  MainActivity.kt                  Compose UI and in-app modules
  alarm/RadioAlarm.kt              Alarm storage, scheduling, receiver
  data/RadioStation.kt             Station model
  data/RadioStations.kt            Station catalog
  player/RadioPlaybackService.kt   Media3 playback, recording, notification, audio effects
  ui/theme/                        Compose theme and skins
```

## Security notes

Before publishing, verify that no private files are staged:

```powershell
git status --short
git grep -n -i "password\|secret\|token\|api_key\|apikey\|private key"
```

This repository is prepared to ignore local signing keys and machine-specific Android Studio files.

## License

No license file has been added yet. Add a license before publishing if you want to define how others may use or modify the code.
