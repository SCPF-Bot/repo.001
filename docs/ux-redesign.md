# MLBB Plus — Android UI/UX Redesign

**Source of truth:** `repo.001/docs/overview.md` (product intent) + code in `repo.001/app/src/main/java/com/mlbb/plus/ui/` (technical reality). Docs were re-synced to the bundled assets on 2026-11-27 — see `overview.md` §14 and `refactoring-report.md`’s addendum for the data-refresh record.
**Stack:** Kotlin 2.3.21, Jetpack Compose + Material 3 (BOM 2026.06.00), single Activity + foreground-service overlay, bundled JSON, Coil 3. No backend, auth, DI, Room, or tests in this snapshot.
**What changed in this pass:** new design-system files + reusable components + navigation/view-model foundations + back-button fix + predictive back. **Plus a 2026-11-27 data-sync pass** after the bundled JSON was refreshed (heroes 2026-11-23, items 2026-11-26, guides 2026-11-20): one data correction (Masha’s wrong hero ID), four code corrections caused by the new data (`shortVersion`, patch date source, Settings staleness guard, version-stamp leak), the recreated/extended `scripts/validate_assets.py`, and all docs synced to the assets. No business logic, API, or JSON schema changed. Full files are in the repo (see §7); this document is the rationale and the migration guide.

> **How to read this:** §1–§9 follow the requested output format exactly. Conflicts between `overview.md` and code are flagged inline as **[CONFLICT]** and collected below. Assumptions are flagged as **[ASSUMPTION]**.

---

## Conflicts & assumptions (read first)

| # | Conflict | Resolution in this redesign |
|---|---|---|
| C1 | ~~**Item count:** `overview.md` said 41 items (40 active); `readme.md` said 107; `items.json` contains **99 active items**.~~ **RESOLVED 2026-11-27** — the JSON was refreshed after the docs were written; all docs now state 99 active items with the growth/removal history. | Followed code/data: 99. Docs synced. No UI change depends on the count. |
| C2 | **Grid:** `refactoring-report.md` and `structure.md` claimed “adaptive grids”; code uses `GridCells.Fixed(5)` with the comment “PHONE-PROOF GRID — `GridCells.Fixed(5)`, NEVER Adaptive” (Adaptive gave 3 columns ≈102dp portraits on a phone). | Docs corrected to match code. Proposal stands: `GridCells.Adaptive(minSize = 64.dp)` — equals 5 columns on a 360dp phone, scales on tablets/foldables. See §5. |
| C3 | **Theme file:** `overview.md` §11 says `MLBBPlusTheme.kt` was *not* changed; it now contains both the overlay theme (`MLBBPlusTheme`) and the app theme (`AppUiTheme`). | Follow code. Keep the app/overlay boundary; extend `AppUiTheme` only. |
| C4 | ~~**Knowledge Base:** `SettingsScreen.kt` + `KnowledgeBase.kt` exist in code but were absent from `overview.md` §5.~~ **RESOLVED 2026-11-27** — now documented in `overview.md` §5 as a shipped capability. | Treated as shipped; redesign covers it inside Settings (§5.8). |
| C5 | **Missing screens:** prompt asks for Splash/Onboarding and Form/Create. Neither exists: no splash activity, no onboarding flow, no forms/auth (offline reference app). | Splash = theme `windowBackground` only (no new activity). Onboarding = dismissible Quick Start (existing copy), first-run only. Form/Create = **N/A** — no user-generated content; adding one would violate “no new features.” |
| C6 | **Architecture:** prompt asks for MVVM + navigation graph; `overview.md`/`refactoring-report.md` explicitly say *no* ViewModel/Hilt/Room/Navigation “solely for fashion.” | Lightweight `AppViewModel` (StateFlow, no Hilt) + sealed `AppRoute` (no Navigation dependency). Staged adoption; current startup untouched. See §7. |
| C7 | **Theme:** prompt asks for light + dynamic color; app is dark-only and overlay needs fixed contrast over gameplay. | Light scheme + dynamic-color support added to `AppUiTheme`, but **default stays dark** until screens migrate off `AppUiColors.*` (315 usages). Overlay never uses dynamic color. See §4. |
| C8 | **Overlay type:** overlay forces `FontFamily.Monospace` for *all* text (body included). | Keep mono for timers/numbers only; body migrates to sans. Overlay behavior otherwise frozen. |
| C9 | **Hardcoded values:** 131 hardcoded `fontSize`, 315 `AppUiColors.*` usages, screen copy hardcoded in Kotlin. Prompt requires resources/tokens. | New code uses tokens + `strings.xml`/`dimens.xml`. Full migration is staged (see §7) — rewriting 6,000 lines in one pass without a build would be irresponsible. |
| C10 | **Hero relationships:** `Hero` has both `counters/counteredBy/synergies` *and* `goodAgainst/badAgainst/goodWith` (verified identical for all 133 heroes). Screens use the latter; overview describes “toughest / favorable / best allies.” | Keep both fields (no schema change). UI labels must match the fields actually rendered (“Works against / Bad against / Best partners”). Flagged for data-owner cleanup. |
| C11 | **Stale Roam guide:** `roam_guide.json` is pinned to patch **2.2.16a** (updated 2026-11-18) while heroes/items/patch and the other four guides are 2.2.23 — yet it declares `meta.latest = true`, so Settings showed a green **LATEST** badge for the one out-of-date guide (and Roam is the default role). | **Code fixed:** staleness now keys off the patch number, not the self-declared flag; the validator warns on the mismatch. **Data action remains:** refresh Roam content to 2.2.23 (hero pools, Sprint 100s→75s, Chastise Pauldron, Antique Cuirass, Concussive Blast). Highest-priority data task. |
| C12 | **Version stamp leaked into UI:** `heroes.json.version` is now `2.2.23-tier-2026-11-23` and `displayVersion` passed it through. The Patch header read “Patch 2.2.23-tier-2026-11-23”, and the guide-coverage card **plus the in-match overlay patch label** called all five guides “older than” the live patch — including the four written for 2.2.23. | **Fixed:** added `PatchNotesFile.shortVersion` (bare patch number) and used it in `PatchNotesScreen`, `OverlayWidgets`, `SettingsScreen`, `HomeScreen`, `HeroesScreen`, `ItemsScreen` — also removing three duplicated `substringBefore("-")` expressions. |
| C13 | **Wrong hero ID:** `patch_notes.json` listed Masha as `heroId: 87`; hero 87 is **Baxia** (Masha is 88). Masha appeared twice on the Patch screen and that row opened **Baxia’s** detail. | **Fixed** the ID to 88 (heroes.json manifest was already correct). Merge now de-duplicates to one Masha row → **13** hero changes. All other 9 hero + 2 item IDs cross-checked by name: correct. Validator now enforces this. |
| C14 | **Patch date wrong:** `PatchData.date` came from `heroes.json.updated` (2026-11-23, the data refresh), so the patch card implied 2.2.23 landed on 23 Nov instead of **11 Nov**. | **Fixed:** prefer `heroes.json.patchDate`, fall back to `updated`, then `patch_notes.json.date`. |
| C15 | **Item patch notes never surface:** `items.json.patchChanges` holds 10 entries (legacy removals, Chastise Pauldron nerf, Antique Cuirass buff, Twilight Armor removal), but `loadPatchNotes()` merges only `heroes.json.patchChanges` + its 2 `equipmentPatchChanges`. | **Flagged, not changed** (would alter what the Patch screen shows). Decision needed: merge with de-duplication, or drop the duplicate copy in `heroes.json`. |
| C16 | **Corrupt/inert data fields:** `heroes.json.fixNotes` has its first note split into 331 single-character array elements; `items.json.notes` has five entries that are orphaned dict keys (`broadsword`, `portraitPattern`, …) with values lost; `items.json.patchChanges` cites a missing `items_portraits.md`. None are read by app code. | **Flagged** for the data owner; no code impact. Validator does not fail on these (they are not referential defects). |
| C17 | **Docs/repo drift mechanism:** `.github/workflows/extract.yml` deletes **everything** except `.github`, `.git` and root archives, then re-extracts and commits. That is why `scripts/validate_assets.py` (referenced by readme + report) was missing and why docs lagged the data. | **Validator recreated + extended.** Flagged: any archive push will erase hand-maintained `docs/` and `scripts/` unless they are inside the archive or the delete step is narrowed. |

**[ASSUMPTION A1]** Phones-first, tablets/foldables secondary — existing 600dp (rail) and 840dp (two-pane) breakpoints are kept.
**[ASSUMPTION A2]** Offline-first and single-module stay. No remote refresh, accounts, or analytics are added.
**[ASSUMPTION A3]** Dark is the default brand (gaming context); light is a supported secondary, not the default.
**[ASSUMPTION A4]** No new dependencies. No Hilt, Room, Navigation Compose, Lottie, or image-stack changes.
**[ASSUMPTION A5]** Overlay *behavior* (timers, drag, fade, spell tracker) is frozen. Only typography/color-token hygiene is proposed.

---

## 1. Executive Summary

- **Biggest win — the core task is now one obvious tap.** Home already remembers Position + Playstyle; the redesign makes the primary action a proper Material 3 button (not a floating custom row that covers content), with permission state, running state, and Snackbar/haptic feedback. Target: **1 tap to launch** when permission is granted (unchanged), **0 dead ends** when it is not.
- **Critical bug fixed — detail screens had no visible Back.** `HeroDetailScreen`/`ItemDetailScreen` accepted `onBack` but never rendered it; users had to discover system Back. Both now show a back bar (existing `DetailBackBar`). This alone removes the worst dead-end in the app.
- **A real design system replaces 315 scattered color references.** New `Color.kt` / `Type.kt` / `Dimens.kt` tokens (M3 light + dark + dynamic-color-ready, WCAG AA) plus `AppComponents.kt` (button, search, card, top bar, empty/error/loading states). New code uses tokens; old screens migrate incrementally without breaking the build.
- **Navigation stays at 5 destinations — correctly.** Bottom Navigation on phones, Navigation Rail ≥600dp (already implemented). No drawer (5 items fit; a drawer would add taps). Predictive back enabled; deep links specified but staged.
- **Search/filter becomes findable and accessible.** The horizontally-scrolling “one-bar” filter hides Clear/Sort off-screen and uses 32–40dp targets. Redesign: full-width search + filter chips + visible sort + Clear, all ≥48dp, with result counts and empty states.
- **First-run onboarding without new screens.** The existing 3-step Quick Start becomes a dismissible first-run card (Skip, no sign-in, progressive disclosure). Returning users see a clean dashboard. No new activity, no forced setup.
- **Accessibility is now a gate, not an afterthought.** 48dp targets, 11sp minimum meaningful text (up from 8sp), TalkBack labels, font-scale to 200%, color-plus-label (never color alone), keyboard focus. Checklist in §8; device matrix in §9.
- **What did *not* change (deliberately):** app name/purpose, JSON schemas, overlay engine, offline data, single-module structure. Every change is reversible and staged behind the existing APIs.

**Success metrics (measure before/after on the same device):**

| Metric | Before (code-measured) | Target |
|---|---|---|
| Taps to launch guide (permission granted) | 1 (floating button) | 1 (primary button) + feedback |
| Taps to find a hero (from Home) | 2 (Heroes tab + search) | 2, with visible Clear + count |
| Visible Back on detail screens | 0 (system Back only) | 1 visible control |
| Min touch target (filter controls) | 32dp | 48dp |
| Min meaningful text size | 8sp | 11sp |
| Screens with loading/empty/error states | Partial (empty only) | All catalogue screens |
| TalkBack pass (nav + search + detail) | Unverified | Pass (§8) |

---

## 2. UX Audit Findings

Top 3 journeys (from `overview.md` §3/§5): **(J1) First launch → choose Position/Playstyle → enable overlay. (J2) Core task: browse Heroes/Items/Patch and open a detail. (J3) Returning user: launch the floating guide and use it in-match.**

| # | Problem (heuristic / law) | Impact | Fix (this redesign) |
|---|---|---|---|
| U1 | **No visible Back on Hero/Item detail** — `onBack` param unused; only system Back works. *Nielsen: User control & freedom; Visibility.* | Users feel trapped; TalkBack users have no discoverable exit. | Render `DetailBackBar` on both details (done in repo). Long-term: `AppTopBar` (§5). |
| U2 | **Floating Launch button covers content** — custom `Row` at `BottomEnd` + 84dp spacer hack; ignores insets; no pressed/disabled states. *Fitts: small/awkward target; Nielsen: Visibility of system status.* | Missed taps, covered cards, no feedback after tap. | Primary `AppButton` in Scaffold slot / sticky bottom bar with `WindowInsets`; Snackbar + haptic on launch. |
| U3 | **Filter bar hides actions** — `OneBarFilter` scrolls horizontally; Clear/Sort off-screen on 360dp phones; 32dp dropdowns. *Fitts: targets <48dp; Cognitive load: hidden state.* | Users don’t know filters are active; mis-taps. | Full-width `AppSearchBar` + chip row + sort row; Clear always visible when active; 48dp targets. |
| U4 | **Tiny text** — 8sp item prices, 10sp hero names, 10sp “Meta snapshot.” *Accessibility / cognitive load.* | Unreadable at 200% font scale; fails WCAG. | Minimum 11sp (`labelSmall`); grid labels move to `bodySmall`/`labelMedium`. |
| U5 | **Detail selection not saveable** — `selectedHero/Item` use `remember`, not `rememberSaveable`; process death loses detail. Search/filter/tab *are* saveable (inconsistent). *Nielsen: Error prevention.* | Rotation/process death dumps user to list. | Store hero/item **IDs** with `rememberSaveable`; resolve via `GameData.*ById`. |
| U6 | **No feedback after Launch** — tap starts foreground service silently; user must switch apps to verify. *Nielsen: Visibility of system status.* | “Did it work?” doubt; duplicate taps. | Snackbar (“Guide running — switch to your game”) + running-state chip already on Home. |
| U7 | **Permission copy split across 3 places** — Home Quick Start, RolePanel, Settings each explain overlay permission differently. *Cognitive load.* | Confusion about “Display over other apps.” | Single source of truth in Settings; Home shows status + one CTA. Copy in §5. |
| U8 | **Fixed(5) grid doesn’t adapt** — stretched on tablets (960dp), cramped on foldables. *Responsiveness.* | Wasted space / tiny cards on large windows. | `GridCells.Adaptive(64.dp)` (§5). |
| U9 | **Monospace body text (overlay)** — entire overlay in mono. *Readability.* | Slow reading in-match; fails font-scale expectations. | Mono for timers only; sans for body (§4). |
| U10 | **No loading/error states** — data loads synchronously in `onCreate`; screens assume data present. *Nielsen: Help users recover.* | Blank/empty on slow devices; no retry path. | `LoadingState`/`ErrorState` components; staged async load (§7). Bundled data is fast, so this is low-urgency but required for QA. |
| U11 | **Custom headers instead of TopAppBar** — `AppHeader`/`ScreenTitleBar` are Rows, not accessible headings; no scroll behavior. | TalkBack heading navigation broken; inconsistent. | `AppTopBar` (M3) on all screens; headers become `headlineSmall`. |
| U12 | **Edge-to-edge missing** — Scaffold handles nav-bar padding only; no status-bar insets, no `enableEdgeToEdge()`. | Content under status bar on API 35+; gesture-nav overlap. | `enableEdgeToEdge()` + `WindowInsets` (§7). |
| U13 | **False staleness + wrong links from data drift** (found in the 2026-11-27 data sync): the stamped `heroes.json` version made the Patch card and the **in-match overlay** label all five guides “older than” the live patch; Masha’s wrong `heroId` opened Baxia; the patch card showed the data-refresh date as the patch date; Settings badged the genuinely stale Roam guide as LATEST. *Nielsen: Visibility of system status; Error prevention.* | Users distrust correct guides, land on the wrong hero, and mis-read patch timing — in-match, where errors cost games. | `shortVersion` everywhere a bare patch number is meant; `patchDate` preferred for the card date; staleness keyed off the patch number; Masha ID corrected; validator now enforces ID ↔ name and version agreement (C12–C14). |

**Cognitive-load note:** Home currently stacks Patch banner + Quick Start + RolePanel + Mechanics + Movers + Quick Actions + Meta heroes on one scroll. That’s appropriate for a dashboard, but the *order* buries the primary task (Position/Playstyle/Launch) below the fold on small phones. §5 reorders: **Guide setup first, discovery second.**

---

## 3. New Information Architecture

```
MLBB Plus (single Activity, 5 tabs — unchanged destinations)
│
├── Home ─────────────────────────────── J1 + J3 (core task entry)
│   ├── Guide setup (Position → Playstyle → Enable/Launch)  ← PRIMARY
│   ├── Patch banner (→ Patch)
│   ├── Mechanics (progressive disclosure)
│   ├── Patch movers (→ Hero detail)
│   └── Quick actions (→ Heroes / Items / Patch) + Meta heroes
│
├── Heroes ───────────────────────────── J2
│   ├── Search + filters + sort (48dp, visible Clear)
│   └── Hero detail (Back bar, tabs: Overview / Counters / Synergies)
│       ├── → Item detail (core build)
│       └── → Related hero detail
│
├── Items ────────────────────────────── J2
│   ├── Search + filters + sort
│   └── Item detail (Back bar, tabs: Overview / Build path / Hero links)
│       └── → Hero detail
│
├── Patch ────────────────────────────── J2
│   ├── Tabs: Current / Advanced / Nov 01 (only when data exists)
│   └── Change cards → Hero / Item detail
│
└── Settings ─────────────────────────── J1 (permission) + diagnostics
    ├── Floating guide permission (single source of truth)
    ├── Startup preference (auto-start last role)
    ├── Help & Knowledge Base (two-pane ≥840dp, list→reader on phones)
    └── Data status & troubleshooting (expandable)
```

**What stays / moves / is removed:**

- **Stays on Home:** Guide setup (moved to top), patch banner, movers, quick actions, meta heroes.
- **Moves to secondary:** Mechanics “show all” (already collapsible — kept), data diagnostics (already in Settings — kept), Knowledge Base (stays in Settings — kept, now documented as shipped per C4).
- **Removed/merged:** Nothing removed. The floating Launch button is merged into the Scaffold primary-action slot (same destination, better placement). No drawer, no new tabs.
- **Navigation model:** Bottom Navigation (<600dp) / Navigation Rail (≥600dp) — already in `MainActivity`, kept. Back: detail → originating tab (existing `heroReturnTab` logic, kept); tab → Home; Home → exit. Predictive back enabled via manifest. Deep links (`mlbbplus://hero/{id}` etc.) specified in `AppRoute.kt` but **staged** (manifest + parser + fallback tests required first).

---

## 4. Design System

All tokens live in `ui/theme/` (`Color.kt`, `Type.kt`, `Dimens.kt`) and are wired through `AppUiTheme`. **App screens only** — the overlay keeps `MLBBPlusColors` (see trade-off below).

### 4.1 Color

| Token | Dark (default) | Light | Usage |
|---|---|---|---|
| `primary` | `#FFD45A` gold | `#7A5A00` dark gold | Primary buttons, selected states |
| `onPrimary` | `#171717` | `#FFFFFF` | Text on primary (≥6:1 both modes) |
| `background` | `#0B0E14` ink | `#FFFFFB` warm white | Screen background |
| `surface` / `surfaceContainer` | `#151B27` | `#FFFFFF` / `#F0EDE4` | Cards (tonal, no heavy borders) |
| `onSurface` | `#F2F5FA` | `#171717` | Body text (≥15:1 dark, ≥16:1 light) |
| `onSurfaceVariant` | `#ADB8C8` | `#4A5568` | Secondary text (≥7:1 both) |
| `outline` | `#2B394B` | `#8A8F98` | Hairlines only |
| `error` | `#E85B70` | `#BA1A2A` | Errors (always + icon/label) |
| `secondary` / `tertiary` | teal `#42D6C5` / blue `#68AFFF` | `#006A5E` / `#0057A8` | Info, links, patch badges |

- **Dynamic color (Android 12+):** supported via `dynamicDark/LightColorScheme`, but `primary` is pinned to brand gold so the app stays recognizable. Wallpaper tones flow through surfaces/containers only. Opt-in (`dynamicColor = true`); default `false` until light-mode migration completes.
- **Trade-off — overlay excluded from dynamic color:** the overlay floats over gameplay (bright/dark frames). Dynamic palettes could wash out text mid-match. Overlay keeps fixed high-contrast tokens. This is intentional and documented in `Color.kt`.
- **Tier/patch colors** (`TierS/A/B`, `PatchBuff/Nerf/…`) remain in `AppUiColors` for now and always pair with a text label (e.g. “S+”, “BUFF”) — never color alone.

```kotlin
// ui/theme/Color.kt — excerpt (full file in repo)
val AppDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD45A), onPrimary = Color(0xFF171717),
    background = Color(0xFF0B0E14), onBackground = Color(0xFFF2F5FA),
    surface = Color(0xFF151B27), onSurface = Color(0xFFF2F5FA),
    /* … surfaceContainer*, outline, error … */
)
```

### 4.2 Typography

System sans-serif (Roboto) — no custom font download (offline-first, font-scale friendly). Full scale in `Type.kt`:

| Role | Size / Line | Weight | Used for |
|---|---|---|---|
| `displaySmall` | 24/32 | Bold | Detail hero/item name |
| `headlineSmall` | 20/28 | Bold | Screen titles |
| `titleLarge` | 17/24 | SemiBold | Card titles (guide, patch) |
| `titleMedium` | 15/22 | SemiBold | List titles |
| `bodyLarge` | 16/24 | Normal | Playstyle, lore |
| `bodyMedium` | 14/20 | Normal | Descriptions, patch body |
| `bodySmall` | 12/16 | Normal | Captions, counts |
| `labelLarge` | 14/20 | SemiBold | Buttons |
| `labelMedium` | 12/16 | Medium | Chips, tabs |
| `labelSmall` | 11/14 | Bold, 0.8 tracked | Section eyebrows |

**Overlay exception:** timers/countdowns stay monospace (tabular numbers prevent layout jitter). All other overlay text migrates to this sans scale.

### 4.3 Spacing & layout

8dp grid (`AppDimens`): screen margin 16dp (24dp ≥600dp), card gap 12dp, section gap 24dp, max content width 960dp (existing — kept). Breakpoints: <600dp bottom nav, 600–839dp rail, ≥840dp rail + two-pane. No fixed heights that clip content; lists use `LazyColumn`/`LazyVerticalGrid` with stable keys (`hero.id`, `item.id` — already used, kept).

### 4.4 Shape & elevation

Cards 12dp, large cards 16dp, sheets 28dp top, dialogs 28dp, inputs 12dp, chips 8dp, buttons pill (M3 default). Tonal elevation (`surfaceContainer*`) + 0dp shadow by default; borders only for inputs/outlined controls. Replaces the current “every card has a 1dp border” pattern.

### 4.5 Components

Standardized in `AppComponents.kt` (see §7 for full code): `AppButton` / `AppOutlinedButton` / `AppTextButton`, `AppSearchBar`, `AppCard`, `AppTopBar`, `EmptyState`, `ErrorState`, `LoadingState`. Every component defines default / pressed (M3 ripple) / disabled / loading / error states and is ≥48dp. Existing `UiKit.kt` primitives (`TierBadge`, `SectionLabel`, filter controls) are kept and migrated to these tokens incrementally.

### 4.6 Icons & imagery

Material Symbols / outlined icons, 24dp (18dp small), consistent `contentDescription` (decorative = null, meaningful = localized string). Hero/item portraits via Coil 3 (existing `AssetImage.kt` — kept, including fallback initials). Empty states use a 48dp icon + title + message + optional action (never a blank screen).

### 4.7 Motion

150ms (fast: chips, toggles) / 250ms (medium: cards, sheets) / 300ms (slow: screen transitions). Fade + slide for navigation; shared-element for hero→detail is **staged** (requires image-transition plumbing — not in this pass). No motion blocks input. Respect reduced motion: when the system animator duration is 0, skip non-essential animation. Overlay idle-fade (existing 4s → 0.6 alpha) is unchanged.

---

## 5. Screen Redesigns

Conventions: **Before** = current code behavior. **After** = redesigned behavior. Code excerpts are in §7; full files in repo.

### 5.1 Splash & Onboarding (first launch)

1. **Purpose:** Show value in ≤3 steps, allow skip, never force sign-in (no auth exists).
2. **Layout (top→bottom):** Splash (theme `windowBackground` + app name, no new activity) → first-run Quick Start card on Home (Position → Playstyle → Enable Widget) with **Skip** / **Get started** → Guide setup.
3. **Hierarchy:** 1) “Choose your position” 2) “Choose a playstyle” 3) “Enable the floating guide.”
4. **UI improvements:** Reuse existing `QuickStartCard` copy verbatim (already plain-language). Add dismiss (persist `onboarding_seen` in existing `mlbb_plus_prefs`). No carousel, no pager — progressive disclosure on the dashboard itself.
5. **Interactions:** Skip dismisses permanently; Get started scrolls to Guide setup; rotation/process death preserves dismissal (saveable/prefs).
6. **States:** First run = card visible. Returning = card hidden, Guide setup at top. Permission denied = step 3 shows “Open Android settings” CTA (existing behavior, kept).
7. **Copy:** Keep existing: “Choose your position — Pick the role you play most often.” / “Choose a playstyle — The guide updates to match your strategy.” / “Allow the guide to appear — Tap Enable Widget, then turn on ‘Display over other apps’.”

*Trade-off:* A dedicated 3-page onboarding activity would be more “premium” but adds an activity, back-stack complexity, and a new feature surface. The dismissible card achieves the same comprehension with zero new navigation. Chosen for safety.

### 5.2 Home & Dashboard

1. **Purpose:** Let the player pick Position + Playstyle and launch the floating guide in one glance.
2. **Layout:** `AppTopBar` (“MLBB Plus”, patch subtitle) → Guide setup card (Position/Playstyle dropdowns, win/avoid, picks, loadout, status chips, primary Launch button) → Patch banner → Mechanics → Movers → Quick actions → Meta heroes.
3. **Hierarchy:** 1) Guide setup + Launch 2) Patch context 3) Discovery (movers/meta).
4. **UI improvements:** Guide setup moves **above** Patch banner (currently banner + Quick Start push it down). Launch becomes a full-width `AppButton` (48dp+, disabled/loading states) instead of a floating custom row. Status chips keep text labels (“PERMISSION ON”, “PATCH 2.2.23”). Max width 960dp kept.
5. **Interactions:** Tap Position/Playstyle → dropdown (existing, kept). Tap Launch → permission check → service start → Snackbar + haptic. Tap pick/mover/meta hero → Hero detail. Pull-to-refresh: N/A (offline data, no network) — do not add.
6. **States:** Loading (staged, §7), empty (no guide file → existing “overlay still gives you timer…” message, kept), error (staged), offline (N/A — offline-first by design; show patch version so staleness is visible).
7. **Copy:** Button: “Launch guide” / “Enable widget” (action-oriented, kept). Status: “Overlay is running” / “Overlay is not running” (kept).

### 5.3 Heroes — Search & List

1. **Purpose:** Find any of 133 heroes in ≤2 taps and open detail.
2. **Layout:** `AppTopBar` (“Heroes”, “Patch 2.2.23 • N heroes”) → `AppSearchBar` (full width) → filter chips (Role, Tier) + Sort dropdown + Clear (visible when active) → count (“N of 133 heroes”) → grid.
3. **Hierarchy:** 1) Search 2) Filters/sort 3) Grid.
4. **UI improvements:** Replaces scrolling `OneBarFilter` (hidden actions, 32dp targets). Grid: `GridCells.Adaptive(64.dp)` (5 cols on phones, more on tablets). Labels 11sp+ (up from 10sp). Tier badge keeps text + color.
5. **Interactions:** Type → instant filter (existing substring match, kept). Chip/dropdown → filter. Clear → reset all (existing logic, now visible). Tap card → detail. Long-press: N/A (no context menu in current feature set — do not invent). Scroll preserves search/filter (existing `rememberSaveable`, kept).
6. **States:** Empty → `EmptyState` (“No results — Try fewer filters” + Clear action). Loading/error → staged components. No results must never be a blank grid.
7. **Copy:** Placeholder “Search heroes” (kept). Count “N of 133 heroes” (kept). Clear “Clear” (kept).

### 5.4 Hero Detail

1. **Purpose:** Answer “Should I pick this hero, and how?” — stats, build, matchups, synergies.
2. **Layout:** `AppTopBar` (Back + hero name) → header (portrait 72dp, name `displaySmall`, title, role/lane/tier/WR) → tabs (Overview / Counters / Synergies, M3 `TabRow`) → tab content.
3. **Hierarchy:** 1) Identity + tier/WR 2) Tabs 3) Lore/specialty/spells/build or matchup rows.
4. **UI improvements:** **Back bar added** (bug fix, done). Tabs use M3 semantics (`Role.Tab` + selected — already present, kept). Core-build rows become `AppCard` rows with item image + price. Matchup rows keep 52dp portraits but labels move to 11sp.
5. **Interactions:** Back (bar or system) → originating tab (existing `heroReturnTab`, kept). Tap related hero/item → that detail (existing, kept). Tab switch resets per hero (existing `remember(hero.id)`, kept).
6. **States:** Empty matchup/synergy → existing “No … data in heroes.json” text, restyled as `EmptyState` with provenance (“Statistical association, not a guarantee” — per overview §6). Missing portrait → Coil fallback initials (existing, kept).
7. **Copy:** Tabs “Overview / Counters / Synergies” (kept). Provenance: “Meta snapshot {date}” (kept) + matchup caveat (new, required by overview principles).

### 5.5 Items — Search & List

Same pattern as Heroes (§5.3). Differences: filters = Category (Attack/Magic/Defense/Movement/Roam/Jungle); sort = Recommended / Price low→high / Price high→low (existing, kept). Removed items excluded from catalogue (existing `status == "active"` filter, kept). Price label moves from 8sp to `labelMedium` (12sp). Empty state: “No items match — Clear filters.”

### 5.6 Item Detail

1. **Purpose:** Answer “What does this item do, how do I build it, who uses it?”
2. **Layout:** `AppTopBar` (Back + item name) → header (portrait, name, category, tier/price) → tabs (Overview / Build path / Hero links) → stats, build-path chain (→ separators), hero synergy/effective-against rows.
3. **Hierarchy:** 1) Identity + price 2) Stats 3) Build path 4) Hero links.
4. **UI improvements:** **Back bar added** (done). Build-path chain keeps → separators but uses 48dp rows. “Hero links” tab label kept (matches actual fields: synergies vs. effective-against — see C10).
5. **Interactions:** Back → Items list. Tap build-path/related item → that item detail. Tap hero → Hero detail (existing cross-links, kept).
6. **States:** No build path → “No build path in items.json” as `EmptyState` (kept copy). Missing portrait → fallback (kept).
7. **Copy:** “Granted upgrade” / “Tier X • N Gold” (kept). Tab “Hero links” (kept — clearer than “Matchups” for item→hero direction).

### 5.7 Patch Notes

1. **Purpose:** Show what changed in the current patch and link to affected heroes/items.
2. **Layout:** `AppTopBar` (“Patch notes”) → header (“Updated 2026-11-11 • Patch 2.2.23 • Season 42”) → server tabs (Current only — the shipped `patch_notes.json` is flat, with no `originalServer`/`advancedServer`/`nov01` blocks, so Advanced and Nov 01 stay hidden by existing logic) → summary card → highlights → hero changes → item changes → battlefield/system/equipment → meta-by-lane → guide coverage → source footer.
3. **Hierarchy:** 1) Patch identity 2) Hero/item changes 3) System notes.
4. **UI improvements:** Tabs use M3 `TabRow` (replaces custom pill row). Change cards use `AppCard` with buff/nerf badge **+ text label** (existing `patchBadgeColor` + type text, kept). Meta-by-lane and guide-coverage cards are data-derived (existing fix for hardcoded Roam card, kept).
5. **Interactions:** Tap hero/item change → detail (existing, kept). Tab switch preserves selection (existing `rememberSaveable`, kept).
6. **States:** No Advanced/Nov01 data → tabs hidden (existing, kept). Empty change details → “No additional change details supplied.” (existing, kept).
7. **Copy:** “Tap for hero details →” (kept). Stale-guide warning “⚠ older than {patch}” (kept — required by “patch-aware, not patch-assumptive”).

**Verified current content (2026-11-27):** the Patch screen lists **13 hero changes** — the 10 in `patch_notes.json` (Popol and Kupa revamp; Xavier, Lunox, Freya, Aldous, Kaja, Melissa buffs; Gloo, Masha, Atlas nerfs) plus Lukas, Karrie and Kagura from the `heroes.json` manifest, de-duplicated by hero ID (manifest entry wins where both files list a hero, so Freya shows `BUFF+NERF`). Also 2 item changes (Demon Hunter Sword, Dominance Ice — both “adjust”, no direct stat change), 2 equipment changes (Chastise Pauldron nerf, Antique Cuirass buff), 3 battlefield and 2 system notes, 4 highlights, meta notes, and the Roam guide link.

**Data-integrity requirements this screen depends on (now enforced by the validator):**
- Every change row’s ID must match the roster ID for its name, or the row links to the wrong hero (this shipped broken for Masha → Baxia; fixed).
- The header/card version must be the **bare** patch number, not the stamped `heroes.json` version, or the guide-coverage card reports every guide as stale (fixed via `shortVersion`).
- The card date must be the patch’s live date (`patchDate` 2026-11-11), not the data-refresh date (`updated` 2026-11-23) (fixed).
- Guide coverage must flag `roam_guide.json` (2.2.16a) as older while the four 2.2.23 guides show ✓ (fixed).
- `items.json.patchChanges` (10 entries, incl. the Nov 26 legacy removals) is still **not** merged into this screen — open decision C15.

### 5.8 Settings & Profile (+ Knowledge Base)

1. **Purpose:** Manage overlay permission, startup preference, help, and data health. (No profile/auth exists — “Profile” is N/A.)
2. **Layout:** `AppTopBar` (“Settings”) → Floating guide card (status + “Open Android settings” button + instructions) → Startup switch (“Start the guide when the app opens”) → Help & Knowledge Base row → Data status (expandable) → version.
3. **Hierarchy:** 1) Permission 2) Startup 3) Help 4) Diagnostics.
4. **UI improvements:** Permission card keeps single-CTA pattern (never a fake in-app switch — existing correct behavior, kept). Switch row uses M3 `Switch` with `Role.Switch` semantics (existing `toggleable`, kept). Diagnostics stay collapsed by default (progressive disclosure, kept).
5. **Interactions:** Tap button → system overlay settings (existing `ACTION_MANAGE_OVERLAY_PERMISSION`, kept). Return → `ON_RESUME` re-reads real permission (existing, kept — never optimistic). Switch → persists immediately (existing, kept).
6. **States:** Permission granted/denied cards (existing ✓/! icons → replace with M3 icons + text). Data missing → red “MISSING” chip (existing, kept).
7. **Copy:** “Permission is enabled — The floating guide is allowed to appear over other apps.” / “On the next screen, find MLBB Plus and enable ‘Display over other apps’.” (kept — plain language, no jargon).

**Knowledge Base (sub-screen):** List → reader on phones; two-pane ≥840dp (existing, kept). Prev/Next, related topics, quick-reference tables, “Common mistakes,” “Using this in MLBB Plus” tip (existing, kept). Back: reader → list → Settings (existing `BackHandler`, kept). Redesign only swaps custom rows for `AppCard`/`AppTopBar` and 48dp targets.

### 5.9 Floating Guide Overlay (critical in-match flow)

1. **Purpose:** Show the next timed step, Turtle/Lord countdown, and enemy spell cooldowns *without leaving the game*.
2. **Layout (frozen):** Compact widget (draggable, idle-fades to 0.6 alpha after 4s) → expanded guide (steps, objectives, spell tracker). Foreground notification with Stop.
3. **Hierarchy:** 1) Current step + timer 2) Objective countdown 3) Spell tracker.
4. **UI improvements (minimal, by design):** Body text migrates from monospace to sans; timers stay mono. No new colors, no decorative animation, no layout jumps (existing fixed-width/font discipline, kept). Do **not** apply dynamic color here.
5. **Interactions (frozen):** Drag, tap-to-wake, expand/collapse, spell-tap to start cooldown, Stop from notification. Touch pass-through outside widget (existing `JetOverlay`, kept).
6. **States (frozen):** Missing guide file → timer/objective/spell tracker still run (existing fallback, kept). Permission revoked → service stops (existing, kept).
7. **Copy (frozen):** Guide step text comes from `*_guide.json` — never hardcoded in UI.

### 5.10 Form & Create — N/A

There is no form, create, edit, or account flow in `overview.md` or code. Per constraints (“do not add new features”), none is designed. If saved builds/bookmarks are added later (roadmap medium-term), they must follow §4 components + §8 accessibility gates.

---

## 6. Key Flows

### 6.1 Onboarding (first launch → first guide)

1. Launch app → splash (theme background) → Home with Quick Start card visible (first run only).
2. **Step 1 — Position:** Tap Position dropdown → select (e.g. “Roam”). Guide briefing updates instantly. Selection persists (`last_role`).
3. **Step 2 — Playstyle:** Tap Playstyle dropdown → select (e.g. “Initiator”). Win/avoid, picks, loadout update. Selection persists (`last_role_type`).
4. **Step 3 — Enable:** Tap “Enable widget” → Android overlay settings opens (app-specific page). User enables “Display over other apps” → returns → status flips to “PERMISSION ON” (via `ON_RESUME`, never optimistic).
5. Tap “Launch guide” → service starts → Snackbar “Guide running — switch to your game” + haptic → user switches to MLBB.
6. Tap **Skip** at any point → card dismisses permanently; Guide setup remains at top of Home. No sign-in, no forced path.

**Feedback at every step:** dropdown selection is instant; permission status is always visible; launch confirms via Snackbar + running chip. **Error prevention:** invalid role/type combos auto-correct to the guide’s first type (existing `LaunchedEffect`, kept); permission cannot be faked by UI state.

### 6.2 Core task (find a hero/item and act on it)

1. From Home, tap **Heroes** (or Quick Action “Hero Explorer”) → Heroes list.
2. Type in search (e.g. “Gusion”) → grid filters instantly; count updates (“1 of 133 heroes”).
3. (Optional) Tap Role/Tier chips or Sort → refine. Tap **Clear** → reset all.
4. Tap hero card → Hero detail (Back bar visible; tabs Overview/Counters/Synergies).
5. Tap a core-build item → Item detail → tap a synergy hero → Hero detail (cross-links preserved).
6. Back → returns to originating tab (Heroes/Items/Home/Patch), search/filter state intact.

**Taps:** Home→Heroes (1) → search (1) → detail (1) = **3 taps** to actionable detail. **Preservation:** search/filter/sort/tab survive rotation and process death (`rememberSaveable`); detail survives via hero/item **ID** (fix U5). **Undo:** N/A (read-only reference; no destructive actions in-app). Stop-overlay is reversible by relaunching.

### 6.3 Returning user (launch in-match guide)

1. Open app → last Position/Playstyle restored; overlay status shown (“running” / “not running”).
2. (If auto-start enabled + permission granted) guide starts automatically once per session (existing logic, kept).
3. Otherwise tap **Launch guide** (1 tap) → Snackbar confirms → switch to game → widget shows current step/timer.
4. In-match: drag widget, tap to expand, tap enemy spell to start cooldown. Stop via notification action.

**Personalization (existing, kept):** last role/type, auto-start preference, tab/search/filter state. **Staged (not in this pass):** recently viewed heroes/items, bookmarks — roadmap items requiring prefs + UI; do not ship without §8/§9 gates.

---

## 7. Full Kotlin Implementation

**Principle:** additive + staged. New files compile alongside old ones; modified files keep every public signature. No JSON, manifest-permission, or service changes (except predictive-back flag). **Build not verified in this environment** (no Android SDK / JDK 17 here) — run `./gradlew :app:assembleDebug` + `python3 scripts/validate_assets.py` locally before merging (see §9).

### 7.1 File inventory

| File | Status | Purpose |
|---|---|---|
| `ui/theme/Color.kt` | **NEW** | M3 light/dark tokens, WCAG AA |
| `ui/theme/Type.kt` | **NEW** | M3 type scale (sans) |
| `ui/theme/Dimens.kt` | **NEW** | 8dp spacing, shapes, motion, breakpoints |
| `ui/components/AppComponents.kt` | **NEW** | Button, Search, Card, TopBar, Empty/Error/Loading |
| `ui/navigation/AppRoute.kt` | **NEW** | Sealed routes + tabs + deep-link spec |
| `ui/viewmodel/AppViewModel.kt` | **NEW** | StateFlow root state (no Hilt) |
| `ui/theme/MLBBPlusTheme.kt` | MODIFIED | `AppUiTheme` gains light/dynamic + `AppTypography`; overlay theme untouched |
| `ui/screens/HeroesScreen.kt` | MODIFIED | `DetailBackBar` on hero detail (bug fix); `shortVersion` for the header |
| `ui/screens/ItemsScreen.kt` | MODIFIED | `DetailBackBar` on item detail (bug fix); `shortVersion` for the header |
| `ui/model/Models.kt` | MODIFIED | Added `PatchNotesFile.shortVersion`; patch card date now prefers `heroes.json.patchDate` |
| `ui/screens/PatchNotesScreen.kt` | MODIFIED | Uses `shortVersion` in header/meta card/guide coverage → stops false “older than” warnings |
| `ui/screens/SettingsScreen.kt` | MODIFIED | `shortVersion`; staleness keyed off patch number, not self-declared `meta.latest` |
| `ui/screens/HomeScreen.kt` | MODIFIED | Uses shared `shortVersion` instead of a local `substringBefore("-")` copy |
| `ui/widgets/OverlayWidgets.kt` | MODIFIED | In-match patch label compares bare patch numbers (behaviour otherwise frozen) |
| `ui/viewmodel/AppViewModel.kt` | MODIFIED | Exposes `shortVersion` as `patchVersion` |
| `assets/patch_notes.json` | MODIFIED | **Data fix:** Masha `heroId` 87 → 88 (87 is Baxia) |
| `scripts/validate_assets.py` | **NEW** (recreated) | Asset + referential-integrity validator; was referenced by docs but missing from the repo |
| `AndroidManifest.xml` | MODIFIED | `enableOnBackInvokedCallback="true"` (predictive back) |
| `res/values/strings.xml` | MODIFIED | Shared component strings |
| `res/values/dimens.xml` | **NEW** | XML spacing tokens (mirror `AppDimens`) |
| `docs/overview.md`, `docs/structure.md`, `docs/links.md`, `docs/refactoring-report.md`, `readme.md` | MODIFIED | Synced to the refreshed assets (99 items, guide patches, tier snapshot, provenance) + correction notes |

### 7.2 Theming (copy-paste ready)

```kotlin
// ui/theme/MLBBPlusTheme.kt — AppUiTheme (overlay MLBBPlusTheme unchanged)
@Composable
fun AppUiTheme(
    // Default dark until screens migrate off AppUiColors.* (315 usages).
    // Pass darkTheme = isSystemInDarkTheme() only after migration (§4).
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context)
                          else dynamicLightColorScheme(context)
            // Brand gold pinned as primary; wallpaper tones via surfaces only.
            if (darkTheme) dynamic.copy(primary = AppColorTokens.Gold, onPrimary = Color(0xFF171717))
            else dynamic.copy(primary = AppColorTokens.GoldDeep, onPrimary = Color.White)
        }
        darkTheme -> AppDarkColorScheme
        else -> AppLightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
```

Full `Color.kt` / `Type.kt` / `Dimens.kt` are in the repo (see file inventory). Key rules: colors from `MaterialTheme.colorScheme`, type from `MaterialTheme.typography`, spacing from `AppDimens`, strings from `R.string.*` — no hardcoded literals in new code.

### 7.3 Reusable components (excerpt — full file in repo)

```kotlin
// ui/components/AppComponents.kt — excerpt
@Composable
fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier,
              enabled: Boolean = true, loading: Boolean = false,
              leadingIcon: ImageVector? = null, content: @Composable () -> Unit) {
    Button(onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = AppDimens.TouchTargetMin),
        shape = AppShapes.CardLarge) {
        if (loading) { CircularProgressIndicator(Modifier.size(AppDimens.IconSizeSmall), strokeWidth = 2.dp) }
        else if (leadingIcon != null) { Icon(leadingIcon, null, Modifier.size(AppDimens.IconSize)) }
        content()
    }
}

@Composable
fun AppSearchBar(query: String, onQueryChange: (String) -> Unit,
                 modifier: Modifier = Modifier,
                 placeholderRes: Int = R.string.search_hint) {
    OutlinedTextField(value = query, onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().heightIn(min = AppDimens.TouchTargetMin),
        placeholder = { Text(stringResource(placeholderRes)) },
        leadingIcon = { Icon(AppIcons.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
            Icon(AppIcons.Close, stringResource(R.string.action_clear_search)) } },
        singleLine = true, shape = AppShapes.Input,
        textStyle = MaterialTheme.typography.bodyMedium)
}
```

`AppCard`, `AppTopBar` (Back + title), `EmptyState`, `ErrorState`, `LoadingState` follow the same pattern (48dp, tokens, string resources). See repo file for complete implementations.

### 7.4 Navigation & state (excerpt — full files in repo)

```kotlin
// ui/navigation/AppRoute.kt — excerpt
enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", AppIcons.Home), HEROES("Heroes", AppIcons.Person),
    ITEMS("Items", AppIcons.Apps), PATCH("Patch", AppIcons.History),
    SETTINGS("Settings", AppIcons.Settings)
}
sealed interface AppRoute {
    data object Home : AppRoute
    data object Heroes : AppRoute
    data class HeroDetail(val heroId: Int) : AppRoute   // ID, not object → saveable
    data object Items : AppRoute
    data class ItemDetail(val itemId: Int) : AppRoute
    data object Patch : AppRoute
    data object Settings : AppRoute
    data object KnowledgeBase : AppRoute
}

// ui/viewmodel/AppViewModel.kt — excerpt
data class AppUiState(val isDataReady: Boolean = GameData.isLoaded, /* … */)
class AppViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    fun refreshOverlayState(granted: Boolean, running: Boolean) { /* … */ }
}
```

**Adoption steps (staged, one at a time):** (1) Replace `MainActivity`’s private `Tab` with `AppTab` (same labels/icons). (2) Store `selectedHeroId: Int?` / `selectedItemId: Int?` via `rememberSaveable`; resolve with `GameData.heroesById[id]`. (3) Hoist `AppViewModel.uiState` into `MLBBPlusApp` with `collectAsStateWithLifecycle()`. (4) Migrate screens to `AppComponents` + `MaterialTheme` tokens; only then flip `AppUiTheme(darkTheme = isSystemInDarkTheme())`. Each step is independently shippable.

### 7.5 Performance & polish

- **Recomposition:** keep `remember`/`derivedStateOf` for filtered lists (existing `remember(query, …)` pattern, kept); stable keys on all lazy items (already `key = { it.id }`, kept); hoist state (screens receive values + callbacks, no direct `GameData` reads in new composables).
- **Images:** Coil 3 with placeholders + fallback (existing `AssetImage.kt`, kept). No new image stack.
- **Edge-to-edge:** add `enableEdgeToEdge()` in `MainActivity.onCreate()` before `setContent`, use `WindowInsets` (status/nav bars) via Scaffold `contentWindowInsets`. Status/nav bar colors follow the theme (dark default matches existing `themes.xml`).
- **Startup:** `GameData.load()` stays synchronous in `onCreate()` for now (bundled JSON, offline-first). Async migration (load on `Dispatchers.IO` + `LoadingState`) is staged — it requires a loading UI contract first, otherwise first frame shows blanks. Measure with Macrobenchmark before changing.
- **Do-not-break list:** JSON schemas, hero/item IDs, guide `roles[]` IDs, overlay service/intents, `SharedPreferences` keys (`last_role`, `last_role_type`, `auto_start_last_role`), `AppUiColors`/`MLBBPlusColors` names (315+ usages). All preserved.

---

## 8. Accessibility Checklist

- [ ] All interactive elements ≥48dp (search, chips, dropdowns, tabs, back, launch, switches).
- [ ] TalkBack: every icon button has `contentDescription`; decorative icons = null; grid cards announce “{name}, Tier {tier}”; tabs announce selected state; switches announce on/off.
- [ ] Heading navigation: screen titles use `AppTopBar` (headings), not custom Rows.
- [ ] Font scale 200%: no clipped text, no fixed heights, grid labels wrap or ellipsize gracefully; minimum meaningful text 11sp.
- [ ] Color independence: tier/patch/win/lose always pair color + text label/icon; never color alone.
- [ ] Focus order: logical top→bottom; dropdowns return focus correctly; keyboard/D-pad navigation works on TV/ChromeOS.
- [ ] Contrast: body text ≥4.5:1, large text ≥3:1 in both light and dark (see §4.1 ratios).
- [ ] Reduced motion: non-essential animation skipped when animator duration = 0.
- [ ] Touch: no hover-only or long-press-only actions; all actions reachable by tap.
- [ ] Overlay: timers readable at 200% font scale; widget remains draggable with large text.

---

## 9. QA Checklist

**Build & data (run first):**
- [ ] `python3 scripts/validate_assets.py` passes — currently **pass with 1 warning** (`roam_guide.json` at 2.2.16a). It checks JSON syntax, declared totals (133 heroes / 99 items), unique IDs, all 232 portrait paths, matchup self-links/duplicates/unresolved IDs, guide pick linking, patch-change ID ↔ roster name, and cross-file patch-version agreement.
- [ ] `./gradlew :app:assembleDebug` and `:app:assembleRelease` (R8 + shrink) both succeed. **Not yet run** — this environment has JDK 11 and no Android SDK; the project needs JDK 17 + SDK 37.
- [ ] Every guide hero pick / core item resolves or is listed in Settings diagnostics (verified 2026-11-27: 0 unlinked picks, 0 unlinked core items).
- [ ] Patch screen lists **13** hero changes, each opening the correct hero (spot-check Masha → Masha, not Baxia) and 2 item changes.
- [ ] Patch header reads “Updated 2026-11-11 • Patch 2.2.23 • Season 42” — no `-tier-2026-11-23` stamp anywhere in the UI.
- [ ] Guide coverage card: Jungle/Mid/Gold/EXP show ✓ at 2.2.23; **Roam shows “⚠ older than 2.2.23”**.
- [ ] Settings → Data status: `roam_guide.json` row shows **OLDER** (orange), the other four show LATEST; `items.json` row reads 99 items.
- [ ] Overlay expanded view shows no false “(older than …)” patch label for Jungle/Mid/Gold/EXP.

**Devices & windows:**
- [ ] Small phone (360dp, e.g. 5" device): bottom nav, 5-col grid, no clipped filter/search.
- [ ] Large phone (412dp+): same, with comfortable spacing.
- [ ] Tablet (600–839dp): Navigation Rail, adaptive grid expands, content ≤960dp centered.
- [ ] Tablet/foldable (≥840dp): Knowledge Base two-pane; fold/unfold preserves state.
- [ ] Landscape + split-screen: no clipped content, back behavior correct.
- [ ] API 29 (min), API 34/35/36 (target): overlay permission, foreground service, predictive back, edge-to-edge.

**Modes & settings:**
- [ ] Dark (default) and Light (after migration): all screens readable, no dark-on-dark.
- [ ] Dynamic color on/off (API 31+): brand gold preserved, surfaces adapt.
- [ ] Font scale 100% / 150% / 200%: no overlap, no truncation of critical labels.
- [ ] TalkBack full pass: Home → Heroes → Detail → Items → Detail → Patch → Settings → Knowledge Base.
- [ ] Offline (airplane mode): full catalogue + guides work; patch version visible.
- [ ] Slow device / cold start: no ANR; loading state (staged) or fast sync load verified.
- [ ] Overlay: start, update role/type, drag, expand, spell-tap cooldown, Stop from notification, permission revoke, process recreation.
- [ ] Rotation + process death (“Don’t keep activities”): tab/search/filter/detail (by ID) restored.
- [ ] OEMs (Samsung, Xiaomi, Oppo): overlay + foreground-service behavior verified.

---

*End of redesign. Implementation files are in `repo.001/` (see §7.1). Suggested next PRs, in order: (1) back-bar fix + predictive back **(done)**, (2) data-integrity fixes: Masha ID, `shortVersion`, `patchDate`, Settings staleness guard, validator **(done — 2026-11-27)**, (3) refresh `roam_guide.json` from 2.2.16a to 2.2.23 and clear its `meta.latest` flag **(data owner — highest priority)**, (4) repair `heroes.json.fixNotes` / `items.json.notes` and decide whether `items.json.patchChanges` should reach the Patch screen, (5) `AppComponents` migration for Heroes/Items search/filter, (6) detail `AppTopBar` + ID-based saveable state, (7) Home primary-action slot + Snackbar, (8) light-mode screen migration, (9) async data load + loading states, (10) narrow `.github/workflows/extract.yml` so archive pushes stop erasing `docs/` and `scripts/`.*
