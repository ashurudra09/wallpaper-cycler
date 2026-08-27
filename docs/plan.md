# Wallpaper Cycler for Android — Build Plan

## Context

Desktop OSes have shipped folder-based wallpaper slideshows for a decade; Android never has. You curate large wallpaper collections and want them to rotate automatically, independently on the home screen and the lock screen, on schedules you control.

This plan builds a native Android app that points at a folder of images and cycles the wallpaper on a schedule — with shuffle that exhausts the folder before repeating, interval or time-of-day triggers, manual next/previous, and independent home/lock schedules. The app is a personal sideloaded project for a Nothing Phone 3(a) on Android 16, but the repository is meant to read as a well-structured open-source project, not a one-off script.

**Design north star:** the AOSP Clock app's alarms tab. A list of cards, each a schedule, each with a switch. Quiet typography, generous spacing, one primary FAB.

---

## Platform constraints that shape the design

These were verified before the architecture was chosen; each one forced a decision.

| Constraint | Consequence |
|---|---|
| `setExactAndAllowWhileIdle` fires **at most once per ~9 min per app** in Doze, stretching toward 15 min in deep idle | Sub-15-minute intervals are unreliable unless the app is on the power allowlist. We request the battery-optimization exemption at onboarding. |
| Apps on the power allowlist are **always** permitted to call exact-alarm APIs | The battery exemption also solves exact-alarm permission. `SCHEDULE_EXACT_ALARM` is declared only as a fallback path for a user who declines. |
| Whether a device honors a **lock-only** wallpaper (`FLAG_LOCK`) is OEM-dependent | Phase 0 is a spike on your actual phone before anything else is built. |
| If no lock wallpaper was ever set separately, **the lock screen mirrors the home wallpaper** | A home-only schedule would appear to change the lock screen too. The app pins the current lock image once (`FLAG_LOCK`) to decouple them. |
| `DocumentFile.listFiles()` does one IPC round-trip per file | We query `DocumentsContract` directly with an explicit projection — one cursor for the whole folder. |
| Wallpapers are set as full-screen bitmaps; there is no crossfade API | Changes are instantaneous swaps. No transition animations are possible for static wallpapers. |

---

## Decisions taken (from your answers)

**Scheduling**
- Exact alarms + a one-time battery-optimization exemption. Intervals may go as low as 1 minute.
- Two trigger types: **every N minutes/hours**, or **at specific times of day** with day-of-week repeat toggles. No unlock/charging triggers.
- Manual Next/Previous does **not** reset the countdown; automatic ticks keep their original cadence.
- **No catch-up.** If a change is missed (phone off, Doze, folder unreachable), the app simply ensures the next alarm is scheduled. Nothing is applied on recovery.
- Changes apply **immediately**, even if you are looking at the screen.

**Targets and conflicts**
- A schedule carries two independent target toggles: `home` and `lock`. "Both" is not a separate type — it is both toggles on.
- Invariant: **at most one enabled schedule owns `home`, and at most one owns `lock`.**
- Enabling schedule *S* resolves conflicts per previously-enabled schedule *S′*:
  - `remaining = S′.targets − S.targets`
  - if `remaining` is non-empty → `S′.targets = remaining`, *S′* stays enabled (e.g. a home+lock schedule visibly downgrades to lock-only)
  - if `remaining` is empty → *S′* switches **off**, its target toggles left untouched
- One schedule = one cycle. A schedule targeting both screens shows the **same image on both**. Different images on home and lock means two schedules.

**Images**
- A schedule's source is one of two kinds:
  - **Linked folder** — one folder, **top level only**, no subfolder recursion. Access via SAF tree URI with persisted permission. Images stay where they are; edits to the folder show up in the cycle.
  - **Gallery selection** — pick multiple photos through Android's photo picker; the app **copies** them into a private managed folder, and that folder becomes the schedule's source. The set is editable afterwards (add more, remove individual photos).
- Shuffle uses a bag that plays every image once before reshuffling.
- With shuffle off: sort by **file name or date modified**, ascending or descending.
- **Previous** steps back within the current shuffle cycle only; at the first image of a cycle the button is disabled.
- Fit mode is **per schedule**: Fill (center-crop, default), Fit with blurred bars, Fit with solid-color bars.

**UI and surfaces**
- Schedule list + editor, per-card Next/Previous, per-card thumbnails of the **current and next** image.
- One **home screen widget** with a configuration screen at placement time (scope: Home / Lock / Both).
- No Quick Settings tile, no ongoing notification, no history screen, no per-image exclude list.
- Theme: **fixed light palette + fixed dark palette**, mode selector Light / Dark / Follow system. Plus a **custom accent** option (baseline implementation: preset swatches + hex entry) that overrides the accent in both palettes. No Material You dynamic color.
- **Backup / restore** to JSON, covering schedules, settings, and the custom accent. Folder permissions cannot be serialized and must be re-granted after restore.

**Project**
- Personal sideload. Local keystore; optionally attach a signed APK to GitHub releases.
- Moderate repo rigor: single Gradle module with clean package boundaries, unit tests on shuffle and schedule math, README plus a `docs/` folder. No CI, no static-analysis gates.

---

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Kotlin 2.x** | The only first-class language for modern Android. Coming from Python/Java it reads easily; null-safety and coroutines remove most classic Android bugs. |
| UI | **Jetpack Compose + Material 3** | Declarative, closest thing to writing UI as data. The stock Clock look is essentially default M3. |
| Persistence | **Room** (schedules, cycle state, file index) + **DataStore** (app settings) | Room gives typed, migratable SQL with compile-time query checking. DataStore for a handful of scalar prefs. |
| Background | **AlarmManager** (exact) → **BroadcastReceiver** → **WorkManager** one-shot | Alarms give precise wake-ups; WorkManager guarantees the job survives process death mid-apply. |
| Images | **BitmapFactory** with two-pass downsampling for wallpapers, **Coil 3** for UI thumbnails | Manual decode gives exact control over memory for the large bitmap; Coil handles list thumbnails with its own cache. |
| Photo picking | **`PickMultipleVisualMedia`** (androidx.activity) | System photo picker on Android 13+, backported below it. Needs no storage permission on any version and exposes only images. |
| Widget | **RemoteViews `AppWidgetProvider`** | Lighter and easier to debug than Glance for two buttons and an image. |
| DI | Manual `AppContainer` service locator | Hilt's build-time cost and indirection are not worth it in a single-module app. |
| Serialization | kotlinx-serialization | Backup/restore JSON. |
| SDK | `minSdk 29` (Android 10), `compileSdk`/`targetSdk 36` (Android 16) | API 29 covers ~97% of active devices and drops all legacy-storage branches. |

**Why not cross-platform:** every core capability here (WallpaperManager, AlarmManager, SAF, app widgets) is Android-specific. Flutter or React Native would mean writing the entire feature as a native plugin anyway, plus a runtime that costs memory and startup time.

---

## Repository layout

```
mobile-wallpaper-cycler/
├── README.md                  # what it is, screenshots, install, quick start
├── CHANGELOG.md
├── LICENSE                    # MIT
├── .gitignore                 # Android/Gradle/IDE
├── .editorconfig
├── docs/
│   ├── architecture.md        # module map, data flow, threading model
│   ├── decisions.md           # the "Decisions taken" table above, with rationale
│   ├── scheduling.md          # Doze, exact alarms, the reliability story
│   ├── development.md         # Windows setup, build, deploy to device, logcat
│   └── troubleshooting.md     # OEM quirks, lock-screen mirroring, permission recovery
├── gradle/libs.versions.toml  # version catalog
├── settings.gradle.kts
├── build.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/ashurudra/wallpapercycler/
        │   ├── WallpaperCyclerApp.kt
        │   ├── di/AppContainer.kt
        │   ├── domain/
        │   │   ├── model/          Schedule, ScreenTarget, Trigger, FitMode, SortOrder, CycleState
        │   │   ├── shuffle/        ShuffleBag.kt            ← pure, seeded, fully tested
        │   │   ├── schedule/       NextTickCalculator.kt    ← pure time math, fully tested
        │   │   ├── target/         TargetArbiter.kt         ← pure conflict rules, fully tested
        │   │   └── usecase/        ApplyNext, ApplyPrevious, ToggleSchedule, SaveSchedule
        │   ├── data/
        │   │   ├── db/             AppDatabase, ScheduleDao, CycleDao, entities, converters
        │   │   ├── prefs/          SettingsRepository
        │   │   ├── source/         ImageSource.kt, LinkedFolderScanner.kt, ManagedSetScanner.kt,
        │   │   │                   UriPermissionManager.kt, MediaImporter.kt
        │   │   └── backup/         BackupSerializer.kt
        │   ├── wallpaper/          WallpaperApplier.kt, WallpaperImageDecoder.kt, CropGeometry.kt
        │   ├── scheduler/          AlarmScheduler, WallpaperAlarmReceiver,
        │   │                       BootCompletedReceiver, ApplyWallpaperWorker
        │   ├── ui/
        │   │   ├── theme/          Color.kt, Theme.kt, Type.kt
        │   │   ├── schedules/      SchedulesScreen, ScheduleCard, SchedulesViewModel
        │   │   ├── editor/         ScheduleEditorScreen, EditorViewModel, pickers
        │   │   ├── settings/       SettingsScreen, AccentPicker, BackupSection
        │   │   ├── onboarding/     PermissionsScreen
        │   │   └── common/         shared composables
        │   └── widget/             CyclerWidgetProvider, WidgetConfigActivity
        ├── main/AndroidManifest.xml
        ├── main/res/               layouts (widget only), drawables, values
        └── test/kotlin/            JVM unit tests
```

---

## Core designs

### Image sources (`data/source/`)

Both source kinds sit behind one `ImageSource` interface returning `List<ImageRef>` (`id`, `displayName`, `uri`, `lastModified`, `sizeBytes`), so shuffle, sorting, and the wallpaper engine never learn which kind they are working with.

- **`LinkedFolderScanner`** — SAF tree URI, one `DocumentsContract` cursor per scan.
- **`ManagedSetScanner`** — plain `File.listFiles()` over the set directory. Substantially cheaper than SAF: no IPC, no permission re-checks, no risk of a revoked grant.

**Gallery import flow (`MediaImporter`)**

1. The editor launches `ActivityResultContracts.PickMultipleVisualMedia` — the system photo picker on Android 13+, the backported picker below it. It needs **no storage permission on any version**, and shows only images.
2. The picker returns URIs carrying a read grant **scoped to this process and lost on restart**, so the copy runs immediately, suspending on `Dispatchers.IO`, with progress held in the editor ViewModel so it survives rotation.
3. Each photo is copied **byte-for-byte, never re-encoded**, into `filesDir/sets/<setId>/` — EXIF and original quality preserved. The display name comes from an `OpenableColumns.DISPLAY_NAME` query; collisions get a `-1`, `-2` suffix. A SHA-256 of the first 64 KB plus the byte size is stored per image so re-picking a photo already in the set is skipped rather than duplicated.
4. Import is transactional per photo: a failed copy deletes its partial file and is reported in a summary, instead of aborting the batch.
5. `MediaStore.getPickImagesMaxLimit()` caps a single pick on some devices (commonly 100). The editor therefore supports repeated **Add photos** rounds into the same set and shows a running count.

**Set management** lives in the editor: a thumbnail grid, multi-select removal, total size on disk, and Add photos. Deleting a schedule that owns a set prompts before deleting the copied files.

**Consequences to document in `docs/`:**
- Copies live in app-private storage, so **uninstalling the app deletes them**. The originals in your gallery are never touched or moved.
- A gallery set is a snapshot — deleting a photo from your gallery afterwards does not affect the schedule. That is the point of copying.
- Backup JSON records set membership and names, not image bytes. Restoring onto a fresh install leaves gallery-backed schedules empty until re-imported.

### Shuffle bag (`domain/shuffle/ShuffleBag.kt`)

Pure Kotlin, no Android imports, seeded RNG so tests are deterministic.

- State: `sequence: List<ImageId>`, `index: Int`, `seed: Long`.
- `next()` advances the index; on overflow it reshuffles with a new seed and resets to 0. **The last image of the old cycle is never the first of the new one** (swap position 0 with a random other position if they collide) — otherwise you see the same wallpaper twice in a row and shuffle feels broken.
- `previous()` decrements; returns `null` at index 0 (button disabled in UI).
- `reconcile(currentFiles)` — run at every tick against the freshly scanned folder:
  - IDs that disappeared are removed from the unplayed remainder; if the *current* image vanished, advance.
  - New IDs are inserted at random positions **within the unplayed remainder**, so a newly added image can show up during the current cycle but never re-shows an already-played one.
- Persisted per schedule as a Room row (sequence stored as a delimited string of document IDs).

### Schedule tick math (`domain/schedule/NextTickCalculator.kt`)

Pure function `nextTriggerAt(trigger, from: Instant, zone: ZoneId): Instant`.

- **Interval:** anchored to the moment the schedule was enabled, not the wall clock, so "every 6 hours" from 09:15 fires at 15:15 and 21:15.
- **Times of day:** next matching (time, day-of-week) pair strictly after `from`. Must handle DST gaps (a 02:30 alarm on a spring-forward night) and month/year rollover.
- Tests cover: interval rollover, midnight crossing, all-days-off (schedule cannot be enabled), DST spring-forward and fall-back, and a schedule enabled exactly at its trigger time.

### Target arbitration (`domain/target/TargetArbiter.kt`)

Pure function taking the schedule being enabled plus all current schedules, returning the mutations to apply. Implements the conflict rule above. Tests assert the invariant holds after every combination of enable/disable/edit.

### Wallpaper application (`wallpaper/`)

`ApplyWallpaperWorker` runs the whole tick:

1. Load schedule + cycle state from Room.
2. Scan the source — one `DocumentsContract` cursor (projection: `DOCUMENT_ID`, `DISPLAY_NAME`, `MIME_TYPE`, `LAST_MODIFIED`, `SIZE`) for a linked folder, or a plain directory listing for a gallery set — filtering to `image/*` minus animated types.
3. `bag.reconcile(files)` then `bag.next()` (or the explicitly requested target image for manual next/prev).
4. Decode: `inJustDecodeBounds` → compute `inSampleSize` as the largest power of two that keeps the image ≥ the target size → decode → apply EXIF orientation → scale/crop per `FitMode` into a bitmap the size of `WallpaperManager.getDesiredMinimumWidth/Height` (falling back to display metrics).
5. `setBitmap(bitmap, null, true, flags)` where `flags` is the OR of `FLAG_SYSTEM` / `FLAG_LOCK` for that schedule's targets. Recycle immediately.
6. Persist the new index, write the "current/next" thumbnail cache for the card preview, schedule the next alarm, update widgets.

**Idle CPU cost is zero** — there is no service, no polling, no background thread while idle. The entire cost is one alarm wake-up plus roughly 200-600 ms of work per tick.

**Failure handling:** folder unreachable, permission revoked, or zero images → the schedule card enters a visible error state and a low-priority notification is posted. The alarm is still rescheduled (an SD card or cloud provider may come back). After 5 consecutive failures the schedule auto-disables.

### Permissions (manifest)

| Permission | Purpose |
|---|---|
| `SET_WALLPAPER` | Normal permission, granted at install. |
| `RECEIVE_BOOT_COMPLETED` | Re-arm alarms after reboot. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | The onboarding ask; unlocks reliable sub-15-minute intervals. |
| `SCHEDULE_EXACT_ALARM` | Fallback path if the exemption is declined. |
| `POST_NOTIFICATIONS` (33+) | Failure notifications only. Fully optional; app works if denied. |

No `READ_MEDIA_IMAGES`, no `MANAGE_EXTERNAL_STORAGE` — SAF tree URIs cover everything.

---

## Build phases

**Phase 0 — Device spike (do this first, half a day).** A throwaway single-Activity app that de-risks everything downstream. Verify on the Nothing Phone 3(a) running Android 16:
1. `setBitmap(..., FLAG_LOCK)` sets a lock-only wallpaper that Nothing OS actually honors.
2. After pinning a lock wallpaper, `FLAG_SYSTEM` changes no longer bleed to the lock screen.
3. An exact alarm with the battery exemption fires on time after 8 hours of overnight idle.
4. `DocumentsContract` listing of your real wallpaper folder, timed.
5. Nothing OS does not apply its own filter/processing to the applied bitmap.

*If (1) fails, the lock-screen half of the product needs rethinking — better to know on day one.*

**Phase 1 — Skeleton.** Repo, `.gitignore`, license, Gradle with version catalog, package structure, theme files, an empty schedule list with a FAB. Installs and launches on device.

**Phase 2 — Domain core + tests.** Models, `ShuffleBag`, `NextTickCalculator`, `TargetArbiter`, and their unit tests. No Android dependencies; runs in seconds on the JVM. This is where the actual product logic gets proven correct.

**Phase 3 — Data layer.** Room schema, DAOs, `SettingsRepository`, both `ImageSource` implementations, persisted URI permission handling with recovery when a grant is revoked, and `MediaImporter` with its copy/dedupe/progress logic and managed-set directory lifecycle.

**Phase 4 — Wallpaper engine.** Decoder, crop geometry (pure, tested), `WallpaperApplier`. Wire a debug button that applies a random image on demand.

**Phase 5 — Scheduler.** `AlarmScheduler`, alarm receiver, `ApplyWallpaperWorker`, boot receiver, onboarding permission screen. First end-to-end automatic cycling.

**Phase 6 — Main UI.** Schedule list with alarm-style cards (label, source, target chips, next-change countdown, switch), the editor (source step offering **Link a folder** or **Select from gallery**, trigger, shuffle/sort, fit mode, targets), the gallery-set management grid with import progress, per-card next/previous, current/next thumbnails.

**Phase 7 — Settings.** Light/Dark/System selector, custom accent, backup/restore JSON with the documented folder-permission caveat.

**Phase 8 — Widget.** `AppWidgetProvider` + configuration activity for scope selection, prev/next buttons, current thumbnail.

**Phase 9 — Docs and release.** Fill `docs/`, screenshots in the README, CHANGELOG, signed release APK.

---

## Verification

**Unit tests** (`./gradlew test`, runs on the JVM in seconds):
- `ShuffleBag`: full-cycle exhaustion over 1000 iterations, no repeat within a cycle, no back-to-back repeat across the boundary, `reconcile` with additions/removals/current-image-deleted.
- `NextTickCalculator`: intervals, time-of-day with day-of-week masks, DST transitions, midnight and year rollover.
- `TargetArbiter`: the invariant holds across every enable/disable/edit permutation, including a schedule reduced to zero targets.
- `CropGeometry`: fill/fit rects for portrait, landscape, square, and extreme-aspect images.
- `BackupSerializer`: round-trip equality, forward-compatible unknown-field handling.
- `MediaImporter`: display-name collision suffixing and dedupe-key computation, both extracted as pure functions so they test without a device.

**Manual device verification** (documented as a checklist in `docs/development.md`):
- Two schedules with different folders on home and lock simultaneously; confirm they advance independently and never cross-apply.
- Enable a home+lock schedule, then enable a home-only one; confirm the first visibly downgrades to lock-only and keeps running.
- Enable a home-only schedule that steals the only target of another; confirm the other switches off with its toggles intact.
- Set a 2-minute interval, lock the phone for 30 minutes, confirm every tick landed (`adb logcat` timestamps).
- Reboot; confirm schedules resume without opening the app.
- Delete the current image from the folder externally, then tick; confirm graceful skip.
- Revoke the folder permission in system settings; confirm the error state and notification, then re-grant and confirm recovery.
- Import ~150 photos from the gallery across two picker rounds; confirm the running count, that re-picking already-imported photos adds nothing, and that the set survives a reboot.
- Delete one of the original photos from your gallery afterwards; confirm the schedule's copy still cycles normally.
- Remove photos from a set in the editor; confirm the files are deleted from disk and the shuffle bag reconciles without repeating.
- Widget prev/next for each configured scope.
- Light/dark/system switching, custom accent, backup → wipe app data → restore.

**Battery check:** after 24 hours with a 30-minute schedule, Settings → Battery usage should show the app at a negligible share. Any regular multi-minute wake-lock indicates a bug in the alarm/worker path.

---

## Open questions — defaults I've chosen

These are minor enough that I've picked a default and will proceed unless you say otherwise. Flag any you disagree with.

1. **App/package name** — app "Wallpaper Cycler"; package `com.ashurudra.wallpapercycler`.
2. **Previous at cycle start** — the button is simply disabled at index 0 rather than reaching back into the previous cycle's order.
3. **Enabling a schedule applies immediately** — you get the first wallpaper the moment you flip the switch, then the interval starts.
4. **Disabling a schedule leaves the current wallpaper in place** — no restore of whatever was there before.
5. **Editing an enabled schedule restarts its cycle** only if the folder or sort/shuffle mode changed; changing just the interval keeps the current bag and index.
6. **No parallax** — wallpapers are set at exactly screen size, not double-width. Modern launchers rarely scroll them, and double-width doubles memory per apply.
7. **Formats** — JPEG, PNG, WebP, HEIC/HEIF, BMP. Animated GIF/WebP are filtered out (first frame only would be misleading).
8. **Image identity** — SAF document ID for linked folders, file name within the set directory for gallery sets. Renaming a file mid-cycle drops it from the current bag and reinserts it as new.
9. **Interval bounds** — 1 minute to 7 days, with an in-app note that intervals under 15 minutes depend on the battery exemption holding.
10. **Times-of-day count** — up to 8 times per schedule.
11. **Schedule count** — unlimited saved schedules, at most one enabled per target (as specified).
12. **Language/locale** — English only, but all strings live in `strings.xml` so translation stays possible.
13. **Analytics/telemetry** — none, ever. No network permission is declared at all.
14. **One source kind per schedule** — a schedule is either linked-folder or gallery-set, never a mix. Switching an existing schedule from a gallery set to a linked folder prompts before deleting the copied files.
15. **Sets are per-schedule, not a shared library** — two schedules wanting the same photos import them twice. A shared-set library is a clean later addition if you ever want it.
16. **Set storage** — `filesDir/sets/<setId>/`, private to the app and excluded from Android's cloud auto-backup so a large collection never inflates your backup quota.
17. **Import size guard** — informational warning if one import exceeds ~500 MB or all sets together exceed ~2 GB, with current usage shown in Settings. Nothing is ever blocked.
