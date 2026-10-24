# Changelog

All notable changes to PixelPlayerOSS will be documented in this file.

## [Unreleased]

### Fixed
- Navidrome/Subsonic (including Gonic) and Jellyfin covers no longer hold up other artwork while a slow server answers. A cover download kept one of the threads that decode every image in the app until the server replied, even after its list item had scrolled away, so a burst of slow covers left the rest of the library on placeholders. Downloads now run off those threads and stop as soon as their cover leaves the screen, and a cover shown in several places at once is downloaded once instead of once per place ([#118](https://github.com/PixelPlayerHQ/PixelPlayerOSS/issues/118)).

## [0.4.0] - 2026-10-17

### Added
- An Updates screen (Settings → Updates, also linked from Settings → About) that checks for new versions on a chosen channel: Stable pulls from F-Droid, Alpha from the GitHub prereleases built for every merged change. Same-channel updates download and install in the app; switching channels walks through backing up, downloading the other build, and uninstalling, since F-Droid and GitHub builds are signed with different keys. An optional daily check notifies when an update is available (on by default for alpha builds, off for F-Droid builds).
- Smart Mix composer that replaces the "Describe it" dialog. Describe a mix, adjust its energy and length, preview it, then play it, save it as a playlist, or use it as today's Daily Mix (with undo). It suggests mixes based on your library and listening habits and remembers recent requests. History can be cleared in Settings. Everything still runs offline.
- The offline mix engine handles more requests: include or exclude artists and genres, "rock but not metal", "only" requests, "like <playlist>", spelled-out decades, and mood words nobody listed by hand. It ranks results by your listening habits and keeps mixes varied. Songs without genre tags get a genre from their artist, using a bundled Wikidata (CC0) table.
- AMOLED theme preset with true-black surfaces.
- Lyrics settings screen with an adjustable lyrics font size.
- Multi-selection in Search for songs, albums, playlists, and genres.
- Optional swipe-to-skip on the mini player.
- Audio output picker in the player (phone speaker, wired, USB, or a Bluetooth device).
- Audio bookmarks are now part of backups, can be selected and deleted in bulk, and reconnect to their songs after a rescan, even when the scan gives their old song id to a different song.
- Excluded folders now open on their own screen, with a list of every audio folder.
- In-app licenses screen under About.
- Battery optimization exemption prompt in setup and Playback settings.
- "Show scrollbar" setting, grid view for the Playlists tab, and customizable sidebar shortcuts.
- Redesigned album and artist headers, segmented library lists, a new Stats screen with Heavy Rotation drill-down, and an animated splash.
- Playback speed in exact 0.05× steps from 0.25× to 3×, with − and + buttons, presets, and a typed value with two decimals. The same controls open from a new Speed button in the Next up queue, which is highlighted while the speed isn't 1× ([#145](https://github.com/PixelPlayerHQ/PixelPlayerOSS/issues/145)).
- "Auto-play on skip" setting (Settings → Behavior, on by default): Next, Previous, and the mini player's swipe-to-skip start playback when you skip while paused.
- Hide library tabs you don't use from the Reorder library tabs sheet, and bring them back from its Hidden tabs group.
- Multi-selection on genre pages.
- Choose the app language from the setup welcome page.
- Five new Home collage patterns.
- The Next up queue follows the predictive back gesture on Android 14 and newer.

### Changed
- The playback service now runs the sleep timer. Duration timers no longer drift while audio offload lets the CPU sleep. End-of-track pauses exactly at the end of the song, even with crossfade on. Counted play restores your previous repeat mode.
- Shuffle All queues up to 10,000 songs instead of 500.
- On devices where audio offload stalls, playback falls back after 1.5 seconds instead of 4, and offload stays off until the next system update.
- Library tabs keep their scroll position when you come back to them.
- Better online lyrics matching for non-Latin titles.
- Settings search scrolls to the matching setting and highlights it once. It also finds the playback speed (with words like "podcast"), Auto-play on skip, crossfade duration, ReplayGain mode, the lyrics auto-hide delay, the artist parsing options, and Listening stats.
- The library toolbar animates between its Shuffle, New playlist, and folder modes, and the storage filter button is tinted while a filter is active.
- Sleep-timer sliders tick as you drag, and equalizer knobs no longer buzz continuously.
- Smoother player expand and collapse: the full player keeps its layout and the mini player stays aligned with its card, including next to the navigation rail.
- Navidrome/Subsonic logins accept server addresses typed without http:// or https://, using http for local and VPN hosts and https otherwise, as Jellyfin already did.
- Performance reports include library scan, metadata, and artwork timings.

### Fixed
- Navidrome/Subsonic and Jellyfin album artwork no longer gets stuck on the placeholder after a few songs. The player loaded cloud covers through the app's own artwork content provider, and concurrent loads could exhaust the image decoder slots that provider waited on, freezing every artwork load until the app restarted. Server error responses are also no longer cached as cover images, and concurrent cover downloads no longer corrupt each other's cache file ([#138](https://github.com/PixelPlayerHQ/PixelPlayerOSS/issues/138)).
- System media controls can load album artwork through the app's artwork URI again. System UI reads that URI without connecting as a media controller, so it never received the per-track read grant and was refused on every track; privileged media surfaces (holders of `MEDIA_CONTENT_CONTROL`) may now open the artwork the session currently shows, while every other cover, and every other app, still needs the explicit grant. Stock Android fell back to the session bitmap, but System UIs without that fallback could show no artwork.
- Jellyfin playlists no longer go missing on Jellyfin 10.10 and newer, where playlists can hold mixed content and audio playlists are often reported without a media type.
- With crossfade on, queues longer than 200 songs no longer shrink to a 200-song window after a transition, and the shortened queue is no longer restored after the app is killed.
- Restoring a backup before the library scan finishes no longer drops songs from playlists. They are added back once the scan finds them, without undoing changes you made to those playlists in the meantime. Transition rules restored on another device now point at the right songs.
- Correctly accented titles such as "Château" or "CORAÇÃO" are no longer turned into garbled text during library scans. Libraries scanned by an earlier version are checked once after updating, so titles that were already garbled are repaired.
- Reordering the queue no longer flickers, and a swiped-away queue item no longer jumps back before it is removed.
- Metadata edits work on files that can only be written through the system's media permission. If saving the edited file is interrupted, the app keeps a full copy and restores the song instead of leaving it cut off.
- Songs whose files were already deleted can now be removed from the library.
- Empty library tabs no longer stay stuck on the loading placeholder.
- File info (bitrate, sample rate) no longer disappears when you toggle shuffle or repeat.
- Tracks no decoder can play now pause and show a message, instead of playing silence while the progress bar moves.
- The widget's "next up" artwork follows shuffle and repeat order.
- When a song from elsewhere is queued between playlist songs, it now gets the playlist's default transition. A song pair's custom transition only plays while the two songs are still next to each other, and shuffling no longer gives playlist transitions to a song you queued again by hand.
- The scrollbar no longer keeps outdated state or reserves space when the list can't scroll. The folder picker no longer lists folders that contain no audio.
- The "locate current song" button no longer appears in Liked for songs that aren't liked, or in Folders for folders that don't contain the current song.
- Back closes the settings search before leaving Settings.
- Kugou word-by-word lyrics get correct spacing. Plain lyrics no longer have romanization written into the text. Large Apple Music TTML files and `<smil>`-rooted TTML import correctly. Titles containing `$`, `#`, or `%` are no longer cut short when searching for lyrics.
- Recently Played hour labels follow the system 24-hour setting, and the Search filter chips are translated.
- Updated Netty to 4.2.28 for security fixes.
- Pressing back with the Now Playing screen or the Next up queue open inside a folder in the Folders tab now closes the player instead of leaving the folder ([#136](https://github.com/PixelPlayerHQ/PixelPlayerOSS/issues/136)).
- Albums copied to the device while music was playing, while a library scan was running, or while the app was in the background no longer show only some of their songs. Syncs started by new files, pull-to-refresh, and Refresh library no longer wait up to about 15 minutes for playback to stop, and changes that arrive during a scan get a follow-up scan ([#126](https://github.com/PixelPlayerHQ/PixelPlayerOSS/issues/126)).
- Listening Stats are easier to find: the Home card is always shown, with loading and "No listening data yet" states, and the Home menu button opens a sidebar with a Listening Stats shortcut ([#142](https://github.com/PixelPlayerHQ/PixelPlayerOSS/issues/142)).
- Previous goes back to the previous song during the first 10 seconds of a track. After 3 seconds it used to restart the current song.
- Adding songs to a playlist or creating one no longer breaks library sorting, search, album colours, the seek bar, and the sleep timer until the app restarts.
- Closing the player that opens audio files from other apps no longer breaks library sorting, search, album colours, the seek bar, and the sleep timer in the app until it restarts.
- Closing the app while music keeps playing no longer splits the current song's listening session in your stats.
- Reordering a playlist no longer removes songs that are currently unavailable (excluded folder, unmounted storage, signed-out server, or not scanned yet). Deleting a playlist or removing songs from it also removes their saved transition rules.
- Deleting several selected playlists in Library or Search asks for confirmation first. Select all in Liked selects every liked song, not only the ones loaded so far.
- The Songs tab's locate button works again, Locate on Liked scrolls to the playing song's place in the Liked list, and Locate no longer moves the neighbouring tab. The Folders locate button keeps working after moving between folders, and the Library header and back handling follow the tab on screen during fast swipes.
- Rapid next/previous taps in the full player no longer land on an intermediate song or stall the album carousel, and the player colours keep up. Paging the carousel with TalkBack, Switch Access, the keyboard, or a mouse wheel changes the song again.
- The full player's album carousel no longer keeps re-measuring on every frame after it scrolls to another song.
- The full player no longer keeps redrawing on every frame after you pause it, which wasted battery while it sat paused.
- A paused full player also stops animating its flat progress wave out of sight, which kept the app busy on every screen refresh.
- Pulling the Next up queue down from its list and back up brings it back with your finger.
- Widget controls no longer flash a placeholder notification or drop the paused media notification after the app is swiped away. The main widget follows the album palette like the other widgets.
- Library scans no longer extract embedded artwork from poorly tagged files or log per-file tag details.
- Public IPv6 servers are no longer treated as local, so they no longer skip the HTTPS requirement; .home and .internal hosts count as local. Cloud streaming and offline downloads accept .ogg, .webm, .mka, and .3gp files served with video/ or application/ content types.
- The setup wizard's Next button stays above the navigation bar, and setup pages scroll on short screens and with large font sizes. Changing the app language also updates text from background work, such as backup restore progress.
- Stats weekday, month, and "Week N" labels, library sort options, collage pattern names, the bottom navigation labels, and the message shown when cloud downloads start follow the app language.
- Song counts, listening durations, and "time ago" labels are translated, with each language's plural forms (for example Russian 2 песни and 5 песен). Zero songs reads "0 Songs" instead of "0 Song".
- Lyrics sheet: square corners at rest, a loading skeleton, and the options sheet stays below the status bar.
- The playlist options sheet scrolls on small screens and in landscape, so Export stays reachable. The New playlist dialog opens with the keyboard ready, creates the playlist on Done, and ignores names made only of spaces.
- Multi-selection sheets no longer clip long translated labels, and the genre header keeps its size after rotation.
- Home and Audio bookmarks restore their scroll position when you come back to them.
- Equalizer sliders no longer draw dark fills while the equalizer is off, and checked switches in the sort sheet are readable.
- The cover editor shows a music-note placeholder instead of a broken-image icon when artwork can't be loaded.
- The playing song's scrolling title in song lists fades with its row highlight.
- The screen revealed by a predictive back gesture is dimmed every time, not only every other time after a cancelled gesture.

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
