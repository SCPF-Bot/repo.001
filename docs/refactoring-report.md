# MLBB Plus — Kotlin Review & Refactoring Report

**Review date:** 21 November 2026  
**Reviewed source:** `repo.001-main` archive supplied for this task  
**Scope:** all 14 Kotlin files, app/build configuration, manifest, bundled JSON/assets, existing documentation, and release workflow.

## 1. Executive summary

The repository is a single-module, offline-first Android application built with Kotlin and Jetpack Compose. Its core product boundary is sound: a normal in-app reference experience plus a separate floating, role-aware guide that can remain visible while a match is running. The overlay's original purpose is preserved; no new navigation or decorative animation layer was allowed to distract from the in-match guide.

The review found several source-level inconsistencies that would prevent or undermine a clean build, along with UI and data-integrity issues:

- `HeroesScreen.kt` referenced `RoleTypes.byRole`, which does not exist; the canonical role list is `RoleTypes.roles`.
- Both catalogue screens imported `widgets.AssetImage`, but no such declaration existed. The image implementation lived in `screens/AssetImage.kt` and exposed only `HeroImage` / `ItemImage`.
- `ItemsScreen.kt` referenced `Item.relatedItemIds`, `Item.synergyHeroIds`, and `ItemStat.label`, while the actual model fields are `counters` / `counteredBy`, `synergiesHeroes` / `worksAgainstHeroes`, and `ItemStat.stat`.
- The item catalogue did not distinguish active entries from the removed legacy item in `items.json`.
- All item image paths were declared in JSON, but the `assets/items/` directory was absent.
- Hero and item grids were fixed at seven columns, which made cards and labels too small on narrow phones. Sort state existed but had no visible controls.
- The Gradle settings still declared JitPack with comments for libraries that were not dependencies of this project.
- The root `readme.md` was empty, and the existing docs contained links to standalone documents that were not present in the archive.

The immediate source mismatches, catalogue behavior, grid behavior, item portrait coverage, stale repository configuration, and documentation gaps have been corrected in this refactor. Larger architectural migrations are documented as staged follow-up work rather than introduced without tests or build verification.

## 2. Implemented changes

### Kotlin and UI

1. **`Models.kt`** — added `Item.status` and parse the JSON status with a safe `active` default. Removed items remain resolvable for old builds/details, but can be excluded from the active catalogue.
2. **`AssetImage.kt`** — corrected fallback paths to `heroes/{slug}.webp` and `items/{id}.webp`; added shared `AssetImage(hero=...)` and `AssetImage(item=...)` overloads expected by the catalogue screens; passed through content scale and accessibility descriptions.
3. **`HeroesScreen.kt`** — replaced the invalid `RoleTypes.byRole` reference with a distinct role/class list derived from the loaded hero records; made search and filters saveable; exposed the existing recommended / win-rate / name sort state as visible controls. *(Grid: the shipped code uses `GridCells.Fixed(5)` — see the 27 November addendum.)*
4. **`ItemsScreen.kt`** — corrected model-field references; excludes removed items from the active catalogue; uses the correct counter and hero relationship fields; displays hero synergy and effective-against relationships separately; made search/category/sort state saveable; added visible price/recommended sorting controls. *(Grid: `GridCells.Fixed(5)` — see the 27 November addendum.)*
5. **`WidgetOverlayService.kt`** — validates a restored or incoming role against the supported roles and normalizes invalid/missing playstyle IDs to the role's default before rendering the widget.
6. **`OverlayWidgets.kt`** — changed the composable's fallback `roleType` default from the invalid display label `Tank` to an empty sentinel, allowing the loaded guide's actual type ID to be selected.

### Assets and repository quality

- Added `app/src/main/assets/items/` with WebP portraits for all **41** item records: 40 active entries and one removed legacy entry. *(Superseded: the catalogue now holds **99** active records with 99 bundled WebP portraits — see the 27 November addendum.)*
- Added `scripts/validate_assets.py` to validate JSON syntax, item counts, unique IDs, and every declared hero/item image path. *(The file was missing from the repository as of 27 November 2026 and has been recreated and extended — see the addendum.)*
- Added an asset-validation step to the release workflow before Android build setup. *(Not present in the current `.github/workflows/release.yml`: the workflow runs checkout → JDK 17 → Gradle → SDK → keystore → `assembleRelease` with no validation step. See the addendum.)*
- Removed the unused JitPack repository and stale dependency comments from `settings.gradle.kts`.
- Added `.gitignore` entries for Gradle/Android build outputs, local properties, signing files, and generated packages.
- Replaced the empty root README with project, build, validation, and documentation entry points.
- Consolidated documentation links around files that actually exist and added this report.

### Item portrait provenance

The new item portraits are original illustrative fantasy-equipment artwork generated for this project and cropped into individual 256×256 WebP assets. They are **not official MLBB / Moonton item artwork** and should not be represented as exact in-game icons. The stable IDs and `image` paths in `items.json` were retained. If exact official-looking art is required, replace these generated illustrations with properly licensed/source-attributed assets while keeping the same filenames.

## 3. File-by-file Kotlin review and refactoring plan

This section covers every Kotlin source file in the archive. “Implemented” identifies changes made in this pass; “Next” records the remaining high-value refactor without implying it has already shipped.

| Kotlin file | Review findings | Implemented / recommended refactor | Best-fit references |
|---|---|---|---|
| `ui/MainActivity.kt` | Owns Activity startup, root navigation, overlay permission flow, preference loading, role persistence, and Back behavior. The file is a large composition root; `GameData.load()` correctly occurs before `setContent`. Some selected detail objects are transient rather than ID-backed saveable state. | Keep this as a thin host. In a later pass, move preference I/O into a small `UserPreferences` abstraction and move root UI state into a navigation/state-holder file. Store selected hero/item IDs if detail restoration across process death becomes a requirement. Keep permission truth refreshed from Android rather than optimistic UI state. | [Compose state](https://developer.android.com/develop/ui/compose/state), [state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting), [Activity](https://developer.android.com/jetpack/androidx/releases/activity), [overlay permission](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW) |
| `ui/WidgetOverlayService.kt` | Owns foreground-service lifecycle, notification channel/actions, role selection, and overlay startup/teardown. Static service state can be stale after process recreation; null-intent restarts need validated defaults. | Implemented role/type normalization for incoming and restarted service intents. Next, keep the service narrowly focused on lifecycle/notification orchestration and add instrumentation coverage for start, update, Stop action, permission revocation, and process recreation. | [Foreground services](https://developer.android.com/develop/background-work/services/fgs), [special-use service type](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use), [notifications](https://developer.android.com/develop/ui/views/notifications) |
| `ui/components/UiKit.kt` | Centralizes custom vector icons, role colors, title/header components, and reusable search/filter UI. The hand-authored icon path helper falls back to a generic square on malformed path data. | Keep shared components here; add previews and semantics tests for interactive primitives. Consider a typed design-token object instead of scattering palette access across app and overlay themes. Preserve 48dp minimum filter targets. | [Material 3 Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility) |
| `ui/model/Models.kt` | Contains domain models, tolerant JSON parsing, patch merging, the `GameData` singleton, and several cross-reference indexes. It is a large responsibility cluster; the current eager load is reasonable for bundled offline data. | Implemented `Item.status` parsing. Keep per-record failure isolation and cached ID maps. Next, split model declarations, JSON parsing, and `GameData` indexing into separate files; introduce a repository interface only when tests, remote refresh, or multiple data sources are added. | [Android architecture](https://developer.android.com/topic/architecture), [architecture recommendations](https://developer.android.com/topic/architecture/recommendations), [Kotlin collections](https://kotlinlang.org/docs/collections-overview.html) |
| `ui/model/Guides.kt` | Loads the five role-guide JSON files and builds name-to-ID links for heroes and item guide references. Parsing is defensive, but the overlay contains a second parser for similar data. | Keep guide models and parsing out of composables. Next, unify the screen and overlay parsers behind one typed guide data model so schema changes cannot drift; add fixture tests for missing/empty/unknown fields. | [Android assets](https://developer.android.com/reference/android/content/res/AssetManager), [Compose architecture](https://developer.android.com/develop/ui/compose/architecture), [Kotlin null safety](https://kotlinlang.org/docs/null-safety.html) |
| `ui/screens/AssetImage.kt` | Uses Coil 3 for bundled images and a fallback portrait. Previously, the catalogue expected a differently packaged `AssetImage` API and the blank-path fallback did not match the actual asset layout. | Implemented the shared overloads, corrected fallback path conventions, and propagated descriptions/content scale. Keep images local and offline-first; use stable IDs/slugs as fallback paths and retain an accessible fallback when a file is absent. | [Coil](https://coil-kt.github.io/coil/), [Coil Compose API](https://coil-kt.github.io/coil/api/coil-compose/coil3.compose/index.html), [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility) |
| `ui/screens/HomeScreen.kt` | Owns the role/playstyle overview, guide summary, quick actions, patch/meta content, and launch CTA. It is a dense dashboard with multiple nested sections and selection state. | Preserve its role as the first-use decision surface. Next, extract independent sections (role selector, quick actions, patch/meta summary) into small stateless composables and pass state/events down. Avoid adding animation libraries for decorative motion; Compose animation APIs are sufficient for the current interaction needs. | [Compose UI architecture](https://developer.android.com/develop/ui/compose/architecture), [state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting), [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3) |
| `ui/screens/HeroesScreen.kt` | Combines catalogue filters, sorting, hero detail, and related hero/item links. It referenced a non-existent `RoleTypes.byRole`, and its fixed seven-column grid compressed touch targets and text. | Implemented canonical role-list access, saveable search/filter state, and visible sort controls. *(Grid: shipped as `GridCells.Fixed(5)`, not adaptive — see addendum A.)* Next, separate catalogue and detail composables and test relationship ID resolution and empty states. | [Adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive), [lazy grids](https://developer.android.com/develop/ui/compose/lists), [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics) |
| `ui/screens/ItemsScreen.kt` | Combines item browsing/detail, but its references to `relatedItemIds`, `synergyHeroIds`, and `ItemStat.label` did not match the actual model. Removed item records were not excluded from the main catalogue. The fixed seven-column grid was too dense. | Implemented model-aligned counters/countered-by and hero links, `ItemStat.stat`, active/removed filtering, saveable filters, and visible sort controls. *(Grid: shipped as `GridCells.Fixed(5)`, not adaptive — see addendum A.)* Detail tabs now distinguish item counters from hero synergy/effectiveness relationships. | [Adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive), [lazy grids](https://developer.android.com/develop/ui/compose/lists), [state in Compose](https://developer.android.com/develop/ui/compose/state) |
| `ui/screens/PatchNotesScreen.kt` | Presents patch sections and navigation to changed entities. The selected patch tab already uses saveable state. | Keep patch data presentation read-only and source-aware. Next, extract reusable change cards and add tests for absent patch blocks, unknown entity IDs, and long text scaling. | [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3), [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility) |
| `ui/screens/SettingsScreen.kt` | Owns overlay-permission explanation, auto-start preference, and diagnostics. Permission is a system-level special permission, not an ordinary runtime toggle. | Keep permission status derived from `Settings.canDrawOverlays()` and refresh on resume. Next, consolidate preference access with the Activity behind a preferences interface; consider DataStore if settings grow beyond the current small set. | [Overlay permission](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW), [Settings action](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |
| `ui/theme/MLBBPlusTheme.kt` | Maintains separate app and floating-overlay palettes. This separation is intentional because the overlay must remain compact and readable over a game. | Preserve the app/overlay theme boundary. Next, consolidate duplicated color tokens and test contrast in both light/dark system settings and over bright/dark game backgrounds. Do not force dynamic color onto the overlay if it reduces in-game contrast. | [Material 3 theming](https://developer.android.com/develop/ui/compose/designsystems/material3), [accessible design](https://m3.material.io/foundations/accessible-design/overview) |
| `ui/widgets/JetOverlay.kt` | Implements the WindowManager host, drag interception, saved position, touch wake signal, and custom Lifecycle/SavedState/ViewModel owners for ComposeView. This is the most platform-sensitive part of the app. | Preserve its pass-through behavior so touches outside the widget reach the game. Keep add/remove idempotent and owner cleanup paired. Next, add device/API-level tests for drag, permission revocation, window removal, and service destruction; replace silent catches with diagnostic logging where safe. | [SYSTEM_ALERT_WINDOW](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW), [Compose in Views](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views), [Lifecycle](https://developer.android.com/topic/libraries/architecture/lifecycle) |
| `ui/widgets/OverlayWidgets.kt` | Contains the role-aware in-game UI, private guide parser, timer/phase computation, objective reminders, spell cooldown tracker, and interaction state. Its size and duplicate parser are the main maintainability risks. | Implemented safer default type handling. Keep the widget's purpose and compact overlay behavior intact. Next, split into `GuideOverlayScreen`, `GuideTimerState`, `ObjectiveTracker`, `SpellTracker`, and a shared guide parser; keep all timer loops in cancellable Compose effects and test objective edge cases. | [Compose effects](https://developer.android.com/develop/ui/compose/side-effects), [Compose state](https://developer.android.com/develop/ui/compose/state), [foreground services](https://developer.android.com/develop/background-work/services/fgs) |

## 4. Architecture decisions

### Keep now

- Single app module: the project is still small enough that module splitting would add build complexity without a clear boundary benefit.
- Bundled JSON: the app's core reference experience remains offline-first.
- Coil 3: already present and suitable for image decoding/caching. No additional image stack is needed.
- Compose animation: the existing overlay uses Compose animation APIs. Lottie is not justified for a tactical overlay where motion should not compete with gameplay.
- Custom floating overlay: keep `JetOverlay` and `WidgetOverlayService` as the delivery mechanism because the floating guide is a core product feature, not a general-purpose chat bubble.

### Stage next, behind tests

1. Extract `GuideLoader` into a single parser used by both the app and overlay.
2. Separate `JsonLoader` / `GameData` from model declarations and add parser fixtures.
3. Move preference reads/writes out of composables into a small preferences abstraction; migrate to DataStore only if scope grows.
4. Split `OverlayWidgets.kt` by feature while preserving its public entry point and state semantics.
5. Add Compose UI tests and Android instrumentation tests for permission/service/overlay lifecycle.
6. Consider a ViewModel/repository layer if data becomes asynchronous, remotely refreshed, or user-editable. Do not add Hilt/Room/Navigation solely for architectural fashion.

## 5. UX and UI changes

- Responsive hero/item grids use adaptive cell sizing instead of seven fixed columns.
- Search and catalogue filter/sort state survives Activity recreation.
- Sort controls are visible and wired to the sort state that already existed in the screens.
- Removed items remain in the underlying model for historical references but do not pollute the active item catalogue.
- Item detail relationship labels now match the JSON semantics: counters/countered-by, hero synergies, and heroes the item works against.
- Portraits are present for all item JSON records; Coil retains a fallback if an asset is missing or unreadable.
- The floating guide's mission is unchanged: role/playstyle plan, match timer, objective timing, and enemy spell cooldown tracking remain the focus.

No Lottie dependency was added. The current app does not need decorative animation to solve its usability issues; Compose's built-in animation APIs are smaller and already in use. Coil remains the image-loading solution.

## 6. Validation results

| Check | Result |
|---|---|
| Python JSON / portrait validator | **Passed** — 133 heroes, 41 item records, 40 active items, all declared hero/item portraits present *(numbers superseded by the 2026-11-27 refresh: 99 items, 99 active — see addendum A; the script itself was missing from the repo and has been recreated)* |
| Unique item IDs | **Passed** |
| Declared item totals | **Passed** — `totalItems=41`, `totalActiveItems=40` *(now `totalItems=99`, `totalActiveItems=99`)* |
| Kotlin unresolved-reference sweep for the known mismatches | **Passed** — no remaining references to `RoleTypes.byRole`, `relatedItemIds`, `synergyHeroIds`, `ItemStat.label`, or `widgets.AssetImage` |
| Gradle debug build | **Not verified** — the Gradle wrapper attempted to download Gradle 9.6.1, but the execution environment could not resolve `services.gradle.org` (`UnknownHostException`). This is an environment/network limitation, not a successful build result. |

Run locally or in CI:

```bash
python3 scripts/validate_assets.py
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

A successful asset validation is not a substitute for compiling, linting, device testing, or verifying overlay behavior across Android versions.

## 7. External references

The implementation decisions above are grounded in the official platform and library references indexed in [`links.md`](links.md), especially:

- [Android app architecture](https://developer.android.com/topic/architecture)
- [Compose state and state hoisting](https://developer.android.com/develop/ui/compose/state-hoisting)
- [Compose adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive)
- [Compose lazy lists and grids](https://developer.android.com/develop/ui/compose/lists)
- [Compose accessibility semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)
- [Android foreground services](https://developer.android.com/develop/background-work/services/fgs)
- [Android overlay permission](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW)
- [Coil](https://coil-kt.github.io/coil/)
- [Material 3 for Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)

## Follow-up release compilation fix

- `HeroesScreen.kt`: corrected the hero detail core-build source from the nonexistent `Hero.coreBuild` property to the model's declared `Hero.coreItems` property.
- Made the `remember` keys include `hero.coreItems`, so the filtered build list is recalculated when the hero's item build changes.
- Used an explicit lambda parameter (`buildItem`) to make the item type clear and avoid a misleading unresolved-`it` cascade.
- Release build attempted with `./gradlew :app:assembleRelease`; it could not start because the environment could not resolve `services.gradle.org` to download Gradle 9.6.1. Compilation is therefore not yet verified.

---

## Addendum — 27 November 2026 (data refresh sync)

The bundled JSON was refreshed after this report was written (heroes 2026-11-23, items 2026-11-26, guides 2026-11-20), and the documentation had not been updated. This addendum records what the refreshed data changed, what it broke, and what was fixed. It does not rewrite the 21 November findings above; where a statement above is now inaccurate it is annotated inline.

### A. Numbers that changed

| Claim in this report (21 Nov) | Verified reality (27 Nov) |
|---|---|
| 41 item records, 40 active, one removed legacy entry | **99** item records, **99** active, no removed entries left in the file |
| Validator passed: `totalItems=41`, `totalActiveItems=40` | Validator passes with `totalItems=99`, `totalActiveItems=99` |
| Hero/item grids changed to adaptive columns | `GridCells.Fixed(5)` in both screens, with the comment `PHONE-PROOF GRID — GridCells.Fixed(5), NEVER Adaptive.` (Adaptive yielded three ~102dp columns on a phone) |
| `scripts/validate_assets.py` added | File was **absent** from the repository; recreated and extended |
| Asset-validation step added to the release workflow | No such step exists in `.github/workflows/release.yml` |
| 133 heroes | Still 133 — confirmed, plus 133 bundled portraits |

Item growth path recorded in `items.json.patchChanges`: 66 items added 2026-11-21 (Tier 1/2 components + missing Tier 3), legacy/duplicate entries removed 2026-11-26 (Courage Mask 4003, shop Favor 4004, Athena's Shield 3004 → Valkyrie's Shield 3015, Twilight Armor 3007). Original IDs, stats and build paths were preserved, and all 133 heroes' core-build items still resolve (0 unlinked).

### B. Why the repository drifted

`.github/workflows/extract.yml` deletes **every** file and directory except `.github`, `.git`, and root archives, then extracts the uploaded archives and commits the result. Anything not inside the archive is erased on the next archive push — which explains both the missing `scripts/validate_assets.py` and the documentation lagging the data. **Action:** either include `scripts/` and `docs/` in the archive, or narrow the delete step to the paths the archive actually replaces. Until then, treat every archive push as destructive to hand-maintained files.

### C. Defects found in the refreshed data (and fixed)

1. **Wrong hero ID in `patch_notes.json`.** Masha was listed with `heroId: 87`; hero 87 is **Baxia**, Masha is **88**. Because `loadPatchNotes()` merges `patch_notes.json` with the `heroes.json` manifest by hero ID, Masha appeared **twice** on the Patch screen and the mis-ID'd row opened **Baxia's** detail screen. Fixed the ID to 88; the merge now de-duplicates to one Masha row (13 hero changes total). All other patch-change IDs (9 hero + 2 item in `patch_notes.json`, 13 in `heroes.json`) were cross-checked by name and are correct.
2. **Version data-stamp leaked into UI and comparisons.** `heroes.json.version` is now `2.2.23-tier-2026-11-23`, and `PatchNotesFile.displayVersion` passed it through. Home/Heroes/Items already stripped it with `substringBefore("-")`, but `PatchNotesScreen` (header, meta-by-lane card, guide-coverage card), `OverlayWidgets` (in-match patch label) and `SettingsScreen` (diagnostics) did not. Result: the Patch header read "Patch 2.2.23-tier-2026-11-23" and **all five guides were labelled older than the live patch — including inside the overlay during a match**, the worst place for false noise. Fixed by adding `PatchNotesFile.shortVersion` and using it at those sites, which also removed three duplicated `substringBefore("-")` expressions.
3. **Patch card showed the data-refresh date as the patch date.** `loadPatchNotes()` set `PatchData.date` from `heroes.json.updated` (2026-11-23) rather than `patchDate` (2026-11-11), implying 2.2.23 landed on 23 Nov. Now prefers `patchDate`, then `updated`, then `patch_notes.json.date`.
4. **Settings masked a genuinely stale guide.** Guide staleness was computed as `!g.latest && patch mismatch`. `roam_guide.json` declares `meta.latest = true` while pinned to **2.2.16a**, so Settings showed a green LATEST badge for the one guide that is actually out of date. Staleness now keys off the patch number; `latest` only labels a guide that is truly current.

### D. Validator (`scripts/validate_assets.py`) — recreated and extended

Checks: JSON syntax for all eight assets; declared totals vs actual counts; duplicate hero/item IDs; every declared local portrait path; matchup arrays for self-links/duplicates/unresolved IDs; guide hero picks resolving by name; patch-change IDs cross-checked against roster names in both files; cross-file agreement on the bare patch number; and a warning when a guide declares `latest = true` at an older patch. Current result: **pass, 1 warning** (Roam guide at 2.2.16a). Checks 6–8 are new and are what would have caught defect C1 automatically.

### E. Still open (data-owner decisions)

- **Refresh `roam_guide.json` to 2.2.23** — highest priority: it is the only guide out of sync with the live patch, and Roam is the app's default role. Content needing review: hero pools, Sprint cooldown 100s → 75s, Chastise Pauldron (attacker keeps 80% AS), Antique Cuirass (Deter 6% → 8%), Concussive Blast (50% less damage to minions), and the `meta.patch` / `meta.latest` flags.
- **`heroes.json.fixNotes` is corrupted** — the first note was split into 331 single-character array elements (the remaining four notes are intact strings). Not read by app code; rejoin before surfacing it.
- **`items.json.notes` lost five values** — the first five entries are bare keys (`broadsword`, `portraitPattern`, `blessings`, `equipmentSource`, `portraitOverrides`) with their values missing. Not read by app code.
- **`items.json.patchChanges` (10 entries) is never surfaced** — `loadPatchNotes()` merges only `heroes.json.patchChanges` and `heroes.json.equipmentPatchChanges` (2 entries), so the item removal notes and the Chastise Pauldron / Antique Cuirass / Twilight Armor details do not reach the Patch screen. Decide: merge with de-duplication, or drop the duplicate copy in `heroes.json`.
- **Dangling reference** — `items.json.patchChanges` cites `items_portraits.md`, which is not in the repository.
- **Duplicate relationship fields** — every hero carries `counters`/`counteredBy`/`synergies` *and* identical `goodAgainst`/`badAgainst`/`goodWith`/`bestPartners`; the screens read the second set. Collapse to one canonical pair before they can drift.
- **Merge precedence** — where both patch files list a hero, the `heroes.json` manifest entry wins (`associateBy { heroId }`, last wins), so the richer `patch_notes.json` fields (`skillsAffected`, `impact`, `tierChange`, `isMeta`) are dropped for those 8 heroes. Intended per the code comment ("heroes.json is the authoritative patch manifest"), but worth an explicit decision now that both files carry overlapping content.

### F. Build verification status (unchanged caveat)

No Gradle build has been run for this pass either: the working environment has JDK 11 and no Android SDK, while the project requires JDK 17 plus SDK platform 37. All changes were verified statically (referential checks against the assets, API cross-checks against `Models.kt`/`Guides.kt`, and a Python simulation of the `loadPatchNotes()` merge). Before merging, run:

```bash
python3 scripts/validate_assets.py     # currently: pass, 1 warning
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```
