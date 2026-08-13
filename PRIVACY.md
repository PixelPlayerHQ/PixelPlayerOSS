# Privacy Policy

PixelPlayerOSS is local-first and does not include analytics, advertising SDKs, Firebase, Crashlytics, or Google Play Services runtime dependencies.

## Data Stored On Device

The app stores music library metadata, playlists, favorites, lyrics, preferences, playback state, artwork cache, and optional backup/restore data on the device.

## Network Access

Network features are optional and user-controlled:

- Navidrome/Subsonic and Jellyfin are used only after the user signs in to a self-hosted server. Those servers may receive the authentication, library, playback state, and play history requests needed for their protocols.
- MusicBrainz lookup runs only when the user requests enrichment for a track. The app sends that track's title and, when available, artist, album, and duration to the public MusicBrainz search API; applying a result stores the selected MusicBrainz identifiers locally.
- LRCLIB lyric lookup is used only when online lyrics are enabled.
- Deezer artist artwork lookup is used only when online artist images are enabled.
- ListenBrainz scrobbling is optional and disabled by default. It activates only after the user connects a ListenBrainz account with their own user token. While connected, the app submits listening activity (track title, artist, album, duration, listen timestamps, and MusicBrainz identifiers when available) to the configured ListenBrainz server for the playback sources the user has enabled — listenbrainz.org by default, or a user-supplied custom URL for self-hosted ListenBrainz-compatible servers such as Maloja; per-source toggles cover local files, Navidrome/Subsonic, and Jellyfin playback. Disconnecting stops submissions and deletes any queued listens. Last.fm is not supported.

Server credentials and preferences are stored locally. The app does not sell or share user data.

## Files And Media

PixelPlayerOSS requests media/file permissions to scan and play local music, read artwork, edit metadata, and export/import user backups. Optional Navidrome/Subsonic and Jellyfin downloads are stored in the app's private files area for offline playback and are excluded from Android cloud backup and device transfer.

## Crash Reports

The app keeps crash logs locally for troubleshooting. It does not automatically upload crash reports.

## Contact

Open a GitHub issue for privacy questions that do not contain sensitive personal data.
