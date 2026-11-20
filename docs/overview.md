# MLBB Plus

> One app. Every role. Help right on top of your game.

**MLBB Plus** is a lightweight Android overlay companion for Mobile Legends: Bang Bang.
It started as a Roam Guide, but it is built to grow into tools for every role — Roam, Jungle, Mid, Gold, and EXP.

Tap the app icon → overlay starts → app auto-closes → you are back in-game.

---

## Mission

Give MLBB players clear, in-game guidance without leaving the match.

- **Stay in-game:** No alt-tabbing, no pausing to read a guide.
- **Be fast:** One tap to start. The app closes itself after launching the overlay.
- **Be practical:** Rotation, pathing, and timing — not walls of text.
- **Stay light:** Small app, no heavy SDKs, no account login.

First feature: **Roam Guide** — jungle pathing, rotation, and gank timing for roamers.

## Vision

Become the all-in-one overlay companion for every MLBB role.

- A roamer opens the app and sees roam rotations.
- A jungler opens the app and sees jungle timers and pathing.
- A mid laner opens the app and sees wave + roam timing.
- A gold laner opens the app and sees farm + positioning.
- An EXP laner opens the app and sees matchup + turtle timing.

Every role gets the same fast pattern: **open → overlay → auto-close**.

Long-term: personalized guides, per-hero tips, and per-patch updates, all inside one app — **MLBB Plus**.

---

## Roadmap

### Phase 1 — Roam Guide (Done)
- [x] Separate Roam Guide from MLBB Assistant into standalone app
- [x] New structure: `ui/` + `ui/widgets/`
- [x] `MainActivity` + `WidgetOverlayService` + `JetOverlay` + `OverlayWidgets`
- [x] `roam_guide.json` in `app/src/main/assets/`
- [x] Rename to **MLBB Plus** — `com.mlbb.plus`, `Theme.MLBBPlus`
- [x] `isMinifyEnabled = true` release build passing
- [x] Cleanup: `res/xml/`, `config/detekt`, old features removed

### Phase 2 — Launcher Polish (In Progress)
- [x] GitHub dark theme — `#0D1117` background, `#161B22` cards, `#2F81F7` accent
- [x] Fix status-bar overlap with `statusBarsPadding()` + `navigationBarsPadding()` + `enableEdgeToEdge()`
- [x] Improved launcher UI: header, Roam Guide card, READY / NEEDS PERMISSION chip
- [x] Auto-start + auto-close on launch
- [x] Re-open app while running shows only **Stop Roam Guide**
- [x] This docs file — `docs/OVERVIEW.md`

### Phase 3 — Expand to All Roles (Next)

`JetOverlay` is generic, so no new service or new app is needed. Each new role = new widget + new JSON + new card in `MainActivity`.

#### 3.1 Jungle Guide — Easiest next, reuses Roam logic
- [ ] Jungle pathing: buff → buff → turtle routes
- [ ] Buff timers, Turtle / Lord timers
- [ ] When to invade vs. farm
- [ ] Gank priority: Gold > Mid > EXP
- File: `JungleGuideWidget.kt` + `assets/jungle_guide.json`

#### 3.2 Mid Guide
- [ ] Wave clear → roam timing
- [ ] When to help Jungle / Gold / EXP
- [ ] Vision spots + bush check timing
- [ ] Power spike by level 4
- File: `MidGuideWidget.kt` + `assets/mid_guide.json`

#### 3.3 Gold Guide
- [ ] Farm pattern: last-hit, freeze, push timing
- [ ] When to rotate to turtle / teamfight
- [ ] Positioning in teamfights, when to stay back
- [ ] Power spike: 1st item / 2nd item timing
- File: `GoldGuideWidget.kt` + `assets/gold_guide.json`

#### 3.4 EXP Guide
- [ ] Lane matchup: winning / losing / even
- [ ] Emblem + spell suggestion per matchup
- [ ] When to help Turtle vs. split push
- [ ] Cut wave timing, when to TP / rotate
- File: `ExpGuideWidget.kt` + `assets/exp_guide.json`

#### 3.5 Role Picker in Launcher
- [ ] Home screen shows 5 cards: Roam / Jungle / Mid / Gold / EXP
- [ ] Tap a role → start that overlay → auto-close
- [ ] Remember last role: next launch auto-starts same role with zero taps
- [ ] Re-open while running → Stop → pick a different role

### Phase 4 — Customization
- [ ] Per-hero picks inside each guide
- [ ] Settings: overlay size, opacity, position
- [ ] Patch notes / data updates via `*_guide.json` files

### Phase 5 — Polish & Release
- [ ] App icon + Play listing assets for MLBB Plus
- [ ] Onboarding: permission flow + first-run tutorial
- [ ] Crash / ANR cleanup, release build verification

---

## How It Works

1. **Launch:** User taps **MLBB Plus** icon.
2. **Check:** `MainActivity` checks `Settings.canDrawOverlays()`.
3. **Pick (future):** User taps Roam / Jungle / Mid / Gold / EXP. If last role is saved, skip this and auto-start it.
4. **Auto-start:** `startForegroundService(WidgetOverlayService)` with the picked role, then `finish()`.
5. **Overlay:** `WidgetOverlayService` starts in foreground, calls `JetOverlay.initialize()` then `JetOverlay.show()`.
6. **Guide:** The picked widget renders inside `JetOverlay` from data in its `*_guide.json`.
7. **Stop:** User re-opens the app while running → sees only **Stop** → stops service → app closes. Or uses Back inside the overlay to hide it.

---

## Directory Structure

```
MLBB-Plus/
├── .github/
│   └── workflows/
│       ├── debug.yml              # Debug build
│       └── release.yml            # Release build (minify = true)
│
├── app/
│   ├── build.gradle.kts           # namespace = com.mlbb, applicationId = com.mlbb.plus
│   ├── proguard-rules.pro         # Tiny rules for overlay + Compose
│   └── src/main/
│       ├── AndroidManifest.xml    # MainActivity + WidgetOverlayService, overlay + FGS permissions
│       ├── java/com/mlbb/
│       │   └── ui/
│       │       ├── MainActivity.kt          # Launcher: GitHub dark UI, role picker, auto-start + auto-close
│       │       ├── WidgetOverlayService.kt  # Foreground service, hosts JetOverlay, tracks isRunning
│       │       └── widgets/
│       │           ├── JetOverlay.kt        # Generic overlay engine (initialize / show / hide)
│       │           ├── OverlayWidgets.kt    # RoamGuideWidget today, split per-role later
│       │           ├── RoamGuideWidget.kt   # Future: split from OverlayWidgets.kt
│       │           ├── JungleGuideWidget.kt # Future
│       │           ├── MidGuideWidget.kt    # Future
│       │           ├── GoldGuideWidget.kt   # Future
│       │           └── ExpGuideWidget.kt    # Future
│       ├── res/
│       │   ├── values/
│       │   │   ├── strings.xml    # app_name = MLBB Plus
│       │   │   ├── themes.xml     # Theme.MLBBPlus, GitHub dark
│       │   │   └── colors.xml     # github_bg, github_card, github_blue, etc.
│       │   ├── mipmap-*/          # Launcher icons
│       │   └── drawable/          # Icons / backgrounds
│       └── assets/
│           ├── roam_guide.json    # Roam Guide data
│           ├── jungle_guide.json  # Future
│           ├── mid_guide.json     # Future
│           ├── gold_guide.json    # Future
│           └── exp_guide.json     # Future
│
├── docs/
│   └── OVERVIEW.md                # This file: mission, vision, roadmap, structure
│
├── gradle/
│   └── libs.versions.toml         # Compose, Activity, Lifecycle versions
│
├── settings.gradle.kts            # Only :app
└── README.md                      # Quick start (optional, points to docs/OVERVIEW.md)
```

### Key Files

| File | What it does |
| :--- | :--- |
| `ui/MainActivity.kt` | Launcher. Checks overlay permission, picks role, auto-starts, auto-closes, shows Stop when running. |
| `ui/WidgetOverlayService.kt` | Foreground service. Owns the overlay lifecycle, tracks `isRunning`. |
| `ui/widgets/JetOverlay.kt` | Reusable overlay engine. Any future role can reuse it. |
| `ui/widgets/OverlayWidgets.kt` | Actual guide UI. `RoamGuideWidget` is the first one. Split per-role before it gets too big. |
| `assets/roam_guide.json` | Data for Roam Guide. Content can change without changing code. |
| `res/values/themes.xml` | `Theme.MLBBPlus`, GitHub dark, status-bar colors. |

---

## Adding a New Role Guide

The widget is data-driven — no new Kotlin per role.

1. Add `<role>_guide.json` to `app/src/main/assets/`: `roam_guide.json`, `jungle_guide.json`, `mid_guide.json`, `gold_guide.json`, `exp_guide.json` (lowercase role name + `_guide.json`).
2. Use the same shape as `roam_guide.json` (below).
3. In the overlay, tap the selector in the widget header → pick the **role** → pick the **type**. The widget loads that file and adapts instantly.

A role with no file still works (timer, objective, spell tracker); the role menu marks it `no guide`.

### JSON shape the widget reads

- `meta.patch` — shown in the expanded view (optional).
- `blessings[]` — `{ id, name, effect }` (optional; shown when a type's `blessing_id` matches).
- `roles[]` — one entry per **type** in the type menu (`plans` or `types` also accepted as the key):
  - `id`, `label` (`"Initiator - Start Fight"` → menu shows the full label, header shows the part before ` - `)
  - `blessing`, `blessing_id`, `heroes` (strings or `{ "name": ... }`), `emblem` (optional)
  - `objective_templates` — `turtle_contest`, `turtle_trade`, `lord_contest`, `lord_trade` (optional)
  - `steps[]` (required, at least one) — `startSeconds`, `endSeconds`, `instruction` (or `description`), and optional `timeLabel`, `phase`, `callout`, `checklist[]`, `vision`, `decision`, `dont`

Types with no `steps` are skipped. A file that is missing or invalid is treated as "no guide" (see logcat tag `RoamGuideWidget`).

That is why the app is called **MLBB Plus**, not just Roam Guide.

---

## Build

- **Debug:** Run `.github/workflows/debug.yml` in Actions, or `./gradlew :app:assembleDebug`
- **Release:** Run `.github/workflows/release.yml`, or `./gradlew :app:assembleRelease`
- Release uses `isMinifyEnabled = true`, `isShrinkResources = false`.

## Permissions

- `SYSTEM_ALERT_WINDOW` — draw overlay on top of game
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — keep overlay alive
- `POST_NOTIFICATIONS` — show “MLBB Plus running” notification

---

*MLBB Plus is a fan-made companion tool. Not affiliated with Moonton.*
