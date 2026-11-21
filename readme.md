# MLBB Plus

MLBB Plus is an offline-first Android companion for Mobile Legends: Bang Bang. It combines hero/item/patch reference screens with a role-aware floating guide overlay for Roam, Jungle, Mid, Gold, and EXP. This is an independent fan project and is not affiliated with Moonton.

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
