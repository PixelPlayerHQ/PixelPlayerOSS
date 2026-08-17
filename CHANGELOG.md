# Changelog

All notable changes to PixelPlayerOSS will be documented in this file.

## [0.3.0] - 2026-08-15

### Added
- Optional ListenBrainz scrobbling, disabled by default. Connect a ListenBrainz account with a user token from the Accounts screen; listens that reach the ListenBrainz threshold (4 minutes or half the track, whichever is lower) queue offline and submit with retry, with per-source toggles for local files, Subsonic, and Jellyfin playback. Now-playing status is reported while scrobbling is enabled, and disconnecting deletes any queued listens. An optional custom server URL scrobbles to self-hosted ListenBrainz-compatible servers such as Maloja instead of listenbrainz.org.
- Offline downloads for Navidrome/Subsonic and Jellyfin tracks, with per-track progress, retry/removal actions, album downloads, app-private storage, and transparent local playback when a download is available, plus a dedicated download management screen.
- On-demand MusicBrainz enrichment with ranked result selection and local recording, release, and artist identifiers. Existing metadata is preserved except for missing or unknown values.
- Translations for twelve languages with an in-app language picker, including Turkish and Simplified Chinese (`zh-rCN`). The selected language now also applies to the login and external player screens.
- Audio bookmarks for saving your place inside long tracks such as mixes, audiobooks, and DJ sets.
- Offline natural-language playlist creation: describe the playlist you want and it is built locally, with no network calls.
- Passphrase-protected backups.
- Search in Settings, so a toggle can be found without digging through categories.
- Tempo-matched crossfades and smoother play/pause transitions.
- Opt-in performance recorder for debugging lag reports.

### Changed
- Listening-stats hour labels follow the system 12/24-hour clock format.
- Cached album art is size-capped and artwork extraction is skipped during library scans, reducing memory use and scan time.
- Alpha releases are skipped for docs-only and CI-only changes; the standalone phone APK workflows were dropped in favour of alpha releases.

### Fixed
- Cloud artwork now reaches media notifications, the lock screen, and external media controllers.
- Offline download cancellation races that could leave partial files or stuck progress.
- Restored queues no longer keep dead local proxy links.
- Cloud streams that cannot be resolved now surface a clear error instead of failing silently.
- Synced embedded lyrics are preferred, and the lyrics API rate limiter no longer throttles valid requests.
- Shuffle changes stay in sync with the media session.
- Folder filters stay in sync with the library, and comma-separated genre tags are split correctly.
- The song info sheet no longer overflows on small screens.
- Bookmark buttons that were still hardcoded English are localized.
- The themed launcher icon is restored.

### Security
- Credentials are redacted from debug network logs.

### Removed
- Android Auto media-library browsing and discovery. Standard MediaSession playback controls for notifications, lock screen, Bluetooth devices, and other system surfaces remain available.

## [0.2.0] - 2026-07-17

### Added
- Navidrome library selector for servers that expose more than one music library.
- The Artists tab and cloud albums are grouped by album artist.
- Source code and F-Droid links in the About screen, plus GitHub Sponsors metadata.

### Changed
- Material 3 Expressive pass: motion scheme, wavy progress indicators, and shape morphs.
- New app icon and redesigned header.
- Leaner R8 rules and regenerated baseline profiles.
- Coroutines, Flow, and Compose hygiene pass, plus dead-code and deprecated-API cleanup.
- Dependency bumps across Material 3, Compose, OkHttp, core-ktx, and lifecycle.

### Fixed
- Local server connections on Android 17.
- Plain HTTP is allowed for local Navidrome and Jellyfin servers, including Tailscale and VPN hosts.
- GitLab mirror repository guard after the organisation move.

### Security
- User-installed CAs are trusted so self-signed cloud servers work without disabling verification.
- State-changing media session commands are restricted to trusted clients, and artwork sharing stays private behind explicit URI grants.

## [0.1.0] - 2026-06-09

### Initial release
- First public FOSS release of PixelPlayerOSS, an OSS-focused Android music player.
- Includes local music playback, playlists, favorites, lyrics, listening stats, dynamic Material 3 theming, widgets, and backup/restore.
- Keeps self-hosted library support for Navidrome/Subsonic and Jellyfin, plus optional LRCLIB lyrics and Deezer artist artwork lookups.

### Removed for FOSS
- Removed non-FOSS and Google Play oriented integrations: Telegram, NetEase, QQ Music, Google Drive, Gemini, Cast, Wear OS, Play Store billing, Firebase, Crashlytics, and Google Play Services runtime dependencies.
- Removed public scrobbling integrations such as Last.fm and ListenBrainz; self-hosted Navidrome/Subsonic playback reporting remains scoped to the user's own server.
- Removed bundled translations and the in-app language selector for the first FOSS release; the initial source release ships with English resources only.
- Removed release paths that depended on local/private signing artifacts, dummy signing values, or app-store-only assumptions.

### Release readiness
- Added F-Droid metadata, Fastlane store metadata, dependency/license documentation, privacy notes, security notes, and contributor guidance.
- Release builds now stay unsigned when local signing keys are absent, and `pixelplayer.disableReleaseSigning=true` forces unsigned verification builds even on a maintainer machine.
- Documented third-party asset and dependency licenses, including native/binary Maven artifacts and JitPack source trails.

### Security and privacy
- The loopback cloud-stream proxy now requires a per-session token so other apps on the device cannot stream the user's cloud library by guessing local proxy URLs.
- Backup restore now ignores preference keys owned by dedicated module handlers, preventing crafted global-settings payloads from bypassing module validation.
- Release logging is tightened so HTTP request headers and remaining raw Android logs do not bypass the Timber release filter.

### App polish included in this FOSS release
- Added smart playlist persistence, duplicate-track scanning, playback speed control, clearer playback/sync failure messages, and retry actions on album/artist detail failures.
- Improved accessibility for toggle states and song row actions.
