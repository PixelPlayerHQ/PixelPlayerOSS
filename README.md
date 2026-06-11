# PixelPlayerOSS

<p align="center">
  <img src="assets/icon.png" alt="PixelPlayerOSS icon" width="128"/>
</p>

<p align="center">
  <strong>A local-first, FOSS Android music player built with Kotlin, Jetpack Compose, and Material 3.</strong>
</p>

<p align="center">
  <a href="https://github.com/lostf1sh/PixelPlayerOSS/releases/latest">
    <img src="https://img.shields.io/github/v/release/lostf1sh/PixelPlayerOSS?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest release">
  </a>
  <a href="https://f-droid.org/packages/com.lostf1sh.pixelplayeross/">
    <img src="https://img.shields.io/f-droid/v/com.lostf1sh.pixelplayeross?logo=fdroid&style=for-the-badge&label=F-Droid" alt="F-Droid version">
  </a>
  <a href="https://github.com/lostf1sh/PixelPlayerOSS/releases">
    <img src="https://img.shields.io/github/downloads/lostf1sh/PixelPlayerOSS/total?logo=github&style=for-the-badge" alt="Total downloads">
  </a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="MIT license">
</p>

<p align="center">
  <img src="assets/screenshot1.jpeg" alt="PixelPlayerOSS home screen" width="205"/>
  <img src="assets/screenshot2.jpeg" alt="PixelPlayerOSS now playing screen" width="205"/>
  <img src="assets/screenshot3.jpeg" alt="PixelPlayerOSS library screen" width="205"/>
  <img src="assets/screenshot4.jpeg" alt="PixelPlayerOSS lyrics screen" width="205"/>
</p>

## What It Is

PixelPlayerOSS is an Android music player maintained by [@lostf1sh](https://github.com/lostf1sh). It focuses on local playback, self-hosted music libraries, expressive Material 3 UI, and user-controlled online lookups.

The app works offline by default. Optional online services are disabled until you enable them in setup or settings.

Package name: `com.lostf1sh.pixelplayeross`

## Why This Exists

PixelPlayerOSS keeps the player FOSS-oriented and removes integrations that are not part of that direction.

Removed integrations include Telegram, NetEase, QQ Music, Google Drive, Gemini, Cast, Wear OS, Play Store billing, Firebase, Crashlytics, and Google Play Services runtime dependencies.

Cloud playback is limited to self-hosted sources: Navidrome/Subsonic and Jellyfin.

## Features

| Area | Highlights |
| --- | --- |
| Playback | Media3 playback engine, FFmpeg support, gapless playback, crossfade, custom transitions, queue controls, shuffle, repeat, sleep timer, external file playback |
| Library | Local scanning for MP3, FLAC, AAC, OGG, WAV, M4A, albums, artists, genres, folders, favorites, playlists, stats, metadata editing |
| Self-hosted | Navidrome/Subsonic login, sync, streaming, artwork, Jellyfin login, sync, streaming, artwork |
| Lyrics | Embedded lyrics, local `.lrc` files, lyrics import/editing, optional LRCLIB lookup |
| Artwork | Local artwork, album-art palette extraction, optional Deezer artist image lookup |
| UI | Jetpack Compose, Material 3, dynamic color, light/dark themes, Glance widgets, animated player surfaces |
| Backup | Preferences, playlists, favorites, lyrics, stats, and app state backup/restore |

## Online Services

PixelPlayerOSS separates offline playback from network lookups.

| Service | Purpose | Default |
| --- | --- | --- |
| Navidrome/Subsonic | Self-hosted library sync and streaming | User login required |
| Jellyfin | Self-hosted library sync and streaming | User login required |
| LRCLIB | Search online lyrics when local or embedded lyrics are missing | Off |
| Deezer | Fetch missing artist artwork and cache it locally | Off |

LRCLIB and Deezer can be enabled during first-run setup or later from `Settings > Music Management > Optional online services`.

## Requirements

| Requirement | Version |
| --- | --- |
| Android | 11 or newer, API 30+ |
| JDK | 21 |
| Android SDK | compile/target 37 |

## Build From Source

Clone the repository:

```sh
git clone https://github.com/lostf1sh/PixelPlayerOSS.git
cd PixelPlayerOSS
```

Build the debug APK:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug
```

Build one universal debug APK for local installation:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug -Ppixelplayer.enableAbiSplits=false
```

Build a universal unsigned release APK suitable for F-Droid verification:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=false -Ppixelplayer.disableReleaseSigning=true
```

Run unit tests:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest
```

Generate the baseline profile with a connected device or emulator:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :baselineprofile:generateBaselineProfile
```

## Download

PixelPlayerOSS is available on F-Droid:

<a href="https://f-droid.org/packages/com.lostf1sh.pixelplayeross/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
</a>

GitHub releases are available at:

```text
https://github.com/lostf1sh/PixelPlayerOSS/releases
```

Obtainium app id:

```text
com.lostf1sh.pixelplayeross
```

Public releases are planned on a regular weekly cadence when `main` passes the release checklist.

F-Droid listing metadata lives in `fastlane/metadata/android/en-US`; build/release notes for F-Droid are in [docs/FDROID.md](docs/FDROID.md).

> Note: F-Droid builds and signs its own APKs from source, so they may lag behind GitHub releases while the new version works through the F-Droid build cycle. F-Droid and GitHub APK signatures differ — switching between the two requires an uninstall/reinstall.

## Project Structure

```text
app/src/main/java/com/lostf1sh/pixelplayeross/
- data/             Room, repositories, preferences, services, workers
- di/               Hilt modules and qualifiers
- presentation/     Compose screens, components, navigation, ViewModels
- ui/               Theme and Glance widgets
- utils/            Shared utilities

baselineprofile/      Macrobenchmark and baseline profile generation
```

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose |
| Design | Material 3 |
| Playback | AndroidX Media3, ExoPlayer, FFmpeg |
| Database | Room |
| Dependency Injection | Hilt |
| Preferences | DataStore |
| Background Work | WorkManager |
| Networking | Retrofit, OkHttp |
| Images | Coil |
| Metadata | TagLib |

## Contributing

Contributions are welcome. Open an issue or pull request with a focused change and include test/build results when possible.

Useful local checks:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest
```

Release process: [docs/RELEASE.md](docs/RELEASE.md)

F-Droid notes: [docs/FDROID.md](docs/FDROID.md)

Privacy policy: [PRIVACY.md](PRIVACY.md)

Security policy: [SECURITY.md](SECURITY.md)

## License

PixelPlayerOSS is licensed under the [MIT License](LICENSE).

Distributed APKs include third-party components under their own licenses. In particular, the optional FFmpeg decoder dependency `org.jellyfin.media3:media3-ffmpeg-decoder` is GPL-3.0; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

<p align="center">
  Maintained by <a href="https://github.com/lostf1sh">lostf1sh</a>
</p>
