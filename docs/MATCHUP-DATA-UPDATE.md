# Matchup and synergy data update — 2026-11-21

## What changed

- Removed the repeated placeholder `counters`, `counteredBy`, `synergies`, and `bestPartners` arrays that had been assigned across most of the 133-hero roster.
- Added sourced all-rank, seven-day matchup and same-team ally associations for Hayabusa, Saber, Lancelot, Hanabi, and Eudora, using the 2026-11-16 snapshot.
- Left other heroes' relationship arrays and matchup notes empty until their matchup records can be sourced. This intentionally prefers missing data over fabricated or repeated recommendations.
- Updated the Hero detail UI to say **Matchups**, distinguish **Toughest Matchups** from **Favorable Matchups**, and label synergies as **Best Measured Allies**. The UI shows source/date/scope and explains that win-rate shifts are trends, not guarantees.

## Source and limitations

The matchup snapshot comes from MLBB Core, an independent fan project that reports using Moonton's GMS ranked matchup matrix. Its pages describe a seven-day window updated 2026-11-16. The source itself notes that Moonton does not document the matchup shift's sign convention, does not publish per-pair game counts, and that the data is a single-source feed. Same-team synergy is a measured win-rate association, not proof that a pair has a special kit combo. Treat these lists as draft signals, not guaranteed counters or causal recommendations.

- Matchup overview: https://www.mlbbcore.com/counters/
- Synergy overview: https://www.mlbbcore.com/synergies/
- Hayabusa: https://www.mlbbcore.com/heroes/hayabusa/counters/ and https://www.mlbbcore.com/heroes/hayabusa/synergies/
- Saber: https://www.mlbbcore.com/heroes/saber/counters/ and https://www.mlbbcore.com/heroes/saber/synergies/
- Lancelot: https://www.mlbbcore.com/heroes/lancelot/counters/ and https://www.mlbbcore.com/heroes/lancelot/synergies/
- Hanabi: https://www.mlbbcore.com/heroes/hanabi/counters/ and https://www.mlbbcore.com/heroes/hanabi/synergies/
- Eudora: https://www.mlbbcore.com/heroes/eudora/counters/ and https://www.mlbbcore.com/heroes/eudora/synergies/

## Floating widget

No floating-widget source files or UI behavior were changed.
