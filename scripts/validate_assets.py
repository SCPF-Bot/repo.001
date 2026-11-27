#!/usr/bin/env python3
"""Validate bundled MLBB Plus JSON assets and portrait paths.

Checks (offline, no network):
  - JSON syntax for heroes/items/patch_notes + five guide files
  - Unique hero/item IDs; declared totals match actual counts
  - Every local image path resolves to a bundled file
  - Matchup arrays: no self-links, no duplicates, all IDs resolve
  - Guide hero picks resolve to heroes.json by name
  - Patch-change IDs cross-checked against the hero/item rosters by name
    (a wrong ID makes the Patch screen link to the wrong entity)
  - heroes/items/patch_notes versions agree on the patch number
  - Warns when a guide declares meta.latest=true but is pinned to an older patch

Run: python3 scripts/validate_assets.py
Exit 0 = pass, 1 = fail (prints each failure; warnings never fail the run).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "assets"
failures = []
warnings = []


def check(cond, msg):
    if not cond:
        failures.append(msg)
        print(f"FAIL: {msg}")


def load(name):
    p = ASSETS / name
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        check(False, f"{name}: invalid JSON ({e})")
        return {}


heroes_doc = load("heroes.json")
items_doc = load("items.json")
patch_doc = load("patch_notes.json")
heroes = heroes_doc.get("heroes", [])
items = items_doc.get("items", [])

# Counts vs declared totals
check(len(heroes) == heroes_doc.get("totalHeroes", len(heroes)),
      f"heroes.json: totalHeroes={heroes_doc.get('totalHeroes')} != {len(heroes)} records")
check(len(items) == items_doc.get("totalItems", len(items)),
      f"items.json: totalItems={items_doc.get('totalItems')} != {len(items)} records")
active = [i for i in items if i.get("status") == "active"]
check(len(active) == items_doc.get("totalActiveItems", len(active)),
      f"items.json: totalActiveItems={items_doc.get('totalActiveItems')} != {len(active)} active")
print(f"heroes: {len(heroes)} (version {heroes_doc.get('version')}, updated {heroes_doc.get('updated')})")
print(f"items: {len(items)} total, {len(active)} active (version {items_doc.get('version')}, updated {items_doc.get('updated')})")
print(f"patch_notes: version {patch_doc.get('version')}, date {patch_doc.get('date')}")

# Unique IDs
hero_ids = [h.get("id") for h in heroes]
check(len(hero_ids) == len(set(hero_ids)), "heroes.json: duplicate hero IDs")
item_ids = [i.get("id") for i in items]
check(len(item_ids) == len(set(item_ids)), "items.json: duplicate item IDs")
hero_id_set = set(hero_ids)

# Image paths resolve (local paths only; http(s) skipped)
def check_images(records, kind):
    missing = []
    for r in records:
        for key in ("image", "portrait"):
            p = (r.get(key) or "").strip()
            if not p or p.startswith(("http://", "https://", "file://", "content://")):
                continue
            if not (ASSETS / p).exists():
                missing.append(f"{r.get('name')} -> {p}")
    check(not missing, f"{kind}: {len(missing)} missing portraits e.g. {missing[:3]}")
    print(f"{kind} portraits: all local paths resolve" if not missing else f"{kind} portraits: {len(missing)} missing")

check_images(heroes, "heroes")
check_images(items, "items")

# Matchup integrity: no self-links, no dups, all resolve
for h in heroes:
    hid = h.get("id")
    for f in ("goodAgainst", "badAgainst", "goodWith", "counters", "counteredBy", "synergies", "bestPartners"):
        arr = h.get(f) or []
        check(hid not in arr, f"{h.get('name')}: self-link in {f}")
        check(len(arr) == len(set(arr)), f"{h.get('name')}: duplicate IDs in {f}")
        for x in arr:
            check(x in hero_id_set, f"{h.get('name')}: unresolved ID {x} in {f}")
print("matchup arrays: no self-links/dups/unresolved (checked)")

# Guides: types, steps, picks resolve
hero_names = {h.get("name", "").lower() for h in heroes}
hero_name_to_id = {h.get("name", "").lower(): h.get("id") for h in heroes}
item_name_to_id = {i.get("name", "").lower(): i.get("id") for i in items}
total_types = total_steps = 0
for role in ("roam", "jungle", "mid", "gold", "exp"):
    name = f"{role}_guide.json"
    doc = load(name)
    meta = doc.get("meta", {})
    roles = doc.get("roles", [])
    steps = sum(len(r.get("steps", [])) for r in roles)
    total_types += len(roles)
    total_steps += steps
    print(f"{name}: patch {meta.get('patch')}, updated {meta.get('updated')}, types {len(roles)}, steps {steps}")
    for r in roles:
        for hp in r.get("heroes", []):
            pname = hp.get("name", "") if isinstance(hp, dict) else str(hp)
            check(pname.lower() in hero_names, f"{name}: unlinked pick '{pname}'")
    # A guide that claims latest=true while pinned to an older patch hides real staleness
    # in Settings (badge) and misleads the overlay label. Warn, do not fail: refreshing
    # guide content is a data task, not a broken reference.
    live = str(patch_doc.get("version", ""))
    gpatch = str(meta.get("patch", ""))
    if meta.get("latest") and gpatch and gpatch != live:
        warnings.append(f"{name}: meta.latest=true but patch={gpatch} != live patch {live}")
print(f"guides total: {total_types} types, {total_steps} steps")

# Cross-file referential integrity for patch changes.
# The Patch screen links each change row by ID, so a wrong ID opens the wrong hero/item.
for c in patch_doc.get("heroChanges", []):
    cid, cname = c.get("heroId"), (c.get("heroName") or "")
    expected = hero_name_to_id.get(cname.lower())
    check(expected is not None, f"patch_notes.json: heroChange '{cname}' not in heroes.json roster")
    check(expected == cid,
          f"patch_notes.json: heroChange '{cname}' heroId={cid} but heroes.json says {expected} "
          f"(id {cid} is '{[h.get('name') for h in heroes if h.get('id') == cid]}')")
for c in patch_doc.get("itemChanges", []):
    cid, cname = c.get("itemId"), (c.get("itemName") or "")
    expected = item_name_to_id.get(cname.lower())
    check(expected is not None, f"patch_notes.json: itemChange '{cname}' not in items.json catalogue")
    check(expected == cid, f"patch_notes.json: itemChange '{cname}' itemId={cid} but items.json says {expected}")
for c in heroes_doc.get("patchChanges", []):
    cid, cname = c.get("id"), (c.get("hero") or "")
    expected = hero_name_to_id.get(cname.lower())
    check(expected == cid, f"heroes.json: patchChanges '{cname}' id={cid} but roster says {expected}")
print(f"patch change IDs: cross-checked vs rosters "
      f"({len(patch_doc.get('heroChanges', []))} + {len(heroes_doc.get('patchChanges', []))} hero, "
      f"{len(patch_doc.get('itemChanges', []))} item)")

# Version consistency: heroes.json is the authoritative patch manifest, so its version
# must start with the same patch number as patch_notes.json (it may add a data stamp).
hv = str(heroes_doc.get("version", ""))
pv = str(patch_doc.get("version", ""))
check(hv.split("-")[0] == pv.split("-")[0],
      f"version mismatch: heroes.json '{hv}' vs patch_notes.json '{pv}'")
check(str(items_doc.get("version", "")).split("-")[0] == pv.split("-")[0],
      f"version mismatch: items.json '{items_doc.get('version')}' vs patch_notes.json '{pv}'")

for w in warnings:
    print(f"WARN: {w}")

if failures:
    print(f"\n{len(failures)} failure(s), {len(warnings)} warning(s)")
    sys.exit(1)
print(f"\nAll checks passed ({len(warnings)} warning(s)).")
