# Development

## Requirements

- Android Studio (current stable) with the Android SDK, **or** just a JDK 17+ and the Gradle
  wrapper if you'd rather work from a terminal — this project doesn't need Studio's IDE features
  to build or test.
- A device or emulator running Android 10 (API 29) or later. Development happened primarily
  against a physical Nothing Phone 3(a) on Android 16 — see
  [`troubleshooting.md`](troubleshooting.md) for what's worth re-checking on different hardware.

## Building (Windows / PowerShell)

```powershell
.\gradlew.bat assembleDebug
```

The debug build type has an `applicationIdSuffix` of `.debug`, so it installs alongside — never
over — any release build on the same device. It's signed with Android's shared, public debug key,
which is fine for sideloading to your own phone but not meant for distribution.

Run the unit test suite (pure-Kotlin domain logic — shuffle, schedule math, target arbitration,
crop geometry, backup round-tripping — runs on the JVM in seconds, no device needed):

```powershell
.\gradlew.bat test
```

## Deploying to a device

With the device connected over USB and USB debugging enabled:

```powershell
.\gradlew.bat installDebug
```

or build the APK and install it manually:

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Reading logs

Filter logcat to just this app's process to cut through system noise:

```powershell
adb logcat --pid=$(adb shell pidof -s com.ashurudra.wallpapercycler.debug)
```

(drop the `.debug` suffix if you're running a release build). Worth knowing for this app
specifically:

- `WallpaperAlarmReceiver` / `ApplyWallpaperWorker` log nothing by default — if you need to trace
  a tick, the fastest path is the in-app **Diagnostics** screen (from the schedules list's
  overflow menu), which surfaces live permission status and lets you fire a one-off wallpaper
  apply and folder scan to see timing directly, without needing logcat at all.
- `SecurityException` around alarm scheduling is caught deliberately in several places (see
  [`scheduling.md`](scheduling.md)) — it won't show up as a crash, only as a schedule that stops
  ticking. Check the exact-alarm permission status in Diagnostics first if a schedule seems stuck.

## Release signing

`assembleRelease` (`isMinifyEnabled = true`, R8/ProGuard applied) only produces a signed,
installable APK if a local `keystore.properties` exists at the repo root — cloning this repo
without one still builds an *unsigned* release artifact, since nobody else should have this
project's signing key.

To set up signing on a new machine, generate a keystore once:

```powershell
keytool -genkeypair -v `
  -keystore release-keystore.jks `
  -alias release `
  -keyalg RSA -keysize 2048 -validity 10000
```

and create `keystore.properties` next to it, at the repo root (both are already covered by
`.gitignore` — `*.jks` and the literal `keystore.properties`, so neither can be accidentally
committed):

```properties
storeFile=release-keystore.jks
storePassword=<the password you set above>
keyAlias=release
keyPassword=<the password you set above>
```

Then:

```powershell
.\gradlew.bat assembleRelease
```

produces a signed APK at `app\build\outputs\apk\release\app-release.apk`.

**Back up `release-keystore.jks` and its password somewhere outside this repo.** Android refuses
to install an update over an existing install unless it's signed with the exact same key as the
original — losing the keystore means any future version can never cleanly update this one; you'd
have to uninstall (losing all app data) and reinstall fresh.

## Room schema files

`ksp { arg("room.schemaLocation", "$projectDir/schemas") }` in `app/build.gradle.kts` exports the
Room schema on every build to `app/schemas/` for migration validation. That directory is
gitignored (schema history isn't meaningful for a personal project without published migrations
to validate against) — if you ever add a migration, temporarily un-ignore it to commit the
before/after schema pair Room needs to test against.
