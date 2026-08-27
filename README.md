# Wallpaper Cycler

A native Android app that cycles your home screen and lock screen wallpapers on a
schedule — the folder-slideshow feature every desktop OS has had for years, and Android
never did.

Point a schedule at a folder (or a hand-picked set of photos from your gallery), set an
interval or specific times of day, and the app rotates through them in shuffled or sorted
order — independently for the home screen and the lock screen, with manual next/previous
whenever you want to skip ahead.

## Features

- **Independent home/lock scheduling.** Separate folders, intervals, and shuffle state for
  each screen, running at once.
- **True shuffle.** Plays every image in a folder once before reshuffling — never a
  premature repeat.
- **Two trigger types.** Every N minutes/hours/days, or specific times of day with
  day-of-week repeats.
- **Two image sources.** Link a folder directly, or import a hand-picked set from your
  photo gallery (copied in, so it's a stable snapshot that survives the original photo
  being deleted or moved).
- **Manual control.** Next/previous per schedule, right from the list.
- **Alarm-app-style UI.** A list of schedules, each with a switch — modeled on the stock
  Clock app's alarms tab.
- **Light/dark/system theming**, plus an optional custom accent color.
- **Backup/restore to JSON**, covering schedules and settings.
- Runs with effectively **zero idle CPU cost** — no background service, just one alarm
  wake-up per scheduled change.

## Status

Feature-complete for personal use across phases 1–9 of the original build plan (see
[`docs/plan.md`](docs/plan.md) for that history). A home-screen widget was attempted and
then removed after a persistent rendering bug — see [`docs/decisions.md`](docs/decisions.md)
for why.

## Requirements

- Android Studio (current stable) with the Android SDK, or just a JDK 17+ and the Gradle
  wrapper
- A device or emulator running Android 10 (API 29) or later

## Installing

This is a personal sideload project, not published anywhere. To install it on your own
device:

```bash
./gradlew installDebug
```

with the device connected over USB and USB debugging enabled — or build an APK and
install it manually with `adb install`. See [`docs/development.md`](docs/development.md)
for building a signed release APK instead of a debug build.

The first launch walks through a short onboarding screen that requests the battery
optimization exemption (and, on Android 12+, the exact-alarm permission as a fallback) —
both make scheduled changes fire reliably at short intervals. See
[`docs/scheduling.md`](docs/scheduling.md) for why these matter and what happens if you
decline them.

## Project structure

```
app/src/main/kotlin/com/ashurudra/wallpapercycler/
├── domain/     # Pure Kotlin: shuffle logic, schedule timing, target conflict rules
├── data/       # Room, DataStore, image sources, backup/restore
├── wallpaper/  # Decoding, cropping, and applying wallpapers
├── scheduler/  # AlarmManager + WorkManager glue
└── ui/         # Compose screens
```

See [`docs/architecture.md`](docs/architecture.md) for the full module map and data flow.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — module map, data flow, threading model
- [`docs/decisions.md`](docs/decisions.md) — the design decisions behind the app, with rationale
- [`docs/scheduling.md`](docs/scheduling.md) — exact alarms, Doze, and the reliability story
- [`docs/development.md`](docs/development.md) — building, deploying, logcat, release signing
- [`docs/troubleshooting.md`](docs/troubleshooting.md) — OEM quirks and permission recovery
- [`docs/plan.md`](docs/plan.md) — the original end-to-end build plan this app was built from
- [`CHANGELOG.md`](CHANGELOG.md) — what shipped in each phase

## License

MIT — see [`LICENSE`](LICENSE).
