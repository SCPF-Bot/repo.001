# Matchup and synergy data update history

> **Historical note:** This file records the earlier, intentionally partial matchup update. It has been superseded by the complete 133-hero snapshot described in [MATCHUP-DATA-SOURCES.md](MATCHUP-DATA-SOURCES.md) and the current `app/src/main/assets/heroes.json` metadata.

## Earlier partial update — 2026-11-21

The first cleanup removed repeated placeholder relationships and populated sourced relationships for Hayabusa, Saber, Lancelot, Hanabi, and Eudora. At that stage, other heroes were left empty rather than assigning invented values.

That partial state is no longer the current project state.

## Current full-roster snapshot — 2026-11-21

The later full-roster refresh populates all 133 heroes in all three relationship categories:

- **Favorable matchups** (`counters`): source-ranked opponents the selected hero performs well against.
- **Toughest matchups** (`counteredBy`): reverse-indexed favorable-counter relationships, with direct MLBBHub ranked-counter fallback for low-coverage heroes.
- **Best measured allies** (`synergies` / `bestPartners`): source-ranked same-team synergy picks.

The current data uses MLBB Meta Analyser's all-ranks / past-1-day export for favorable matchups and ally synergy, and MLBBHub for direct-counter fallback on 43 heroes. The full methodology, source URLs, date, limitations, and interpretation are maintained in [MATCHUP-DATA-SOURCES.md](MATCHUP-DATA-SOURCES.md).

## Floating widget

The matchup data update changes the bundled hero catalogue. It does not require changes to the floating widget UI or overlay lifecycle.
