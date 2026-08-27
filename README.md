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
- **Two trigger types.** Every N minutes/hours, or specific times of day with day-of-week
  repeats.
- **Two image sources.** Link a folder directly, or import a hand-picked set from your
  photo gallery (copied in, so it's a stable snapshot).
- **Manual control.** Next/previous from the app or a home screen widget.
- **Alarm-app-style UI.** A list of schedules, each with a switch — modeled on the stock
  Clock app's alarms tab.
- **Light/dark/system theming**, plus an optional custom accent color.
- Runs with effectively **zero idle CPU cost** — no background service, just one alarm
  wake-up per scheduled change.

## Status

Early development. See [`docs/plan.md`](docs/plan.md) for the full build plan, phase
breakdown, and the design decisions behind it.

## Requirements

- Android Studio (current stable) with the Android SDK
- JDK 17+
- A device or emulator running Android 10 (API 29) or later

## Building

Open the project root in Android Studio and let it sync — the Gradle wrapper and version
catalog (`gradle/libs.versions.toml`) handle the rest. To build from a terminal instead:

```bash
./gradlew assembleDebug
```

## Project structure

```
app/src/main/kotlin/com/ashurudra/wallpapercycler/
├── domain/     # Pure Kotlin: shuffle logic, schedule timing, target conflict rules
├── data/       # Room, DataStore, image sources, backup/restore
├── wallpaper/  # Decoding, cropping, and applying wallpapers
├── scheduler/  # AlarmManager + WorkManager glue
├── ui/         # Compose screens
└── widget/     # Home screen widget
```

See [`docs/plan.md`](docs/plan.md) for the full layout and the rationale behind it.

## License

MIT — see [`LICENSE`](LICENSE).
