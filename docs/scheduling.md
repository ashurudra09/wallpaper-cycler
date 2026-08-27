# Scheduling: exact alarms and reliability

This is the constraint that shaped the whole background-execution design, so it gets its own
document.

## Why not WorkManager alone

`WorkManager`'s `PeriodicWorkRequest` is the "correct" modern API for recurring background work,
but it has a 15-minute minimum period and no guarantee of *when* inside its flex window a run
actually happens. A schedule whose entire point is "change the wallpaper at 9:00 AM" or "every 2
minutes" can't be built on an API that might run anywhere from on-time to 15+ minutes late. This
app uses `AlarmManager` for the actual wake-up (precise to the second, modulo Doze — see below)
and `WorkManager` only as the execution vehicle *after* the alarm has already fired
(`WallpaperAlarmReceiver` → `enqueueUniqueWork`), so the slow, IO-bound decode-and-apply work runs
off the broadcast receiver's short execution window without giving up timing precision.

## Doze and exact alarms

Android's Doze mode batches background work to save battery. Even `setExactAndAllowWhileIdle` —
the API this app uses, which is explicitly designed to survive Doze — is throttled: roughly once
every ~9 minutes in early Doze, stretching toward 15 minutes in deep idle, **per app**, unless
that app is exempted from battery optimization.

Two things unlock reliable sub-15-minute intervals:

1. **The battery-optimization exemption**
   (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, requested once at onboarding via
   `PermissionsScreen`). An app on the exemption allowlist is not subject to Doze's alarm
   throttling at all, and is *also* always permitted to call the exact-alarm scheduling APIs —
   the exemption alone solves both problems at once.
2. **`SCHEDULE_EXACT_ALARM`** — declared as a manifest permission and requestable from the same
   onboarding screen, purely as a fallback for someone who declines the battery exemption above.
   Without either, `AlarmManager.setExactAndAllowWhileIdle` throws `SecurityException` on Android
   12+; every call site that arms an alarm (`AlarmScheduler.scheduleNext`, called from
   `ApplyWallpaperWorker`, `ToggleScheduleUseCase.enable`, and `BootCompletedReceiver`) catches
   this specifically and lets the operation that triggered it (enabling a schedule, a boot, a
   tick) continue rather than crash — the schedule just silently won't fire again until the
   permission is granted.

The onboarding screen and the in-app diagnostics panel (`ui/diagnostics/DiagnosticsScreen.kt`)
both surface the live status of both permissions, since either can be revoked later from system
Settings without the app being notified.

## The "no catch-up" design

If the phone is off, in deep Doze longer than expected, or a linked folder is temporarily
unreachable when an alarm fires, the app does not try to reconstruct what *should* have happened
and apply it on recovery. `ApplyWallpaperWorker` always returns `Result.success()` and always
re-arms the next alarm (`AlarmScheduler.scheduleNext`) regardless of whether the tick itself
succeeded — there is deliberately nothing for WorkManager's retry semantics to add. A missed tick
is simply skipped; the schedule picks back up at its next naturally-computed trigger time. The
alternative (queue up missed changes and apply them all at once on recovery) would make "what's
on screen right now" depend on exactly when the phone happened to wake up, which is a worse user
experience than just moving on.

## Failure handling

`ApplyWallpaperUseCase.advance` counts consecutive failures per schedule (folder unreachable,
permission revoked, zero images found) in `CycleDao`'s `consecutiveFailures` column. After 5 in a
row, the schedule auto-disables itself (`scheduleDao().upsert(scheduleEntity.copy(enabled =
false))`) rather than continuing to silently retry an alarm that can never succeed — an alarm
still fires reliably in this scenario, it's the underlying source that's the problem, and
`autoDisabled` is surfaced back through `ApplyResult` so the UI can explain what happened.

## OEM-specific caveats

Whether `WallpaperManager.setBitmap(..., FLAG_LOCK)` is honored as a genuinely independent
lock-screen wallpaper (rather than silently mirroring the home screen) is OEM-dependent and was
verified directly on the target device (a Nothing Phone 3(a), Android 16) before any of the rest
of this was built — see [`troubleshooting.md`](troubleshooting.md) for what to check if you're
running this on different hardware.
