# MLBB Plus

MLBB Plus is an offline-first Android companion for Mobile Legends: Bang Bang. It combines hero/item/patch reference screens with a role-aware floating guide overlay for Roam, Jungle, Mid, Gold, and EXP, plus an optional on-device spoken coach (**Voice Guide**, off by default). This is an independent fan project and is not affiliated with Moonton.

## Build

Requirements: JDK 17, Android SDK Platform 37, Android Gradle Plugin / Gradle versions pinned in the repository.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

The first build requires Gradle and Android dependencies to be available from their configured repositories.

The optional Voice Guide needs the sherpa-onnx AAR, which is **not** on Google Maven, plus three
TTS voice packs. A `downloadSherpaAar` task fetches `sherpa-onnx-1.13.8.aar` into `app/libs/` and a
`bundleTtsModels` task fetches the voice packs into `app/tts-models-cache/`; both verify sha256 and
run automatically as part of `assembleDebug` / `assembleRelease`, so the first build needs network
access once (or place the files yourself and the tasks do nothing). The voice packs are **compiled
into the APK** and unpacked into `filesDir/tts/` on first use, so speech is fully offline at
runtime. See [`TTS_IMPLEMENTATION.md`](TTS_IMPLEMENTATION.md).

Before building, validate local data and portraits:

```bash
python3 scripts/validate_assets.py
```

Unit tests (JVM only, no device or emulator needed):

```bash
./gradlew :app:testDebugUnitTest
```

## Documentation

- [`docs/overview.md`](docs/overview.md) — product purpose, capabilities, data snapshot, and roadmap.
- [`docs/structure.md`](docs/structure.md) — project structure, runtime flows, architecture, and change-impact map.
- [`docs/links.md`](docs/links.md) — official Android/Kotlin/library docs, UX references, and game-data provenance.
- [`docs/refactoring-report.md`](docs/refactoring-report.md) — exhaustive Kotlin review, implemented changes, file-by-file recommendations, and validation notes.
- [`docs/ux-redesign.md`](docs/ux-redesign.md) — Material 3 UX audit, design system, screen-by-screen redesign, accessibility and QA checklists.
- [`TTS_IMPLEMENTATION.md`](TTS_IMPLEMENTATION.md) — Voice Guide: engine choice, model URLs and checksums, storage, text normalisation, state machine, how to test, known limitations.

## Bundled data

Current snapshot (verified 27 November 2026 with `python3 scripts/validate_assets.py`):

| Asset | Contents |
|---|---|
| `heroes.json` | 133 heroes · version `2.2.23-tier-2026-11-23` · data updated 2026-11-23 · patch live 2026-11-11 · matchups 2026-11-21 · tiers 2026-11-23 |
| `items.json` | 99 items, all active · version `2.2.23-buildtree-2026-11-23` · data updated 2026-11-26 · build tree 2026-11-23 |
| `patch_notes.json` | Patch 2.2.23 Original Server, 2026-11-11 · 10 hero + 2 item changes, highlights, battlefield/system notes |
| `*_guide.json` | 26 role/playstyle types, 286 timed steps · Jungle/Mid/Gold/EXP at 2.2.23 (2026-11-20) · **Roam still at 2.2.16a (2026-11-18)** |

### Item portraits

Every one of the 99 active item records ships a bundled WebP at `assets/items/{id}.webp`, and every one of the 133 heroes ships `assets/heroes/{slug}.webp`; the validator confirms all declared local paths resolve, so no download step is needed before building. Each item record also carries a remote `portrait` URL (Fandom) as provenance, and Coil renders an initials fallback if an asset is ever missing or unreadable.

> The catalogue grew from the original 41 items: 66 were added on 2026-11-21 (Tier 1/2 components plus missing Tier 3 items), then legacy and duplicate entries were removed on 2026-11-26 (Courage Mask 4003, shop Favor 4004, Athena's Shield 3004 → Valkyrie's Shield 3015, Twilight Armor 3007). Original item IDs, stats and build paths were preserved so `heroes.json` core-build references still resolve. An earlier readme revision described 107 items with `items/{id}.png` portraits fetched by `scripts/download_item_portraits.py`; that intermediate state and that script no longer exist.

### Known data issues

`roam_guide.json` is pinned to patch 2.2.16a while declaring `meta.latest = true`; `heroes.json.fixNotes` has one note split into single characters; `items.json.notes` has five entries that are orphaned keys; `items.json.patchChanges` is not surfaced on the Patch screen. Details and owners are in [`docs/overview.md`](docs/overview.md) §5 and §14.
