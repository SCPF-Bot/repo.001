# Matchup and Synergy Data

The bundled `app/src/main/assets/heroes.json` matchup lists were refreshed on **2026-11-21** for **Patch 2.2.23**, using the **all-ranks / past-1-day** slice from [MLBB Meta Analyser](https://lnsdeep.github.io/MLBB-Meta-Analyser/). Its published CSV contains 3,990 rows across hero, rank, and timeframe slices, with up to five counter and five teammate-synergy entries per hero. The project README says its statistics are extracted from Moonton's Game Management System and refreshed daily. This is a third-party project, not a Moonton-operated service.

## Field meanings

- `counters`: the source's favorable opponents for the selected hero — heroes the selected hero performs well against. This direction was cross-checked against MLBBHub's per-hero **“Heroes X Is Strong Against”** section. Up to five entries are included.
- `bestPartners` / `synergies`: the source's top measured same-team synergy picks for the selected hero. Up to five entries are included.
- `counteredBy`: toughest opponents for the selected hero. The primary method reverses the source's favorable-counter lists: if hero A appears in hero B's favorable list, B is recorded as a tough matchup for A. Reverse edges are ranked by weighted appearances (5 points for a source rank-1 appearance down to 1 point for rank 5), retaining up to five.
- **Fallback:** for 43 heroes with fewer than three reverse edges, `counteredBy` uses the top three direct ranked counter picks from that hero's MLBBHub page. Those pages were updated between **November 17 and November 20, 2026**. Each hero records whether its toughest-matchup list came from the reverse index or the direct MLBBHub fallback in `counteredBySource` / `counteredByUpdated`.

## Coverage and caveats

All **133 of 133 heroes** have sourced entries in all three categories. Favorable-matchup and best-ally lists contain four or five entries where the source included an unresolved placeholder; placeholders such as `Hero #133` were excluded rather than guessed. Toughest-matchup lists contain three to five entries.

The main snapshot is all ranks over the past day, not a rank-specific recommendation. Results can shift with the patch and sample window. Matchup and synergy edges are statistical associations, not guarantees; player skill, lane, draft, bans, and team composition still matter. The reverse-index method is an aggregation of source-ranked recommendations, not a separately published per-pair score.

## Sources

- [MLBB Meta Analyser CSV export](https://github.com/lnsdeep/MLBB-Meta-Analyser/blob/main/data/exports/mlbb_meta_latest.csv) — primary favorable-matchup and measured-ally data.
- [MLBB Meta Analyser project and methodology](https://github.com/lnsdeep/MLBB-Meta-Analyser) — source description and refresh method.
- [MLBBHub counter picker](https://mlbbhub.com/counter) — direct ranked-counter fallback and direction cross-check.
- [MLCounters hero matchup pages](https://www.mlcounters.app/) — secondary cross-check for hero-specific counters and teammate relationships.
- [MLBB.gg counter pages](https://mlbb.gg/counter/atlas) and [MLBBMeta counters](https://mlbbmeta.com/counters/) — additional matchup references.

Mobile Legends: Bang Bang and its game data remain the property of their respective rights holders. This project is not affiliated with or endorsed by Moonton.
