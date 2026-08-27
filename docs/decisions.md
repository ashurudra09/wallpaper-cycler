# Design decisions

This is the reasoning behind the choices baked into the app. The original brainstorm lives in
[`plan.md`](plan.md); this document is the settled, as-built version — a few things changed
during implementation, and those are called out explicitly below.

## Scheduling

- **Exact alarms, not a periodic Worker.** `AlarmManager.setExactAndAllowWhileIdle` plus the
  battery-optimization exemption (requested once at onboarding) is the only way to get reliable
  sub-15-minute cycling. `WorkManager`'s own periodic jobs have a 15-minute floor and don't
  guarantee *when* inside their window they run — wrong tool for "change the wallpaper at exactly
  this time."
- **Two trigger types**: interval (every N minutes/hours/days) and specific times of day with a
  day-of-week mask. No unlock/charging triggers — those answer "when is the phone being used,"
  not "when should the wallpaper change," and would make the schedule unpredictable.
- **Manual Next/Previous never resets the automatic countdown.** `AlarmScheduler.scheduleNext` is
  called from exactly one place, `ApplyWallpaperWorker` — never from the use cases that back a
  manual tap. Otherwise, tapping Next moments before an automatic tick was due would silently
  push that tick back out, which is surprising and hard to reason about from the UI alone.
- **No catch-up.** If a tick is missed (phone off, Doze, an unreachable folder), the app does not
  try to apply whatever was missed on recovery — it only ensures the *next* alarm is armed. A
  catch-up model means "what wallpaper is showing right now" depends on exactly when the phone
  was reachable, which defeats the point of a predictable cycle.

## Targets and conflicts

- **A schedule carries two independent target toggles** (home, lock) rather than a three-way
  enum. "Both" falls out naturally as both toggles on, and the arbitration rule below doesn't need
  a special case for it.
- **Invariant: at most one enabled schedule owns each target.** Enabling a schedule that
  overlaps another's targets doesn't outright disable the other — `TargetArbiter.resolveEnable`
  computes `remaining = other.targets - enabling.targets`; if that's non-empty, the other
  schedule just downgrades to whatever target it still owns exclusively (a home+lock schedule
  visibly becomes lock-only), and only switches off entirely if nothing is left. This keeps a
  schedule "wanting" its original targets in the data model, in case its rival is later disabled.
- **One schedule = one cycle**, shown identically on every target it owns. Wanting different
  images on home and lock is two schedules, not a per-target image list on one schedule — this
  keeps `ShuffleBag`/`SortedCycle` state, and the conflict model above, unambiguous.

## Images

- **Two source kinds behind one `ImageSource` interface**: a linked SAF folder (top-level only,
  no recursion — recursion plus SAF's per-file IPC cost gets expensive fast) or a gallery-picked
  set that gets copied byte-for-byte into app-private storage. Shuffle, sorting, and the
  wallpaper engine never need to know which kind they're looking at.
- **Gallery photos are copied, not referenced.** A picked `MediaStore` URI's read grant is scoped
  to the process that received it from the picker and doesn't survive a restart, so referencing
  it directly would make the schedule silently stop working after the app was killed. Copying
  also means a gallery-backed schedule is a stable snapshot — deleting the original photo later
  doesn't touch the schedule.
- **Shuffle plays every image once before reshuffling** (`ShuffleBag`), rather than picking
  independently at random each tick, which is what most people actually mean by "shuffle" and
  what avoids the classic complaint of true randomness ("it keeps repeating the same few
  photos").

## UI

- **Modeled on the stock Clock app's alarms tab**: a list of cards, each a schedule, each with a
  switch. It's a UI pattern most Android users already have muscle memory for, and it maps
  directly onto "schedule = alarm-like thing with an on/off switch."
- **Fixed light/dark palettes plus an optional custom accent**, not Material You dynamic color.
  Dynamic color would make screenshots and the design intent unpredictable across devices; a
  personal project doesn't need to chase system theming trends.
- **No widget.** Phase 8 originally added a `RemoteViews`-based home-screen widget (prev/next
  buttons plus an image preview). It was removed after repeated attempts to fix a rendering
  flicker — the widget was rebuilding twice for every real state change, and one of the two
  rebuilds could show a different simulated "next" image than the other. Rather than keep
  layering fixes onto a feature that kept misbehaving, it was cut. Everything it depended on
  (`ApplyWallpaperUseCase`, `ToggleScheduleUseCase`, `DeleteScheduleUseCase`) is unchanged in
  shape from before it existed, so it could be re-added later without touching the rest of the
  app.

## Project

- **Personal sideload, moderate repo rigor.** Single Gradle module, clean package boundaries,
  unit tests on the pure-Kotlin domain logic (shuffle, schedule math, target arbitration, crop
  geometry, backup round-tripping), a README and this `docs/` folder — but no CI and no
  static-analysis gates. The audience is one person and one phone; process overhead beyond that
  wouldn't buy anything.
- **A local release keystore**, not Play Store distribution. See
  [`development.md`](development.md) for how signing is wired up.
