# MLBB Plus — Product Overview

> **One companion for every lane. Practical guidance, available when the match is happening.**

**Project snapshot:** 21 November 2026  
**Bundled game-data patch:** 2.2.23 · Original Server · Season 42  
**Android application ID:** `com.mlbb.plus`

## 1. What MLBB Plus is

MLBB Plus is a fan-made Android companion for Mobile Legends: Bang Bang. It combines an offline reference library with a floating in-game guide overlay. The main app helps players browse heroes, builds, items, matchups, patch changes, and lane-specific plans. The overlay keeps time-sensitive guide steps, objective timers, and enemy spell tracking accessible while the player is in another app or game.

The current build includes five role guide datasets: Roam, Jungle, Mid, Gold, and EXP. The overlay is implemented as a shared role-aware guide surface rather than five separate overlay services.

The project is not affiliated with or endorsed by Moonton. Mobile Legends: Bang Bang and its assets / game data remain the property of their respective rights holders.

## 2. Mission

Help MLBB players make clearer, faster in-match decisions by presenting useful, understandable guidance without forcing them to leave the game to consult long-form guides.

The product aims to:

- **Reduce friction:** surface the relevant guide, hero, item, and patch context in a few taps.
- **Support every lane:** provide practical role and playstyle plans for Roam, Jungle, Mid, Gold, and EXP.
- **Make information actionable:** prioritize rotations, objectives, timings, builds, counters, and team composition context over unstructured walls of text.
- **Work offline for core reference:** bundle the current hero, item, patch, and guide catalogues in the app.
- **Explain uncertainty:** distinguish measured statistical trends from guaranteed outcomes, especially for counters and teammate synergy.
- **Stay lightweight:** use a single Android app module, Compose UI, bundled JSON, and a focused set of dependencies.

## 3. Vision

Become a reliable, easy-to-use companion for the full MLBB match lifecycle—from draft and hero selection through laning, rotations, objectives, team fights, and post-patch adaptation.

A player should be able to:

1. Choose a lane and playstyle.
2. Review a concise plan and hero/item recommendations.
3. Launch the overlay when it is useful.
4. Read the next actionable step and objective timing without losing game context.
5. Explore a hero, matchup, item, or patch change when they want deeper detail.

Long-term, MLBB Plus should make its information easier to maintain and trust through transparent sources, patch-aware data, robust validation, and a safe update mechanism.

## 4. Who it serves

- **Newer / less technical players:** clear labels, quick-start guidance, explanations of why a recommendation matters, and predictable navigation.
- **Experienced players:** searchable catalogues, filters, sorting, compact statistics, hero-specific matchups, item relationships, and concise overlay controls.
- **Players learning a new lane:** structured role types, strengths/weaknesses, win conditions, lose conditions, core items, spells, and timed steps.

The app should avoid assuming that every user understands game abbreviations or Android overlay permissions. Advanced details can remain available without making the first-use path intimidating.

## 5. Current product capabilities

### Main app

| Area | Current capability |
|---|---|
| Home | Role and guide-type selection, guide briefing, quick-start guidance, launch state, patch banner, meta/mover content, quick navigation |
| Heroes | Search, filters, sorting, hero cards, hero detail, skills/build/profile data, hero relationships and guide references |
| Items | Search, filters, sorting, item details, stats/passives/actives, build relationships, heroes using an item |
| Patch | Original Server patch summary, hero/item changes, equipment/battlefield/system notes, links to affected entities |
| Settings | Overlay permission flow, auto-start-last-role preference, bundled-data diagnostics |

### Floating guide overlay

- Role-aware guide plans sourced from the five `*_guide.json` assets.
- Guide type / playstyle selection.
- Timed instruction steps and phase / callout content.
- Objective timing support for Turtle and Lord.
- Battle-spell defaults and enemy spell cooldown tracking.
- Compact / expanded presentation and touch interaction state.
- Foreground notification with an explicit Stop action.

### Bundled data snapshot

| Dataset | Snapshot contents |
|---|---|
| `heroes.json` | 133 hero records, patch metadata, matchup provenance, hero patch changes, equipment changes |
| `items.json` | 41 item records and item patch metadata |
| `patch_notes.json` | Patch 2.2.23 Original Server notes and change lists |
| Five guide JSON files | 26 role/playstyle types total (6 Roam, 5 each Jungle/Mid/Gold/EXP) |
| Hero portraits | Bundled WebP portraits under `assets/heroes/` |
| Item portraits | 41 bundled WebP portraits under `assets/items/` (40 active, one removed legacy record; original illustrative artwork, not official game art) |

The matchup snapshot is dated 2026-11-21, all ranks, past one day. It contains three relationship concepts: toughest matchups, favorable matchups, and best measured allies. The derivation and fallback approach are documented in [links.md](links.md#3-mlbb-game-data-and-matchup-references). These are statistical associations, not deterministic advice.

## 6. Product principles

1. **Useful before clever:** the next action should be obvious; avoid decorative complexity that competes with gameplay information.
2. **Progressive disclosure:** beginners see plain-language explanations first; technical/statistical detail remains accessible on demand.
3. **Trust through provenance:** show source, update date, rank scope, and limitations for volatile data.
4. **Offline-first reference:** core catalogue and guide reading should remain available without a network connection.
5. **One source of truth per dataset:** hero/item IDs and shared metadata should resolve consistently across screens and guide references.
6. **Protect the game experience:** overlay behavior should remain predictable, dismissible, readable, and respectful of Android permission controls.
7. **Patch-aware, not patch-assumptive:** stale data must be identifiable; do not imply that an old build reflects the current live patch.

## 7. Technology stack

| Layer | Current implementation |
|---|---|
| Language | Kotlin 2.3.21 |
| Android build | Android Gradle Plugin 9.2.1; Gradle wrapper 9.6.1 |
| Android SDK | compile 37, target 36, min 29 |
| Runtime | Single Activity + Android foreground Service |
| UI | Jetpack Compose, Material 3, Compose BOM 2026.06.00 |
| Lifecycle / Activity | AndroidX Lifecycle 2.11.0; Activity Compose 1.13.0 |
| Images | Coil Compose 3.0.4; bundled WebP portraits |
| Data format | Local JSON assets parsed with `org.json` into Kotlin data classes |
| Preferences | `SharedPreferences` for last role, last guide type, and auto-start setting |
| Overlay | `WindowManager` / `TYPE_APPLICATION_OVERLAY` host with ComposeView (`JetOverlay`) |
| Service | Android foreground service declared as `specialUse` for the overlay use case |
| Release optimization | R8 minification and resource shrinking enabled for release |

There is currently no remote backend, authentication, analytics SDK, database, dependency-injection framework, repository layer, or automated test suite in this repository snapshot. Do not document these as existing features until they are implemented.

## 8. Roadmap

Roadmap status is based on code and bundled assets in this repository snapshot, not on an external project board.

### Current baseline — Catalogue + five-role guide (implemented)

- [x] Single Android app module and launcher experience.
- [x] Compose main app with Home, Heroes, Items, Patch, and Settings sections.
- [x] Hero and item catalogue with detail screens and cross-links.
- [x] Five role guide assets: Roam, Jungle, Mid, Gold, and EXP.
- [x] Shared role-aware floating overlay and foreground notification / Stop action.
- [x] Objective timers and enemy battle-spell tracking in overlay.
- [x] Patch-note integration and guide references on hero/item details.
- [x] Matchup and synergy fields for all 133 heroes, with methodology and source notes.
- [x] UX refinements for quick start, search/filter/sort, navigation, and settings diagnostics.nostics.

### Next priorities — reliability and maintainability

- [ ] Establish a reproducible clean debug and release build in CI; record SDK / JDK / Gradle requirements.
- [ ] Add automated JSON schema and referential-integrity tests for heroes, items, guides, patch notes, and image paths.
- [ ] Add Kotlin unit tests for JSON parsing, guide linking, matchup resolution, and patch merging.
- [ ] Consolidate the duplicated guide JSON parsing logic so screen summaries and overlay execution cannot silently diverge.
- [ ] Add a visible data freshness / source details screen and a consistent stale-data warning.
- [ ] Audit all guide steps, objective timings, and role recommendations against the current live patch before publishing a new data snapshot.
- [ ] Verify accessibility, large text, landscape, and tablet/foldable layouts on real devices or emulators.
- [ ] Test overlay permission, foreground-service start/stop, notification behavior, and OEM-specific restrictions across supported Android versions.

### Medium-term roadmap — content and user control

- [ ] Add a versioned data-update pipeline with integrity checks, rollback, and offline fallback; keep the bundled assets as the safe baseline.
- [ ] Add rank / server / patch scope filters to matchup and meta statistics when reliable source data supports them.
- [ ] Explain matchup direction, sample limitations, and recommendation confidence in the UI.
- [ ] Add per-hero role/loadout recommendations and draft planning that accounts for the full team composition.
- [ ] Add bookmarks / favorites and recently viewed heroes or items.
- [ ] Add overlay customization for size, opacity, position, and compactness, with sensible defaults.
- [ ] Add optional guide-language support and clearer glossary explanations for game terms.

### Longer-term possibilities — only after data quality and reliability are established

- [ ] User-configurable guide profiles and saved builds.
- [ ] Patch-diff history and notifications when tracked heroes/items change.
- [ ] More advanced draft assistant and team-composition analysis.
- [ ] Local analytics / training mode to help users review their own decisions without collecting sensitive account data.
- [ ] Optional community guide contributions with review, provenance, versioning, and moderation.

These are proposals, not commitments or implemented functionality. Prioritize correctness, accessibility, and reliability before adding more recommendation surfaces.

## 9. Quality gates for future releases

A release should not be considered ready until:

- The app assembles in both debug and release configurations.
- JSON parsing tests pass and every referenced hero/item ID resolves.
- Every guide hero pick and core item either resolves or is explicitly documented as unlinked.
- Matchup arrays contain no self-links or duplicate IDs, and their direction is tested.
- Asset paths resolve or show a designed fallback.
- The overlay starts, updates, hides, and stops correctly; notification and permission flows work.
- Screens remain usable with TalkBack, enlarged text, narrow phones, landscape, and larger windows.
- Data source, snapshot date, patch, rank scope, and known limitations are documented.

## 10. Where to go next

- Read [structure.md](structure.md) for the detailed file map, dependency chains, and data flow.
- Read [links.md](links.md) for Android documentation, UX references, game-data sources, and matchup resources.
- Read [links.md](links.md) for the matchup methodology and external source index.
- Read [refactoring-report.md](refactoring-report.md) for the exhaustive Kotlin review and applied refactors.


## 11. UX / UI implementation record

The current app-screen refresh improves first-use clarity and navigation while intentionally preserving the floating overlay implementation.

Implemented refinements:
- Home includes a plain-language Quick Start flow for choosing a position, selecting a playstyle, and enabling the floating guide.
- Setup terminology uses clearer labels such as **Position**, **Playstyle**, **Enable Widget**, and **Display over other apps**.
- Overlay permission is requested through a direct Android Settings action with an explanation, rather than a misleading in-app permission switch.
- The startup preference remembers the last selected position and playstyle and can start the guide on app launch when overlay permission is already granted.
- Back handling, section navigation, and detail-page return behavior were improved.
- Hero and item explorers include sorting, clear-filter actions, result counts, and useful empty states; search/filter/sort state and the selected patch tab are preserved during navigation.
- Wider windows use a responsive navigation rail; phones retain bottom navigation. Home content width is constrained on larger screens.
- Technical data-file diagnostics are grouped in an expandable troubleshooting section.
- Compose toggle semantics and practical minimum touch sizes support accessibility.

The refresh did not change the overlay implementation files: `WidgetOverlayService.kt`, `JetOverlay.kt`, `OverlayWidgets.kt`, and `MLBBPlusTheme.kt`.

## 12. Matchup snapshot audit summary

The 2026-11-21 matchup audit reports:
- 133 heroes total; all 133 have favorable-matchup entries, toughest-matchup entries, and best-measured-ally entries.
- All 133 have at least three toughest matchups.
- Toughest-matchup lists contain 3–5 entries; favorable-matchup and best-measured-ally lists contain 4–5 entries.
- Direct MLBBHub counter fallback was used for 43 heroes.

The fallback roster is: Angela, Arlott, Aulus, Aurora, Badang, Brody, Carmilla, Cecilion, Chou, Claude, Clint, Dyrroth, Guinevere, Gusion, Hayabusa, Hilda, Hirara, Julian, Kadita, Kagura, Lapu-Lapu, Leomord, Lesley, Luo Yi, Martis, Minotaur, Miya, Moskov, Nana, Natan, Nolan, Novaria, Obsidia, Paquito, Pharsa, Selena, Sora, Suyou, Terizla, Vexana, Yi Sun-shin, Yu Zhong, and Zetian.

The audit is a coverage/shape check, not proof that every recommendation is strategically correct. Interpret the relationship fields using the methodology summarized in the Sources section of `links.md`; matchup associations are not guarantees.


## 10. Refactoring snapshot (21 November 2026)

The latest repository refactor fixes model/UI mismatches in the item and hero screens, restores the shared `AssetImage` API expected by catalogue screens, adds item status handling, switches hero/item catalogues to adaptive grids, makes catalogue filters and sorting saveable, and validates overlay role/playstyle defaults. All item JSON image paths now resolve to local WebP files. See [refactoring-report.md](refactoring-report.md) for the complete file-by-file review and build-validation caveat.
