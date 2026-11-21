#!/usr/bin/env python3
"""Validate bundled MLBB Plus JSON references and image assets."""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
errors: list[str] = []


def load_json(name: str):
    path = ASSETS / name
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001 - validator reports malformed source files
        errors.append(f"{name}: cannot parse JSON ({exc})")
        return None


items_root = load_json("items.json")
heroes_root = load_json("heroes.json")

if isinstance(items_root, dict):
    items = items_root.get("items", [])
    if not isinstance(items, list):
        errors.append("items.json: 'items' must be an array")
        items = []
    ids = [item.get("id") for item in items if isinstance(item, dict)]
    if len(ids) != len(set(ids)):
        errors.append("items.json: duplicate item IDs")
    declared_total = items_root.get("totalItems")
    if declared_total is not None and declared_total != len(items):
        errors.append(f"items.json: totalItems={declared_total}, actual={len(items)}")
    active = [item for item in items if item.get("status", "active") == "active"]
    declared_active = items_root.get("totalActiveItems")
    if declared_active is not None and declared_active != len(active):
        errors.append(f"items.json: totalActiveItems={declared_active}, actual={len(active)}")
    for item in items:
        if not isinstance(item, dict):
            continue
        image = item.get("image")
        if image:
            path = ASSETS / image
            if not path.is_file():
                errors.append(f"item {item.get('id')} ({item.get('name')}): missing portrait {image}")
else:
    items = []

if isinstance(heroes_root, dict):
    heroes = heroes_root.get("heroes", [])
    if not isinstance(heroes, list):
        errors.append("heroes.json: 'heroes' must be an array")
        heroes = []
    ids = [hero.get("id") for hero in heroes if isinstance(hero, dict)]
    if len(ids) != len(set(ids)):
        errors.append("heroes.json: duplicate hero IDs")
    for hero in heroes:
        if not isinstance(hero, dict):
            continue
        image = hero.get("image") or hero.get("portrait")
        if image:
            path = ASSETS / image
            if not path.is_file():
                errors.append(f"hero {hero.get('id')} ({hero.get('name')}): missing portrait {image}")
else:
    heroes = []

for role in ("roam", "jungle", "mid", "gold", "exp"):
    guide = load_json(f"{role}_guide.json")
    if guide is not None and not isinstance(guide, dict):
        errors.append(f"{role}_guide.json: root must be an object")

load_json("patch_notes.json")

if errors:
    print("Asset validation failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

active_count = sum(1 for item in items if isinstance(item, dict) and item.get("status", "active") == "active")
print(f"Asset validation passed: {len(heroes)} heroes, {len(items)} items ({active_count} active), all declared portraits present.")
