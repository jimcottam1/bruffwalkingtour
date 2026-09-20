# Bruff Heritage Trail

Self-guided walking tour of four heritage sites in Bruff, Co. Limerick. Two apps
that must behave the same:

- `app/` — Android (Kotlin, OSMDroid map, FusedLocationProvider, minSdk 24)
- `web/` — PWA (vanilla JS modules, Leaflet, browser Geolocation)

`docs/APP_FLOW.md` is the spec for the flow both platforms follow. When you change
behaviour on one, change the other — or record in that doc why they differ.

## Commands

Android (Git Bash; Android Studio's JDK is not on PATH):

    export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
    ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest -q

Web (from `web/`):

    npx vitest run            # unit tests (`npm test` also regenerates data first)
    npx playwright test       # e2e; starts its own server on :3000

Both suites and the Android build should pass before committing.

## Data: edit only `data/waypoints.yaml`

Waypoints, boundary and tour text live there. After editing, run
`npm run gen:waypoints` in `web/`. `BruffTourData.kt` and `web/js/data.js` are
**generated — never hand-edit them.** (`npm run admin:waypoints` is a local GUI.)

## Running the app on an emulator

`scripts/emulator.sh` wraps this (see its header): `start`, `install`, `launch`,
`geo bruff|away`, `shot NAME`, `reset`. To test the flow:

1. `geo away` → tap **Start the walk** → the gate must say you're outside Bruff.
2. `geo bruff` → tap **Start the walk** → the tour starts by itself.
3. Screenshot with `shot`, then look at the image.

Gotchas: `MainActivity` isn't exported, so always enter via `IntroActivity`. A fresh
emulator boot is slow and throws Bluetooth/Play-services ANR dialogs for a minute —
dismiss them and wait before deciding the app is at fault (check `adb logcat` for
`FATAL`/`ANR in com.example.bruffwalkingtour`). Saved progress is stale across
installs; use `reset` for a clean start. Screenshots are 1080x2220 — tap in device
pixels, not the scaled preview.

## Behaviour rules worth knowing

- The boundary gate is **hard**: no "start anyway" in release builds. Debug builds
  only: long-press the gate message to skip it. Don't reintroduce a bypass.
- Every Android screen has an **Exit** action (`ExitHelper`); mid-walk it asks
  whether to start over or keep the place.
- The launcher/splash/logo art is one church drawing kept in step across the
  Android drawables and `web/scripts/create-icons.mjs` (regenerates the PWA PNGs).

## Pitfalls

- `strings.xml`: escape apostrophes as `\'` — an unescaped one fails the whole
  resource merge with an unhelpful "Can not extract resource" error.
- Line endings are mixed (LF/CRLF); git warns on many files. Preserve what a file
  already uses.
- **Web service worker is cache-first**: after changing anything under `web/`
  (JS, CSS, HTML, icons) bump `CACHE_VERSION` in `web/sw.js`, or installed PWAs keep
  serving the old files. Every path in `APP_SHELL` must exist — one 404 makes
  `cache.addAll` fail and the whole worker never installs (this went unnoticed
  before; check with a browser that `caches.keys()` fills up).
- CI (`.github/workflows/build-apk.yml`) runs the Android + web tests on every
  push/PR; only `main` builds and publishes the APK, and it is a **release**
  build (signed with the debug key for now) so the debug-only gate bypass never
  ships. Test the shipped variant with `./gradlew :app:assembleRelease`.
