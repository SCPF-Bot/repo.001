# MLBB Plus — Documentation, Resources, and Data Sources

This index separates **official platform documentation**, **UX references**, **game-data / matchup sources**, and **project-local documentation**. A link being listed does not mean the app calls that website at runtime. The current app ships local JSON assets and does not fetch these sources while running.

## 1. Android platform and app architecture

| Resource | Why it matters to this project |
|---|---|
| [Guide to app architecture](https://developer.android.com/topic/architecture) | Separation of concerns, UI/data layers, state holders, lifecycle, and adaptive app structure. |
| [Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations) | Practical guidance for layered architecture, repositories, unidirectional data flow, ViewModels, and lifecycle-aware UI state. |
| [UI layer](https://developer.android.com/topic/architecture/ui-layer) | How UI state is exposed and consumed by Compose screens. |
| [Compose UI architecture](https://developer.android.com/develop/ui/compose/architecture) | State/events and unidirectional data flow principles used by composables. |
| [State in Jetpack Compose](https://developer.android.com/develop/ui/compose/state) | `remember`, `rememberSaveable`, observable state, and state hoisting. |
| [Where to hoist state](https://developer.android.com/develop/ui/compose/state-hoisting) | Guidance for keeping state at the lowest common owner and separating UI logic from business logic. |
| [Build adaptive apps](https://developer.android.com/develop/ui/compose/layouts/adaptive) | Responsive patterns for phones, tablets, foldables, and resizable windows. |
| [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | Material 3 components and theming for Android UI. |
| [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility) | Semantics, accessibility services, and inclusive Compose interaction. |
| [Compose accessibility semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) | Descriptions, roles, state, and semantics for interactive controls. |
| [Android app accessibility](https://developer.android.com/guide/topics/ui/accessibility/apps) | Touch target sizing, content descriptions, and app accessibility practices. |
| [Android app quality guidelines](https://developer.android.com/docs/quality-guidelines/core-app-quality) | Compatibility, stability, performance, and user-experience quality checks. |

### Overlay and service APIs

| Resource | Why it matters |
|---|---|
| [Manifest.permission reference — SYSTEM_ALERT_WINDOW](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW) | Defines the special permission used to draw application-overlay windows. |
| [Settings API reference](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION) | Documents the system screen for managing overlay permission. |
| [Services overview](https://developer.android.com/develop/background-work/services) | Service lifecycle and foreground-service behavior. |
| [Foreground services overview](https://developer.android.com/develop/background-work/services/fgs) | User-visible work, notification requirements, start/stop constraints, and policy considerations. |
| [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) | Requirements and declarations for foreground service types, including `specialUse`. |
| [Android notifications](https://developer.android.com/develop/ui/views/notifications) | Notification channels, actions, and user-visible service status. |

## 2. UX and interaction design references

| Resource | Project application |
|---|---|
| [Nielsen Norman Group — 10 usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/) | System status visibility, match to real-world language, user control, consistency, error prevention, recognition over recall, and minimalist design. |
| [Nielsen Norman Group — Recognition rather than recall](https://www.nngroup.com/articles/recognition-and-recall/) | Keep options, labels, and current selections visible so users do not need to remember hidden steps. |
| [Material Design 3](https://m3.material.io/) | Design tokens, component behavior, hierarchy, and interaction patterns. |
| [Material Design — Accessibility](https://m3.material.io/foundations/accessible-design/overview) | Contrast, readable typography, accessible controls, and inclusive interaction. |

These are design references, not a claim that every recommendation has already been implemented. See [refactoring-report.md](refactoring-report.md) for the file-by-file review and implemented UX changes.

## 3. MLBB game-data and matchup references

### Primary matchup dataset recorded in the current hero asset

| Source | URL | Recorded use |
|---|---|---|
| MLBB Meta Analyser project | [Project / methodology](https://github.com/lnsdeep/MLBB-Meta-Analyser) | Primary source for favorable-matchup and same-team synergy lists. The project describes its data as Moonton GMS-derived ranked data and daily refreshed. It is an independent third-party project, not a Moonton-operated service. |
| MLBB Meta Analyser CSV export | [Latest CSV export](https://github.com/lnsdeep/MLBB-Meta-Analyser/blob/main/data/exports/mlbb_meta_latest.csv) | The all-ranks / past-1-day snapshot used for the 21 November 2026 matchup update. The export contains rank/timeframe slices; the project selected the `all` rank and `1d` timeframe. |
| MLBB Meta Analyser hosted page | [Hosted analyser](https://lnsdeep.github.io/MLBB-Meta-Analyser/) | Project overview and access to published analysis. |

### Direct counters and secondary cross-checks

| Source | URL | Recorded use / caveat |
|---|---|---|
| MLBBHub | [Counter picker](https://mlbbhub.com/counter) | Direct ranked-counter fallback for 43 heroes where the reverse-indexed primary source did not provide sufficient toughest-matchup coverage; also used to cross-check favorable-matchup direction. |
| MLBB.gg | [Atlas counter page](https://mlbb.gg/counter/atlas) | Hero-specific counter reference supplied by the project owner; recheck page freshness before importing any values. |
| MLBBMeta | [Counters](https://mlbbmeta.com/counters/) | Additional matchup reference supplied for comparison; not the primary source for the current full-roster snapshot. |
| MLCounters | [Aamon matchup page](https://www.mlcounters.app/hero/aamon#counter-panels) | Secondary hero-specific counter / synergy reference supplied for comparison; not the primary source for the current full-roster snapshot. |
| MLBB Guides | [Counter page example](https://www.mlbbguides.com/counter/?hero=hirara) | Additional hero-counter guide reference supplied by the project owner. Confirm hero slug and page freshness before relying on an individual result. |

### Interpretation rules for matchup fields

The current `heroes.json` stores relationship IDs in four fields:

- `counters`: favorable opponents for the selected hero (the selected hero tends to perform well against these opponents).
- `counteredBy`: toughest opponents for the selected hero.
- `synergies` and `bestPartners`: measured same-team ally relationships. In the current data snapshot, these two fields are populated from the same ally list for compatibility with the existing model/UI.

The current snapshot method and interpretation limits are described in this section and in [overview.md](overview.md):

1. `counters` uses the primary dataset's ranked counter picks.
2. `synergies` / `bestPartners` use its ranked teammate synergy picks.
3. `counteredBy` is derived by reversing favorable-counter edges, scoring appearances by source rank (5 down to 1), then selecting the strongest reverse edges.
4. For 43 heroes with fewer than three reverse edges, direct ranked MLBBHub counter picks are used as a fallback.
5. Unresolved placeholder IDs / names are excluded rather than guessed.

**Important:** these are observational win-rate associations, not causal proof or guaranteed draft advice. Rank, patch, sample size, lane, player skill, bans, and team composition can change the result. The 2026-11-21 asset snapshot is specifically all ranks / past one day / patch 2.2.23; it will age and should be refreshed and revalidated for later patches.

### Other game references

- [Mobile Legends: Bang Bang official website](https://www.mobilelegends.com/) — official game and publisher reference.
- [Google Play — Mobile Legends: Bang Bang](https://play.google.com/store/apps/details?id=com.mobile.legends) — public app listing.

## 4. Libraries and frameworks used by the code

| Dependency | Official resource | Use in project |
|---|---|---|
| Kotlin | [Kotlin documentation](https://kotlinlang.org/docs/home.html) | Application language and data/model code. |
| Android Gradle Plugin | [Android Gradle Plugin release notes](https://developer.android.com/build/releases/gradle-plugin) | Android build configuration. |
| Jetpack Compose | [Compose documentation](https://developer.android.com/develop/ui/compose/documentation) | Declarative app and overlay UI. |
| Material 3 | [Material 3 Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) | Main app components and theming. |
| AndroidX Activity | [Activity release notes](https://developer.android.com/jetpack/androidx/releases/activity) | Compose activity integration and Back handling. |
| AndroidX Lifecycle | [Lifecycle release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle) | Lifecycle observer and Compose lifecycle integration. |
| Coil 3 | [Coil documentation](https://coil-kt.github.io/coil/) | Compose image loading / bundled asset image rendering. |
| Gradle version catalog | [Gradle version catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html) | Centralized dependency/plugin versions in `gradle/libs.versions.toml`. |

Exact dependency versions are recorded in `gradle/libs.versions.toml`; see [structure.md](structure.md#7-build-platform-and-dependencies) for the snapshot's version table.

## 5. Project-local documentation

- [overview.md](overview.md) — mission, vision, current capabilities, technology stack, and roadmap.
- [structure.md](structure.md) — file-by-file map, dependency/data flow, service lifecycle, and change-impact guide.
- [refactoring-report.md](refactoring-report.md) — exhaustive Kotlin review, per-file recommendations, UI changes, and validation notes (see its 27 November 2026 addendum for the data-refresh sync).
- [ux-redesign.md](ux-redesign.md) — Material 3 UX audit, information architecture, design system, screen-by-screen redesign, key flows, accessibility and QA checklists.
- `../scripts/validate_assets.py` — the asset validator referenced throughout these docs.

## 6. Maintenance rules for this link index

- Prefer official Android / Kotlin / library documentation for platform and API behavior.
- Record the exact source URL, snapshot date, patch, rank scope, and timeframe whenever game statistics are imported.
- Keep primary data sources distinct from secondary cross-checks and editorial guides.
- Revisit third-party pages before each patch refresh; page structure and statistical coverage can change.
- Do not imply that a listed website is called by the app at runtime unless a network client is actually implemented.


## 7. Current matchup snapshot: provenance, methodology, and audit

### Snapshot identity

- Asset: `app/src/main/assets/heroes.json`
- Snapshot date: **2026-11-21**
- Game patch: **2.2.23**
- Scope: **all ranks / past 1 day**
- Primary source: MLBB Meta Analyser CSV export
- Secondary direct-counter fallback: MLBBHub for 43 heroes

The primary export contains 3,990 rows across hero, rank, and timeframe slices, with up to five counter and five teammate-synergy entries per hero. The MLBB Meta Analyser project describes its statistics as extracted from Moonton's Game Management System and refreshed daily. It is an independent third-party project, not a Moonton-operated service.

### Field definitions and derivation

- `counters`: favorable opponents for the selected hero; the selected hero tends to perform well against these opponents. Up to five entries.
- `bestPartners` / `synergies`: top measured same-team ally relationships. In this snapshot, the fields carry the same ally list for compatibility with the existing model/UI.
- `counteredBy`: toughest opponents for the selected hero. The primary method reverses favorable-counter edges: if hero A appears in hero B's favorable list, B becomes a tough matchup for A. Reverse edges are weighted by source rank (5 points for rank 1 down to 1 point for rank 5), then the strongest edges are retained, up to five.
- **Fallback:** when a hero has fewer than three reverse edges, the top three direct ranked counter picks from that hero's MLBBHub page are used. The recorded fallback pages were updated between November 17 and November 20, 2026. Per-hero provenance is recorded in `counteredBySource` / `counteredByUpdated`.

Unresolved placeholder IDs or names (for example, `Hero #133`) are excluded rather than guessed. The 43 fallback heroes are Angela, Arlott, Aulus, Aurora, Badang, Brody, Carmilla, Cecilion, Chou, Claude, Clint, Dyrroth, Guinevere, Gusion, Hayabusa, Hilda, Hirara, Julian, Kadita, Kagura, Lapu-Lapu, Leomord, Lesley, Luo Yi, Martis, Minotaur, Miya, Moskov, Nana, Natan, Nolan, Novaria, Obsidia, Paquito, Pharsa, Selena, Sora, Suyou, Terizla, Vexana, Yi Sun-shin, Yu Zhong, and Zetian.

### Audit results

The machine-readable audit reports:
| Check | Result |
|---|---:|
| Heroes in dataset | 133 |
| Heroes with favorable matchups | 133 |
| Heroes with toughest matchups | 133 |
| Heroes with at least 3 toughest matchups | 133 |
| Heroes with best measured allies | 133 |
| Toughest matchups per hero | 3–5 |
| Favorable matchups per hero | 4–5 |
| Best measured allies per hero | 4–5 |
| Heroes using MLBBHub counter fallback | 43 |

**Re-verified 2026-11-27** against the refreshed `heroes.json` (`version 2.2.23-tier-2026-11-23`) with `scripts/validate_assets.py`: 133 heroes, every favorable/toughest/ally list populated, toughest 3–5 and favorable/ally 4–5 entries per hero, no self-links, no duplicate IDs, and every relationship ID resolving to a roster hero. The `matchupData`, `matchupDataset`, and per-hero `matchupData*` provenance fields in the asset match the values recorded above (source, 2026-11-21, all ranks, past 1 day, 43-hero MLBBHub fallback).

### Tier snapshot (added 2026-11-23)

Recorded in `heroes.json.tierSnapshot` and mirrored per hero in `tier` / `tierSource` / `tierUpdated`:

- Source: **MLBBDex** tier list (`https://mlbbdex.com/en/tier-list`), patch 2.2.23, updated **2026-11-23**, scope all ranks / all lanes.
- Method: win rate + 0.25 × ban rate, recomputed from the published tier snapshot.
- Distribution: **S+ 18, S 16, A 32, B 46, C 21** (133 total).
- Rule: `isMeta` is true exactly when tier is **S+** — the Home "meta heroes" and Patch "meta heroes by lane" cards derive from this flag, not from a hardcoded list.
- Tier definitions ship with the data (S+ = current-patch meta / high-priority bans; C = needs favorable drafts or buffs), so the UI can explain a tier instead of implying a guarantee.

Tier lists are editorial aggregations of win/ban rates and move faster than matchup data; treat them as draft guidance for the stated patch and refresh them with each patch.

### Item catalogue and build-tree provenance (updated 2026-11-26)

- Source: **Fandom Equipment wiki** (`https://mobile-legends.fandom.com/wiki/Equipment`), recorded in `items.json.source`; per-item remote artwork URLs are in `portrait` / `fandomFile`.
- Catalogue: **99** active items (Attack 31, Magic 25, Defense 25, Movement 8, Roam 6, Jungle 3, Attack & Magic 1); all ship a bundled `items/{id}.webp` portrait.
- Build tree: `items.json.buildTreeMetadata` — patch 2.2.23, updated **2026-11-23**; `buildsInto` is the reverse of `buildPathIds`, and component links resolve by stable item ID.
- History recorded in `items.json.patchChanges`: 66 items added 2026-11-21 (Tier 1/2 components plus missing Tier 3 items), legacy/duplicate entries removed 2026-11-26 (Courage Mask 4003 → Blessing: Encourage, shop Favor 4004 → Blessing: Favor, Athena's Shield 3004 → Valkyrie's Shield 3015, Twilight Armor 3007 → Chastise Pauldron). The original 41 records kept their IDs, stats and build paths so `heroes.json` core-build references still resolve — verified: **0 unlinked core items** across all 133 heroes.
- The Equipment page blocked direct access during collection; names were cross-checked against its search-indexed mirror, so the removal list in `items.json.notes` may not be exhaustive.

### Interpretation and maintenance caveats

These are observational win-rate associations, not causal proof or guaranteed draft advice. The snapshot is all-ranks / past-one-day, not a rank-specific recommendation. Patch, rank, sample size, lane, player skill, bans, and team composition can change outcomes. The reverse-index method is an aggregation of source-ranked recommendations, not a separately published per-pair score.

The snapshot is time-sensitive and should be refreshed and revalidated for later patches. Record source URL, snapshot date, patch, rank scope, timeframe, and fallback provenance whenever data is imported. Keep primary statistical sources distinct from secondary cross-checks and editorial guides.

### Historical update note

The earlier 2026-11-21 partial cleanup populated sourced relationships for Hayabusa, Saber, Lancelot, Hanabi, and Eudora while leaving other heroes empty rather than inventing values. That state was superseded later the same day by the complete 133-hero snapshot described above. The partial update is retained here as history only; it is not the current data state.

## 8. UX / accessibility design references

The recorded app-screen refresh used these references:
- [Android adaptive layouts](https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout)
- [Android accessibility for Compose](https://developer.android.com/guide/topics/ui/accessibility/composables)
- [Compose accessibility semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Android minimum touch targets](https://developer.android.com/guide/topics/ui/accessibility/apps)
- [Nielsen Norman Group usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/)

These are design references, not evidence that every recommendation is fully implemented. The implementation record is summarized in `overview.md` and mapped to code in `structure.md`.

## 9. Canonical documentation map

The current documentation set is:
- `overview.md` — product mission, capabilities, snapshot, and roadmap.
- `structure.md` — repository map, runtime/data flow, file responsibilities, and change-impact guide.
- `links.md` — external platform/design/game-data references, matchup methodology, and maintenance caveats.
- `refactoring-report.md` — Kotlin file-by-file review, applied refactors, remaining recommendations, and validation record.


## 10. Additional references used by the refactor

| Resource | Why it matters to this repository |
|---|---|
| [Compose lazy lists and grids](https://developer.android.com/develop/ui/compose/lists) | Lazy list/grid behavior, stable keys, and adaptive item layouts for the hero and item catalogues. |
| [Compose adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive) | Responsive layouts across compact phones, landscape, tablets, and resizable windows. |
| [Compose side effects](https://developer.android.com/develop/ui/compose/side-effects) | Correct cancellation and lifecycle of timer loops and effects in the floating guide. |
| [Compose in Views interoperability](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views) | `ComposeView` hosting inside the `WindowManager` overlay. |
| [Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore) | A future replacement for direct `SharedPreferences` access if preferences become more complex. |
| [Android AssetManager](https://developer.android.com/reference/android/content/res/AssetManager) | Loading bundled JSON and image resources from `assets/`. |
| [Kotlin null safety](https://kotlinlang.org/docs/null-safety.html) | Defensive parsing and explicit handling of optional fields in the bundled JSON models. |
| [Kotlin collections](https://kotlinlang.org/docs/collections-overview.html) | Building and caching indexes for hero, item, and guide cross-references. |

The project keeps Coil 3 for image loading and Compose animation APIs for small state transitions. Lottie was evaluated as an optional enhancement but not added: the overlay is a tactical in-game tool, so decorative motion would add dependency and visual noise without improving its primary task.
