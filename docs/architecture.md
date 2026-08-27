# Architecture

Wallpaper Cycler is a single-module Android app (`app/`) built with Kotlin and Jetpack
Compose. There is no DI framework and no multi-module split — at this size, a manual
service locator and clean package boundaries buy the same testability and readability
without the build-time cost.

## Package map

```
com.ashurudra.wallpapercycler/
├── domain/             Pure Kotlin — no Android imports, runs on the JVM in unit tests
│   ├── model/          Schedule, Trigger, ScreenTarget, FitMode, SortOrder, ThemeMode, ImageSourceConfig
│   ├── shuffle/        ShuffleBag (shuffle-without-repeat) and SortedCycle (sorted-order cycling)
│   ├── schedule/       NextTickCalculator — pure time math for interval and time-of-day triggers
│   ├── target/         TargetArbiter — the single-owner-per-screen conflict rule
│   └── usecase/        ApplyWallpaperUseCase, ToggleScheduleUseCase, SaveScheduleUseCase, DeleteScheduleUseCase
├── data/
│   ├── db/              Room: AppDatabase, ScheduleDao, CycleDao, entities, type converters
│   ├── prefs/            SettingsRepository (DataStore: theme, custom accent, onboarding flag)
│   ├── source/           ImageSource abstraction, LinkedFolderScanner, ManagedSetScanner,
│   │                      UriPermissionManager, MediaImporter
│   └── backup/           BackupSerializer — JSON export/import of schedules and settings
├── wallpaper/           WallpaperImageDecoder, CropGeometry, WallpaperApplier
├── scheduler/           AlarmScheduler, WallpaperAlarmReceiver, ApplyWallpaperWorker, BootCompletedReceiver
├── ui/
│   ├── theme/            Color.kt, Theme.kt, Type.kt — fixed light/dark palettes + custom accent
│   ├── schedules/        SchedulesScreen, ScheduleCard, SchedulesViewModel (the app's home screen)
│   ├── editor/            ScheduleEditorScreen, EditorViewModel, SourceSection, TriggerSection, SetPhotoGrid
│   ├── settings/          SettingsScreen, SettingsViewModel
│   ├── onboarding/        PermissionsScreen
│   └── diagnostics/       DiagnosticsScreen — an in-app panel for the platform checks in docs/scheduling.md
├── di/                  AppContainer — the manual service locator
├── MainActivity.kt      Single-Activity host; Compose Navigation between the screens above
└── WallpaperCyclerApp.kt Application subclass, owns the AppContainer instance
```

## Data flow: one automatic wallpaper change

1. **`AlarmScheduler`** arms an exact `AlarmManager` alarm (`setExactAndAllowWhileIdle`) for one
   schedule, computed by `NextTickCalculator.nextTriggerAt`.
2. The alarm fires **`WallpaperAlarmReceiver`**, a `BroadcastReceiver` whose only job is to hand
   the schedule id to WorkManager (`enqueueUniqueWork`, `ExistingWorkPolicy.REPLACE`) and return —
   broadcast receivers get a few seconds of execution time, nowhere near enough for the decode
   below.
3. **`ApplyWallpaperWorker`** (a `CoroutineWorker`, so it survives process death mid-run) does the
   real work: calls `ApplyWallpaperUseCase.applyNext`, then re-arms the *next* alarm via
   `AlarmScheduler` — rescheduling only ever happens here, never from a manual action, so a manual
   Next/Previous tap never resets the automatic countdown.
4. **`ApplyWallpaperUseCase.advance`** lists the schedule's images (`ImageSource.listImages()`),
   advances the persisted `ShuffleBag`/`SortedCycle` state by one position, decodes and crops the
   chosen image (`WallpaperImageDecoder` + `CropGeometry`), and applies it
   (`WallpaperApplier` → `WallpaperManager.setBitmap`) with the schedule's `ScreenTarget` flags
   (`FLAG_SYSTEM`, `FLAG_LOCK`, or both).
5. The new cycle position is persisted to Room (`CycleDao`). `SchedulesViewModel.uiState` observes
   both the `schedules` and `cycle_state` tables directly, so the schedule list updates itself —
   no manual refresh call is threaded through the apply path.

Manual Next/Previous (from the schedule list or a schedule card) calls the exact same
`ApplyWallpaperUseCase.applyNext`/`applyPrevious` entry points directly, skipping only the alarm
reschedule in step 3.

## Threading model

- **UI**: Compose + `ViewModel`s, all Room/DataStore reads exposed as `Flow`/`StateFlow`
  (`SharingStarted.WhileSubscribed`), so screens recompose from a single source of truth instead
  of manual refresh calls.
- **Background**: every apply, scan, and decode runs on `Dispatchers.IO` inside a
  `CoroutineWorker` or a `viewModelScope.launch`. There is no long-running service and no polling
  — the entire cost of a scheduled change is one alarm wake-up plus a few hundred milliseconds of
  work, then the process is free to be killed again.
- **Broadcast receivers** (`WallpaperAlarmReceiver`, `BootCompletedReceiver`) do the minimum
  possible synchronously (enqueue work, or `goAsync()` for boot) and never do IO themselves.

## Why no widget

An earlier revision of this app included a home-screen widget (`RemoteViews`-based, per the
original tech-stack choice over Glance). It was removed after its RemoteViews rebuilds turned out
to double up on every real state change and produce a visibly inconsistent "next" preview — see
the git history around the widget's removal for the full diagnosis. Nothing in the architecture
above depends on it; `ApplyWallpaperUseCase`, `ToggleScheduleUseCase`, and `DeleteScheduleUseCase`
are unchanged in shape from before the widget existed.
