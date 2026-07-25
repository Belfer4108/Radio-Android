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

### Create a private release key

Create the key outside the repository or in a local ignored `keystore/` directory:

```powershell
New-Item -ItemType Directory -Force keystore
keytool -genkeypair `
  -v `
  -keystore keystore/release-key.jks `
  -storetype JKS `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -alias release
```

Use a strong password and store the `.jks` file in a private backup location, for example an encrypted drive or password manager attachment. Do not store the only copy inside the Git working directory.

All future app updates for the same `applicationId` must be signed with the same release key. If you lose the key, users will not be able to install future APKs as updates over the old APK.

Local `keystore.properties` example:

```properties
storeFile=keystore/release-key.jks
storePassword=your-private-password
keyAlias=release
keyPassword=your-private-password
```

### Encode the keystore for GitHub Actions

GitHub Actions receives the keystore as a Base64 secret. On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\release-key.jks")) | Set-Clipboard
```

This copies the Base64 value to the clipboard. Paste it only into GitHub Secrets.

### GitHub Secrets

In the GitHub repository, open:

`Settings -> Secrets and variables -> Actions -> New repository secret`

Add exactly these four secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64 text generated from `release-key.jks`.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias, for example `release`.
- `ANDROID_KEY_PASSWORD`: key password.

The workflow reconstructs `keystore/release-key.jks` and `keystore.properties` during the build. It does not print secrets to logs.

## GitHub Releases

The release workflow is defined in `.github/workflows/android-release.yml`.

It runs:

- manually from the GitHub Actions tab through `workflow_dispatch`;
- automatically when a tag matching `v*` is pushed, for example `v1.0.0`.

The workflow:

- checks out the repository;
- configures JDK 17;
- runs Gradle tests with `./gradlew test`;
- builds a signed release APK with `./gradlew assembleRelease`;
- uploads the APK as a workflow artifact;
- for tags, creates a GitHub Release and attaches the APK.

The APK name is generated as:

```text
Radio-Internetowe-v<version>.apk
```

For a tag `v1.0.0`, the file will be:

```text
Radio-Internetowe-v1.0.0.apk
```

### Versioning

Before every public release, update both values in `app/build.gradle.kts`:

```kotlin
versionCode = 2
versionName = "1.0.1"
```

Rules:

- `versionCode` must increase for every Android update.
- `versionName` is the user-visible version.
- Keep `applicationId = "com.radiopolska"` unchanged so new APKs install as updates.

### Create and push a release tag

After committing the version change:

```powershell
git tag v1.0.0
git push origin main
git push origin v1.0.0
```

GitHub Actions will build the signed APK and create a Release for the tag.

### Download and install the APK

Users can download the APK from:

`GitHub -> repository -> Releases -> selected version -> Assets`

To install:

1. Download `Radio-Internetowe-vX.Y.Z.apk` on the Android device.
2. Open the file.
3. If Android asks, allow installation from the browser/file manager used to open the APK.
4. Confirm installation.

For updates, the APK must be signed with the same release key as the previous installed version.

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
