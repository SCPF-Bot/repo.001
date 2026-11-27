# MLBB Plus — Product Overview

> **One companion for every lane. Practical guidance, available when the match is happening.**

**Project snapshot:** 27 November 2026  
**Bundled game-data patch:** 2.2.23 · Original Server · Season 42 (patch live 11 November 2026)  
**Data snapshot dates:** heroes 2026-11-23 · items 2026-11-26 · patch notes 2026-11-11 · matchups 2026-11-21 · tiers 2026-11-23 · guides 2026-11-20 (Roam: 2026-11-18, still pinned to 2.2.16a)  
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
| Home | Role and guide-type selection, guide briefing (win/avoid conditions, picks, loadout), launch state, and a collapsible lane-mechanics card. Deliberately minimal since 2026-11-27: the CURRENT META patch banner, QUICK START card, patch movers, quick-action shortcuts and top-meta-hero row were removed so the screen is position → playstyle → launch. Patch and meta content live on the Patch tab and in Hero Explorer; quick-start guidance moved to Settings → Knowledge Base. |
| Heroes | Search, filters, sorting, hero cards, hero detail, skills/build/profile data, hero relationships and guide references |
| Items | Search, filters, sorting, item details, stats/passives/actives, build relationships, heroes using an item |
| Patch | Original Server patch summary, hero/item changes, equipment/battlefield/system notes, links to affected entities |
| Settings | Overlay permission flow, auto-start-last-role preference, Help & Knowledge Base reader, bundled-data diagnostics, and a **Voice Guide** section (engine, voice, speed, auto-speak, announce enemy spells, model install with real size/progress/cancel, test line) |
| Knowledge Base | In-app help library (`KnowledgeBase.kt`): **19 topics**, list + reader with prev/next, related topics, quick-reference tables, common mistakes, and "using this in MLBB Plus" tips. Two-pane on windows ≥840dp, list → reader on phones. The reader opens on the first entry, which is now **Quick Start: Set Up Your Floating Guide** (moved here from Home on 2026-11-27); the second is **Voice Guide: Hear Your Next Play**, added with the spoken coach. |

### Floating guide overlay

- Role-aware guide plans sourced from the five `*_guide.json` assets.
- Guide type / playstyle selection.
- Timed instruction steps and phase / callout content.
- Objective timing support for Turtle and Lord.
- Battle-spell defaults and enemy spell cooldown tracking.
- Compact / expanded presentation and touch interaction state.
- **Orientation modes (added 2026-11-27).** Portrait shows only a round bubble, which can be dragged. Landscape opens the bubble into the full guide with a smooth transition (tap the bubble; the ▴ control in the header folds it back). Rotating to portrait collapses the guide to the bubble again. The card keeps its clock and objective state while folded. In landscape the card is 70% of the screen width, and one header card holds the timer, role selector, close controls, and the five-cell spell tracker. Each orientation's screen size is recorded automatically on launch and on rotation (`ScreenProfiles.kt`), and each orientation keeps its own bubble position as a fraction of the screen.
- Foreground notification with an explicit Stop action.
- **Voice Guide — spoken coach (added 2026-11-27, default OFF).** Reads the step the player is on
  in condensed spoken English and can auto-speak each new step as the match timer advances; overlay
  controls are Speak/Pause/Resume · Repeat · Prev · Next · Stop, and the line being read aloud is
  tinted gold. With the feature off the overlay composes exactly what it did before.
- **On-device voices, fully offline.** Kokoro (most natural) or the smaller/faster Piper
  voices through **sherpa-onnx 1.13.8**, with Android's own TTS as the last fallback. No cloud TTS
  is used: the selected voice pack is downloaded once, when Voice Guide is turned on, from the
  upstream release (checksum-verified) into `Android/data/<app>/files/tts/`, and every utterance is
  synthesised on the device. Game audio is ducked
  rather than muted, TalkBack pauses the coach, and the notification gains Play/Pause and Stop voice
  actions. Engine choice, model URLs and checksums, the text-normalisation contract and the device
  test checklist are in [tts_implementation.md](tts_implementation.md).

### Bundled data snapshot

Verified against the bundled assets on 2026-11-27 (`python3 scripts/validate_assets.py`).

| Dataset | Snapshot contents |
|---|---|
| `heroes.json` | **133** hero records (`version 2.2.23-tier-2026-11-23`, `updated 2026-11-23`, `patchDate 2026-11-11`), tier snapshot, matchup provenance, **13** hero patch changes, **2** equipment changes. Newest roster entries: Obsidia (130), Sora (131), Marcel (132), Hirara (133). Dori (Advance Server 2.2.30) is deliberately excluded — Original Server 2.2.23 only. |
| `items.json` | **99** item records, **all active** (`totalItems=99`, `totalActiveItems=99`; `version 2.2.23-buildtree-2026-11-23`, `updated 2026-11-26`, build tree 2026-11-23) plus **10** item patch notes. Categories: Attack 31, Magic 25, Defense 25, Movement 8, Roam 6, Jungle 3, Attack & Magic 1. Grew from the original 41 records: 66 items added 2026-11-21 (Tier 1/2 components + missing Tier 3), then legacy/duplicate entries removed 2026-11-26 (Courage Mask 4003, shop Favor 4004, Athena's Shield 3004 → Valkyrie's Shield 3015, Twilight Armor 3007). |
| `patch_notes.json` | Patch 2.2.23 Original Server, dated **2026-11-11**, Season 42: **10** hero changes, **2** item changes, 3 battlefield, 2 system, 4 highlights, meta notes, Roam guide link. `previousPatch` records 2.2.16a (14 Oct 2026) as archived — only the latest patch is kept in the file. No `originalServer` / `advancedServer` / `nov01` blocks, so the Patch screen shows the Current tab only. |
| Five guide JSON files | **26** role/playstyle types, **286** timed steps (6 Roam types / 66 steps; 5 each for Jungle, Mid, Gold, EXP / 55 steps each). Jungle, Mid, Gold and EXP are written for **2.2.23** (updated 2026-11-20). **`roam_guide.json` is still pinned to 2.2.16a** (updated 2026-11-18) — see the data-quality note below. |
| Hero portraits | **133** bundled WebP portraits under `assets/heroes/`, one per hero record; all declared paths resolve. |
| Item portraits | **99** bundled WebP portraits under `assets/items/` (`items/{id}.webp`), one per active item; all declared paths resolve. Each record also carries a remote `portrait` URL (Fandom) as provenance/fallback source, and Coil renders an initials fallback if an asset is unreadable. Artwork is original illustrative art, not official game art. |

**Merged Patch view:** `JsonLoader.loadPatchNotes()` treats `heroes.json` as the authoritative hero-change manifest and merges its 13 entries with the 10 in `patch_notes.json`, de-duplicated by hero ID. The Patch screen therefore lists **13** hero changes (the 10 from `patch_notes.json` plus Lukas, Karrie and Kagura from the manifest); where both files carry the same hero, the `heroes.json` entry wins.

The matchup snapshot is dated 2026-11-21, all ranks, past one day. It contains three relationship concepts: toughest matchups, favorable matchups, and best measured allies. The derivation and fallback approach are documented in [links.md](links.md#3-mlbb-game-data-and-matchup-references). These are statistical associations, not deterministic advice.

The tier snapshot is dated 2026-11-23 (all ranks, all lanes): S+ 18, S 16, A 32, B 46, C 21. `isMeta` is true exactly when tier is S+. Provenance and method are in [links.md](links.md).

#### Known data-quality issues (open)

1. **`roam_guide.json` is stale.** It declares `meta.patch = 2.2.16a` and `meta.latest = true` while the rest of the app ships 2.2.23. The Home card and Patch coverage card correctly flag it as older; its content (hero pools, Sprint cooldown 100s → 75s, Chastise Pauldron / Antique Cuirass changes) still needs a 2.2.23 refresh. This is the top data action item.
2. **`heroes.json.fixNotes` is partly corrupted.** The first note was split into 331 single-character array elements; the remaining four notes are intact strings. No app code reads this field, so there is no UI impact — it should be rejoined into one string before anything surfaces it.
3. **`items.json.notes` lost five values.** The first five array entries are bare keys (`broadsword`, `portraitPattern`, `blessings`, `equipmentSource`, `portraitOverrides`) with their values missing; the last two entries are real notes. Not read by app code.
4. **`items.json.patchChanges` (10 entries) is not surfaced anywhere.** `loadPatchNotes()` merges only `heroes.json.patchChanges` and `heroes.json.equipmentPatchChanges` (2 entries), so item removal notes and the Chastise Pauldron / Antique Cuirass / Twilight Armor details in `items.json` never reach the Patch screen. Decide whether to merge them (with de-duplication) or drop the duplicate copy in `heroes.json`.
5. **Dangling reference.** `items.json.patchChanges` points to `items_portraits.md`, which is not in the repository.
6. **Duplicate relationship fields.** Each hero carries both `counters`/`counteredBy`/`synergies` and `goodAgainst`/`badAgainst`/`goodWith`/`bestPartners` with identical values. The UI reads the second set. Keep one pair, or document which is canonical, before the two can drift.

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
| Language | Kotlin 2.4.21 |
| Android build | Android Gradle Plugin 9.1.1; Gradle wrapper 9.3.1 (committed `gradlew`) |
| Android SDK | compile 37, target 36, min 29 |
| Runtime | Single Activity + Android foreground Service |
| UI | Jetpack Compose, Material 3, Compose BOM 2026.11.01 |
| Lifecycle / Activity | AndroidX Lifecycle 2.11.1; Activity Compose 1.13.0 |
| Images | Coil Compose 3.6.3; bundled WebP portraits |
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
- [x] Optional **Voice Guide**: on-device spoken coach in the overlay (Kokoro / Piper via sherpa-onnx 1.13.8, Android TTS fallback), default OFF. Turning it on downloads the selected voice pack once (checksum-verified) into `Android/data/<app>/files/tts/`; speech is fully offline after that.
- [x] First Kotlin unit tests: **40 JVM tests** (3 classes, including Robolectric on SDK 35) covering the TTS text normalizer (time phrasing, abbreviation expansion, truncation) and the voice-guide state transitions, plus the extracted `VoiceGuideTransitions` decision table shared by manager, widget and UI.
- [x] Patch-note integration and guide references on hero/item details.
- [x] Matchup and synergy fields for all 133 heroes, with methodology and source notes.
- [x] UX refinements for quick start, search/filter/sort, navigation, and settings diagnostics.
- [x] Asset validator (`scripts/validate_assets.py`): JSON syntax, declared totals, unique IDs, portrait paths, matchup self-links/duplicates/unresolved IDs, guide pick linking, patch-change ID ↔ roster name cross-checks, and cross-file patch-version agreement.

### Next priorities — reliability and maintainability

- [~] Reproducible build: the Gradle wrapper (9.3.1) is committed, CI runs `./gradlew`, and JDK 17 / SDK 37 are recorded in the readme. Still needed: a green GitHub run of the release workflow (see `review-2026-11-27.md`, OPEN-1).
- [x] Wire `scripts/validate_assets.py` into CI: `release.yml` and `update-tts-models.yml` run it, so a bad data push fails the build.
- [ ] Refresh `roam_guide.json` from 2.2.16a to 2.2.23 (hero pools, Sprint 100s → 75s, Chastise Pauldron / Antique Cuirass, Concussive Blast vs minions) and clear its `meta.latest` flag mismatch.
- [ ] Repair `heroes.json.fixNotes` (character-split first entry) and `items.json.notes` (five orphaned keys), and decide whether `items.json.patchChanges` should be surfaced on the Patch screen.
- [ ] Collapse the duplicated hero relationship fields (`counters`/`counteredBy`/`synergies` vs `goodAgainst`/`badAgainst`/`goodWith`/`bestPartners`) to one canonical pair.
- [ ] Add Kotlin unit tests for JSON parsing, guide linking, matchup resolution, and patch merging.
- [ ] Consolidate the duplicated guide JSON parsing logic so screen summaries and overlay execution cannot silently diverge.
- [ ] Add a visible data freshness / source details screen and a consistent stale-data warning.
- [ ] Audit all guide steps, objective timings, and role recommendations against the current live patch before publishing a new data snapshot.
- [ ] Run the Voice Guide device checklist ([tts_implementation.md](tts_implementation.md) §9): first-use install and time-to-first-audio, in-game ducking, fallback when a model directory is deleted, TalkBack, incoming call, rapid Next/Prev taps, and settings persistence across restart. The feature was compile- and unit-test-verified off-device but never run on a phone.
- [~] `./gradlew assembleDebug`, `assembleRelease` and `bundleRelease` pass on JDK 17 / SDK 37 in the 2 GB sandbox (2026-11-27 later pass). The release is test-signed unless upload-key secrets are set. Remaining: a green GitHub run, and reducing native size by ABI filtering (review DEC-1).
- [ ] Verify accessibility, large text, landscape, and tablet/foldable layouts on real devices or emulators.
- [ ] Test overlay permission, foreground-service start/stop, notification behavior, and OEM-specific restrictions across supported Android versions.

### Medium-term roadmap — content and user control

- [ ] Add a versioned data-update pipeline with integrity checks, rollback, and offline fallback; keep the bundled assets as the safe baseline.
- [ ] Add rank / server / patch scope filters to matchup and meta statistics when reliable source data supports them.
- [ ] Explain matchup direction, sample limitations, and recommendation confidence in the UI.
- [ ] Add per-hero role/loadout recommendations and draft planning that accounts for the full team composition.
- [ ] Add bookmarks / favorites and recently viewed heroes or items.
- [ ] Add overlay customization for size, opacity, position, and compactness, with sensible defaults.
- [ ] Add optional guide-language support and clearer glossary explanations for game terms. Voice Guide is English-only today: both the neural voice models and the normalizer's abbreviation/clock table are English, so another language means new models plus a new normalizer table, not a toggle.
- [ ] Let Voice Guide share one `espeak-ng-data/` copy between installed models (~18 MB duplicated per model today) and consider a first-run latency probe that recommends Piper instead of Kokoro on low-end hardware.

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
- `python3 scripts/validate_assets.py` passes with zero failures.
- JSON parsing tests pass and every referenced hero/item ID resolves.
- Every guide hero pick and core item either resolves or is explicitly documented as unlinked.
- Matchup arrays contain no self-links or duplicate IDs, and their direction is tested.
- Every patch-change entry's ID matches the roster ID for its name (`patch_notes.json` and `heroes.json.patchChanges`), so Patch rows link to the right hero/item.
- `heroes.json`, `items.json`, and `patch_notes.json` agree on the bare patch number, and any version data-stamp suffix is stripped before it is compared with a guide's `meta.patch`.
- No guide declares `meta.latest = true` while pinned to an older patch.
- Asset paths resolve or show a designed fallback.
- The overlay starts, updates, hides, and stops correctly; notification and permission flows work.
- Screens remain usable with TalkBack, enlarged text, narrow phones, landscape, and larger windows.
- Data source, snapshot date, patch, rank scope, and known limitations are documented.

## 10. Where to go next

- Read [structure.md](structure.md) for the detailed file map, dependency chains, and data flow.
- Read [links.md](links.md) for Android documentation, UX references, game-data sources, matchup methodology, and the external source index.
- Read [refactoring-report.md](refactoring-report.md) for the exhaustive Kotlin review and applied refactors.
- Read [ux-redesign.md](ux-redesign.md) for the Material 3 UX audit, design system, screen-by-screen redesign, and the accessibility / QA checklists.


## 11. UX / UI implementation record

The current app-screen refresh improves first-use clarity and navigation while intentionally preserving the floating overlay implementation.

Implemented refinements:
- Home guides the player through choosing a position, selecting a playstyle, and enabling the floating guide. *(The plain-language QUICK START card that explained those three steps moved to Settings → Knowledge Base on 2026-11-27; the flow itself is unchanged and the guide card still drives it.)*
- Setup terminology uses clearer labels such as **Position**, **Playstyle**, **Enable Widget**, and **Display over other apps**.
- Overlay permission is requested through a direct Android Settings action with an explanation, rather than a misleading in-app permission switch.
- The startup preference remembers the last selected position and playstyle and can start the guide on app launch when overlay permission is already granted.
- Back handling, section navigation, and detail-page return behavior were improved.
- Hero and item explorers include sorting, clear-filter actions, result counts, and useful empty states; search/filter/sort state and the selected patch tab are preserved during navigation.
- Wider windows use a responsive navigation rail; phones retain bottom navigation. Home content width is constrained on larger screens.
- Technical data-file diagnostics are grouped in an expandable troubleshooting section.
- Compose toggle semantics and practical minimum touch sizes support accessibility.

The refresh did not change the overlay implementation files: `WidgetOverlayService.kt`, `JetOverlay.kt`, and `OverlayWidgets.kt`.

### Home simplification (27 November 2026)

Home was cut back to the core task. Removed: the **CURRENT META** patch banner (a clickable card at the top that opened Patch notes), the **QUICK START** card, the **PATCH MOVERS** row, the **QUICK ACTIONS** row (Hero Explorer / Item Explorer / Patch Notes shortcuts) and the **TOP META HEROES** row. Rationale: the bottom navigation bar (and rail on wide windows) already reaches every section, so the shortcuts duplicated it; and the banner/movers/meta rows pushed the guide setup below the fold on small phones.

Home is now: header → guide setup card (position, playstyle, briefing, picks, loadout, status chips, Launch) → collapsible lane-mechanics card → floating Launch button. The mechanics card **starts collapsed**, with its section title inside a 56dp header row that toggles it and a subtitle reporting how many notes and rules are inside; expand/collapse state is saved and resets when the role changes.

Two follow-on corrections: `HomeScreen` no longer accepts `onOpenHeroes` / `onOpenItems` / `onOpenPatchNotes` (their only consumers were removed, and an accepted-but-unwired callback is a silent no-op), and `GameData.topMetaHeroes` in `Models.kt` now has no caller — it is retained as a data-layer helper but is currently dead code.

> **Correction (27 November 2026):** this section previously stated that `MLBBPlusTheme.kt` was unchanged. It has since been extended: the file holds two separate themes — `AppUiTheme` / `AppUiColors` for the main app and `MLBBPlusTheme` / `MLBBPlusColors` for the floating overlay. The Material 3 redesign pass added `darkTheme` / `dynamicColor` parameters to `AppUiTheme` and pointed it at the new `Color.kt` / `Type.kt` token files. **The overlay theme and overlay behaviour were not changed**, and `AppUiTheme` still defaults to dark because screens read `AppUiColors.*` directly. See [ux-redesign.md](ux-redesign.md).

## 12. Matchup snapshot audit summary

The 2026-11-21 matchup audit reports:
- 133 heroes total; all 133 have favorable-matchup entries, toughest-matchup entries, and best-measured-ally entries.
- All 133 have at least three toughest matchups.
- Toughest-matchup lists contain 3–5 entries; favorable-matchup and best-measured-ally lists contain 4–5 entries.
- Direct MLBBHub counter fallback was used for 43 heroes.

The fallback roster is: Angela, Arlott, Aulus, Aurora, Badang, Brody, Carmilla, Cecilion, Chou, Claude, Clint, Dyrroth, Guinevere, Gusion, Hayabusa, Hilda, Hirara, Julian, Kadita, Kagura, Lapu-Lapu, Leomord, Lesley, Luo Yi, Martis, Minotaur, Miya, Moskov, Nana, Natan, Nolan, Novaria, Obsidia, Paquito, Pharsa, Selena, Sora, Suyou, Terizla, Vexana, Yi Sun-shin, Yu Zhong, and Zetian.

The audit is a coverage/shape check, not proof that every recommendation is strategically correct. Interpret the relationship fields using the methodology summarized in the Sources section of `links.md`; matchup associations are not guarantees.


## 13. Refactoring snapshot (21 November 2026, re-verified 27 November 2026)

The latest repository refactor fixes model/UI mismatches in the item and hero screens, restores the shared `AssetImage` API expected by catalogue screens, adds item status handling, makes catalogue filters and sorting saveable, and validates overlay role/playstyle defaults. All item JSON image paths resolve to local WebP files — re-verified on 2026-11-27 for all **99** active item records and all **133** hero records. See [refactoring-report.md](refactoring-report.md) for the complete file-by-file review and build-validation caveat.

> **Correction (27 November 2026):** this section previously said the refactor "switches hero/item catalogues to adaptive grids". The shipped code uses `GridCells.Fixed(5)` in both `HeroesScreen.kt` and `ItemsScreen.kt`, with an explicit comment that `Adaptive` produced three ~102dp columns on a phone. Fixed(5) is the current reality; an adaptive grid (`GridCells.Adaptive(64.dp)`, which equals five columns on a 360dp phone) is a *proposal* in [ux-redesign.md](ux-redesign.md), not implemented behaviour.

## 14. Data refresh record (27 November 2026)

The bundled JSON was refreshed after the 21 November documentation pass (heroes 2026-11-23, items 2026-11-26, guides 2026-11-20). The docs were out of date; they are now synced to the assets, and the refresh exposed three defects that were fixed in this pass:

| Defect found in the refreshed data | User-visible effect | Fix |
|---|---|---|
| `patch_notes.json` listed Masha with `heroId: 87`; hero 87 is **Baxia** (Masha is 88) | Masha appeared **twice** on the Patch screen, and tapping that row opened **Baxia's** detail screen | Corrected the ID to 88 in `patch_notes.json`; the merge now de-duplicates to a single Masha row (13 hero changes total) |
| `heroes.json.version` now carries a data stamp (`2.2.23-tier-2026-11-23`) and `displayVersion` passed it through unmodified | Patch header read "Patch 2.2.23-tier-2026-11-23"; the guide-coverage card and the **in-match overlay** labelled all five guides as "older than" the live patch, even the four written for 2.2.23 | Added `PatchNotesFile.shortVersion` (bare patch number) and used it in `PatchNotesScreen`, `OverlayWidgets`, `SettingsScreen`, `HomeScreen`, `HeroesScreen`, `ItemsScreen` — replacing three copies of the same `substringBefore("-")` expression |
| The Patch card date came from `heroes.json.updated` (2026-11-23, the data refresh) | The patch card implied 2.2.23 landed on 23 Nov instead of **11 Nov** | `loadPatchNotes()` now prefers `heroes.json.patchDate`, falling back to `updated`, then to `patch_notes.json.date` |
| Settings judged guide staleness as `!g.latest && patch mismatch` | `roam_guide.json` declares `latest: true` while pinned to 2.2.16a, so Settings showed a green **LATEST** badge for a genuinely stale guide | Staleness now keys off the patch number; `latest` only labels a guide that is actually current |

Also added in this pass: `scripts/validate_assets.py` (referenced by `readme.md` and `refactoring-report.md` but missing from the repository) — recreated, then extended with the cross-file checks that would have caught the Masha ID bug. It passes on the current assets with one warning: the stale Roam guide.

Still open (data-owner decisions, tracked in §8): refresh `roam_guide.json` to 2.2.23; repair `heroes.json.fixNotes` and `items.json.notes`; decide whether `items.json.patchChanges` should reach the Patch screen; collapse the duplicated hero relationship fields.
