# MLBB Plus

MLBB Plus is an offline-first Android companion for Mobile Legends: Bang Bang. It combines hero/item/patch reference screens with a role-aware floating guide overlay for Roam, Jungle, Mid, Gold, and EXP. This is an independent fan project and is not affiliated with Moonton.

## Project snapshot

- Kotlin + Jetpack Compose + Material 3
- Single Android application module (`:app`)
- Bundled JSON datasets; no runtime backend required for core browsing
- Coil 3 for local WebP portraits
- Floating `TYPE_APPLICATION_OVERLAY` guide hosted by a foreground service
- 133 hero records and 41 item records (40 active, 1 removed legacy record)

## Build

Requirements: JDK 17, Android SDK Platform 37, Android Gradle Plugin / Gradle versions pinned in the repository.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

The first build requires Gradle and Android dependencies to be available from their configured repositories.

Before building, validate local data and portraits:

```bash
python3 scripts/validate_assets.py
```

## Documentation

- [`docs/overview.md`](docs/overview.md) — product purpose, capabilities, data snapshot, and roadmap.
- [`docs/structure.md`](docs/structure.md) — project structure, runtime flows, architecture, and change-impact map.
- [`docs/links.md`](docs/links.md) — official Android/Kotlin/library docs, UX references, and game-data provenance.
- [`docs/refactoring-report.md`](docs/refactoring-report.md) — exhaustive Kotlin review, implemented changes, file-by-file recommendations, and validation notes.

## Item portraits

All 41 item records now resolve to local WebP portraits under `app/src/main/assets/items/`. The portrait set is original illustrative artwork generated for this project; it is not official Moonton item art.
