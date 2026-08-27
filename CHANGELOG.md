# Changelog

All notable changes to this project are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this is a personal project so
versioning is informal — one running `0.1.0` until there's a reason to cut a tagged release.

## [0.1.0] — unreleased

### Added

- Core scheduling engine: exact-alarm-backed interval and time-of-day triggers, independent
  home/lock targeting with single-owner-per-target conflict arbitration, and a shuffle-without-
  repeat cycling model alongside a plain sorted-order mode.
- Two image source kinds: a linked SAF folder (top-level only) and a gallery-imported photo set
  copied into app-private storage, both behind one `ImageSource` abstraction.
- Wallpaper engine: two-pass downsampling decode, EXIF-aware crop geometry for fill/fit-blurred/
  fit-solid modes, and direct `WallpaperManager` application with per-target flags.
- Onboarding screen for the battery-optimization exemption and (Android 12+) the exact-alarm
  permission, plus an in-app Diagnostics screen surfacing live permission status and one-off
  apply/scan checks.
- Schedule list and editor UI (Clock-alarms-tab style cards with a switch), gallery-set
  management grid with import progress, per-card manual next/previous.
- Settings: light/dark/system theme selector with an optional custom accent color, and JSON
  backup/restore of schedules and settings.
- A hand-drawn adaptive launcher icon (`docs/app-icon.svg`, ported to `VectorDrawable`).
- Unit test coverage for all pure-Kotlin domain logic: shuffle exhaustion and reconciliation,
  schedule trigger math (including DST transitions), target arbitration invariants, crop
  geometry, backup round-tripping, and media-import dedupe/collision logic.
- `docs/` covering architecture, design decisions, the scheduling reliability story,
  development/build instructions, and troubleshooting.
- A local release-signing keystore (gitignored) with a `keystore.properties`-driven
  `signingConfig`, so `assembleRelease` produces a signed, minified APK.

### Fixed

- A gallery-photo import crash where a failed metadata query for one photo aborted the whole
  import batch, and a bug where a folder's SAF permission could be released while the editor was
  still using it.
- The actual reported gallery-picker crash: a `LazyVerticalGrid` nested inside a
  `Modifier.verticalScroll` column, which Compose rejects at measure time. Replaced with a
  manually chunked, non-lazy grid.
- `ApplyWallpaperUseCase.peek()`'s simulated "next" look-ahead reshuffled with a fresh,
  wall-clock-based random seed on every call instead of the shuffle bag's own persisted seed,
  making the schedule card's next-image preview change on repeated reads even when nothing about
  the schedule's actual state had advanced.

### Removed

- **Home-screen widget.** Built (`RemoteViews`-based `AppWidgetProvider`, scope-bound to Home/
  Lock/Both rather than a fixed schedule) and then removed after it kept rendering
  inconsistently — every real update rebuilt the widget twice, and the two rebuilds could compute
  different "next" preview images for the same underlying state, which read as a persistent
  flicker no amount of targeted fixing resolved. See `docs/decisions.md` for the full reasoning;
  nothing else in the app depended on it, so removal was a clean revert of that one feature.
