# Contributing to PixelPlayerOSS

Thanks for your interest in improving PixelPlayerOSS! This guide covers the
conventions that aren't obvious from the code alone. For a deeper architectural
overview, see [CLAUDE.md](CLAUDE.md) (written for AI assistants but accurate for
humans too).

## Getting started

```sh
git clone https://github.com/lostf1sh/PixelPlayerOSS.git
cd PixelPlayerOSS

# Universal debug APK for local installation
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug -Ppixelplayer.enableAbiSplits=false
```

Requirements: **JDK 21**, Android **compile/target SDK 37**, min SDK 30 (Android 11+).
Kotlin code style is `official`.

## Local checks before opening a PR

Please run these and include the results in your PR description:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest
```

Run a single test class/method:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest \
  --tests "com.lostf1sh.pixelplayeross.SomeTestClass.someMethod"
```

> Unit tests use **JUnit Jupiter** (`useJUnitPlatform()`), not JUnit 4 — use
> `org.junit.jupiter.api.Test`, not `org.junit.Test`.

## Conventions that matter

These are easy to get wrong and will come up in review:

- **Slice `PlayerUiState`, never collect the whole thing.** It updates on every
  position tick, so collecting all ~30 fields in a screen causes recomposition
  storms. Map to a small slice + `distinctUntilChanged()`. The reference pattern
  is `PlayerUiSheetSliceV2` in `UnifiedPlayerSheetV2`. See
  [app/performance_analysis.md](app/performance_analysis.md) before touching list
  rendering, animations, or `PlayerUiState`.
- **Prefer `ImmutableList<T>` over `List<T>` in `@Composable` params** for
  strong-skipping (`kotlinx.collections.immutable`). But `.toImmutableList()` is
  `O(n)` — don't call it on every recomposition. New widely-used domain models go
  in `app/compose_stability.conf`.
- **Navigate with `navController.navigateSafely(...)`** (from
  `NavControllerExtensions.kt`), never `navController.navigate(...)` directly — it
  retries on the `IllegalStateException` thrown by rapid taps.
- **Log with Timber, not `android.util.Log`.** Debug builds plant a `DebugTree`;
  release uses `ReleaseTree` (WARN/ERROR/WTF only). Raw `Log.x` bypasses the
  release filter.
- **Room schema changes require a migration** (schemas are exported to
  `app/schemas`). Destructive migration is only allowed in debug builds.
- **`@Singleton` is already the dominant scope** — be cautious about adding more
  (flagged as a low-end memory risk in the perf audit). Prefer `@ViewModelScoped`
  for single-consumer state.
- Keep user-facing strings in `res/values/strings*.xml` (`stringResource(...)`),
  not hardcoded in Composables.

## Pull requests

- Keep changes focused; one logical change per PR.
- Describe what changed and why, and include build/test results.
- Do **not** add a `Co-Authored-By:` trailer to commits or PR bodies.

## Reporting bugs

Open an issue using the bug-report template. Please include your app version,
device/Android version, and which music source (local / Navidrome / Jellyfin) is
involved.
