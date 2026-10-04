# Release Checklist

PixelPlayerOSS releases are shipped from `main` after the release candidate passes local checks and a basic device smoke test.

## Versioning

Version values live in `gradle.properties`:

```properties
APP_VERSION_NAME=0.1.0
APP_VERSION_CODE=1
```

For every public release:

1. Update `APP_VERSION_NAME`.
2. Increment `APP_VERSION_CODE`.
3. Move the relevant `CHANGELOG.md` entries from `Unreleased` to the release version.
4. Tag the release as `v<APP_VERSION_NAME>`, for example `v0.1.0`.

## Required Local Checks

Run these before tagging:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=false -Ppixelplayer.disableReleaseSigning=true
```

For split APK artifacts, use:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=true
```

## Signing

Release signing is driven by an untracked `keystore.properties` file at the repository root.

Supported keys:

```properties
storeFile=/absolute/or/repo-relative/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` is optional when the release keystore is available as `vz-pixelplay.jks` at the repository root. `storePassword`, `keyAlias`, and `keyPassword` are required for signing. If signing properties or the keystore file are missing, release builds are unsigned. CI workflows create temporary CI signing keys for artifacts; those are not official release keys.

For F-Droid-compatible unsigned verification builds, pass `-Ppixelplayer.disableReleaseSigning=true` even when local signing files exist.

## Device Smoke Test

Install the release candidate and verify:

1. First launch and setup complete.
2. Local library scan finds music.
3. Playback starts, pauses, resumes, skips, and survives backgrounding.
4. Full player opens and closes smoothly.
5. Widget controls still reach the playback service.
6. Navidrome and Jellyfin login screens open.
7. Backup export flow creates a file.

## In-App Updates

The Updates screen (`data/update/`) reads two sources, so keep their formats stable:

- **Stable:** `https://f-droid.org/api/v1/packages/<applicationId>` (`suggestedVersionCode`) and the F-Droid APK at `https://f-droid.org/repo/<applicationId>_<versionCode>.apk`. The app treats any version name without a `-` suffix as an F-Droid install.
- **Alpha:** GitHub prereleases tagged `v<X.Y.Z>-alpha.<N>` with assets named `PixelPlayerOSS-<X.Y.Z>-alpha.<N>-<abi>.apk`, as produced by `.github/workflows/alpha-release.yml`. Renaming tags or assets there breaks alpha update discovery.

F-Droid and alpha builds are signed with different keys. The updater installs only same-channel releases whose signer matches the installed app, and walks users through uninstalling to switch channels.

## Publishing

1. Ensure `main` is clean and pushed.
2. Create a tag: `git tag v<APP_VERSION_NAME>`.
3. Push the tag: `git push origin v<APP_VERSION_NAME>`.
4. Create a GitHub release from the tag.
5. Attach APK artifacts and paste the changelog section.

## F-Droid Metadata

Before submitting a tagged release to F-Droid-compatible app stores:

1. Update `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Verify the unsigned universal release build from [FDROID.md](FDROID.md).
3. Check `PRIVACY.md` still matches the optional network services present in the app.
4. Create source archives from git, not from the working tree, so ignored local artifacts are excluded.
