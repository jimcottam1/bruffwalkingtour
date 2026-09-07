# App flow — canonical spec

The Android app and the web PWA implement the **same tour flow**. This is the
single description both must follow. When you change behaviour on one platform,
change it on the other (or record here, with a reason, why they differ).

Waypoint *data* already has one source (`data/waypoints.yaml`). This document is
the equivalent single source for the *flow logic*.

---

## Screens / pages

| Step | Android | Web |
|------|---------|-----|
| Intro / overview | `IntroActivity` | `index.html` |
| Live guided walk | `MainActivity` | `tour.html` |
| Single stop detail | `WaypointDetailActivity` | `detail.html?id=<id>` |
| Completion summary | `TourCompletionActivity` | `complete.html` |
| Help | `HelpActivity` | `help.html` |

---

## States within the live walk

```
        ┌─────────────┐   inside boundary + Start (or "start anyway")
        │  BOUNDARY   │ ─────────────────────────────────────────────► LIVE
        │    GATE     │
        └─────────────┘   N consecutive "outside" fixes → back to Intro
              ▲                (with a generous delay; never without an
              │                 explicit "start here anyway" escape)
   app launched / resumed
   with NO active session

        ┌─────────────┐
        │    LIVE     │  each accepted GPS fix updates: map centre (unless the
        │             │  user panned), nav bar (direction + distance + ETA),
        │             │  GPS badge, waypoint markers.
        └─────────────┘
              │  within current waypoint's proximityRadius
              ▼
        ┌─────────────┐   haptic + heads-up notification + on-screen card
        │  ARRIVED    │   ("Explore this stop"). Nav text also becomes a link.
        └─────────────┘
              │  open the stop detail
              ▼
        ┌─────────────┐   "Continue" → advance
        │   DETAIL    │   Back / up, IF opened on arrival → also advance
        │             │   Back / up, if opened while merely walking → no change
        └─────────────┘
              │  advanced past the LAST waypoint
              ▼
        ┌─────────────┐   celebration toast → (1.5 s) → completion screen.
        │  COMPLETE   │   Persisted progress cleared here.
        └─────────────┘
```

### "Walked on without continuing" recovery
If the user was notified of arrival at a stop, never tapped Continue, and has
since moved to more than 2× the proximity radius away **and** is now closer to
the next stop, offer once to advance to that next stop. (Android: a dialog.
Web: TODO — not yet implemented.)

---

## Boundary gate rules

- Shown on entering the live screen with **no active session**.
- "Inside": show Start.
- "Outside": show distance to centre, show **"start here anyway"**, and after
  `GATE_OUTSIDE_FIXES_BEFORE_RETURN` (3) consecutive outside fixes, start a
  `GATE_OUTSIDE_RETURN_DELAY_MS` (12 s) timer that returns to the Intro screen.
  A single inside fix cancels the timer and resets the counter.
- Location permission denied → offer Settings or "continue anyway". "Continue
  anyway" enters the live screen in **map-only mode**: the nav bar states that
  navigation needs permission; no position, proximity, or ETA.

---

## Progress persistence  (the part Android was missing)

| Concern | Rule |
|---|---|
| What is stored | current waypoint index, a "session active" flag, a "last active" timestamp |
| Android | `SharedPreferences("bruff_tour_progress")` in `LocationService` |
| Web | `sessionStorage` — `bruff_tour_state` (index + visited) and `bruff_gate_passed` |
| Session becomes active | when the user clears the gate (`beginTourSession()` / `bruff_gate_passed`) |
| On recreate/launch, last active **< 45 min** ago | resume silently, skip the gate |
| On recreate/launch, last active **> 45 min** ago (Android) | ask "Resume (stop N of M) / Start over" |
| Session cleared | on completion, and on an explicit restart ("Take Tour Again" / "Start over") |

Rationale: the detail screen is a long-dwell screen (reading history, images).
The OS reclaims the backgrounded live screen there; without persistence the tour
silently restarts from stop 1. But silently resuming on a *deliberate* relaunch
hours/days later is equally surprising — hence the timestamp and the prompt.

Web currently still resumes silently on any relaunch with `bruff_gate_passed`
set (sessionStorage clears when the tab/app is fully closed, which limits the
blast radius) — TODO: match the Android prompt.

---

## Completion — single path

`LocationService.moveToNextWaypoint()` is the only place the tour can finish:
stepping past the last waypoint sets `tourCompleted` and clears saved progress.
The UI observes `tourCompleted` and does the celebration-toast-then-fade
transition. There must be **no second trigger** (no result-extra shortcut,
no direct `startActivity` for completion elsewhere).

---

## Constants that must match across platforms

| Meaning | Value | Android | Web |
|---|---|---|---|
| Accept GPS fix below accuracy | 50 m | `ACCURACY_THRESHOLD_M` | `ACCURACY_THRESHOLD_M` |
| …or after timeout | 15 s | `ACCURACY_TIMEOUT_MS` | inline `15_000` |
| Re-fetch route after moving | 20 m | (route-to-you removed on Android) | `ROUTE_REFRESH_THRESHOLD_M` |
| Walking speed for ETA | 83 m/min | `WALKING_SPEED_MPM` | `WALKING_SPEED_MPM` |
| Gate return delay | 12 s | `GATE_OUTSIDE_RETURN_DELAY_MS` | `GATE_OUTSIDE_RETURN_DELAY_MS` (still 6 s — TODO) |
| Arrival-exit hysteresis | 5 m | `EXIT_HYSTERESIS_M` | n/a |

---

## Known remaining divergences (web still to do)

1. Gate: no consecutive-fix debounce, no "start here anyway", 6 s delay.
2. No "walked on without continuing" recovery prompt.
3. Arrival cue is a 15 s banner (no haptic/notification — browser limits).
4. Completion is re-checked on `pageshow`; acceptable, but keep it single-path.
