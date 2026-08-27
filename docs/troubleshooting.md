# Troubleshooting

## A schedule stopped changing the wallpaper

Open the schedule list and check the card itself first — after 5 consecutive failures (folder
unreachable, permission revoked, zero images found) a schedule auto-disables itself and its
switch will show off. Re-enable it once the underlying problem is fixed and it picks back up
immediately.

If the switch is still on but nothing is changing:

1. Open **Diagnostics** from the schedule list's overflow menu and check both the battery
   optimization and exact-alarm permission status. Either can be revoked later from system
   Settings (a system update, an OEM "clean up battery usage" prompt, manually poking around)
   without the app being notified — if the exemption was silently revoked, alarms are throttled
   back down to Doze's ~9-15 minute floor, so a short interval schedule will appear to have
   "stopped" when it's actually just firing far less often than configured.
2. For a **linked folder** source: SAF tree permissions can be revoked by the system (rare, but
   happens after a factory-reset-and-restore-from-backup cycle, or if the providing app is
   uninstalled/reinstalled). Re-link the folder from the schedule editor if so.
3. For a **gallery-imported set**: the copied files live in app-private storage
   (`filesDir/sets/<setId>/`) and can't lose permission the way a SAF folder can — if a set-backed
   schedule is failing, it's almost certainly "zero images" (every photo was removed from the set
   in the editor) rather than a permission issue.

## Lock screen isn't changing independently of the home screen

This is the platform behavior the app is built around, not a bug: **if a device has never had a
lock-screen wallpaper set separately from the home screen, the lock screen mirrors the home
screen wallpaper.** The first time a lock-targeting schedule applies, it pins a lock-only
wallpaper (`FLAG_LOCK`) specifically to break that mirroring — after that first apply, home and
lock are decoupled and a home-only schedule's changes will no longer bleed onto the lock screen.
If you're testing this on a device that's never run the app before, the very first tick of a
lock-targeting (or both-targeting) schedule is what establishes independence; don't judge
home/lock decoupling from before that first tick has happened.

Whether `FLAG_LOCK` is honored as an independent wallpaper at all (rather than the OEM's launcher
silently ignoring it) is worth re-verifying on unfamiliar hardware — it was confirmed on a
Nothing Phone 3(a) running Android 16 before this feature was built, but lock-screen wallpaper
handling has historically been one of the least consistent parts of the Android OEM landscape.

## A schedule enabled with both targets isn't showing on both screens

Check whether another enabled schedule already owns one of those targets — enabling a schedule
never simply fails when there's a conflict; per the [target arbitration rule](decisions.md), the
*other* schedule downgrades to whatever target it still owns exclusively (or switches off
entirely if nothing's left). If you expected a schedule to control both home and lock and it only
shows on one, look at whether a different schedule silently picked up the other target during a
previous enable.

## The app was denied the exact-alarm or battery-optimization permission and I want to fix it later

Both can be granted from system Settings directly (**Settings → Apps → Wallpaper Cycler →
Battery**, and, on Android 12+, **Settings → Apps → Special app access → Alarms & reminders**),
not just from the app's own onboarding screen — the onboarding screen only runs once, but
Diagnostics always shows current status and re-launches the same system permission prompts.

## Backup/restore left folder-based schedules empty

This is expected, not a bug — see the comment at the top of `BackupSerializer.kt`. A SAF folder
permission grant is tied to the specific install that received it and cannot be exported; a
restored linked-folder schedule keeps its old folder path as data but has no read access to it
until you re-link that folder from the schedule editor. Gallery-imported sets restore correctly
*onto the same install* (the copied files are still on disk, only the schedule metadata was
missing), but are empty after a restore onto a fresh install, since the backup never contained the
image bytes themselves — only which photos belonged to which set.

## Widget

There is no home-screen widget — one was built and then removed after a persistent rendering bug
(see [`decisions.md`](decisions.md) for why). If you're looking for prev/next controls outside
the app, the schedule list's per-card buttons are the only place for now.
