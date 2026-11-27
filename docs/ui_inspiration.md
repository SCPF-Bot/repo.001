# UI Design Inspiration & Rationale — MLBB Plus

This document records the Step 5 visual/interaction research conducted before overhauling **MLBB Plus**, ensuring modern Material Design 3 ergonomics, adaptive layouts, and high-contrast gameplay readability while strictly preserving the brand's **Gold + Ink Navy (dark) / Warm Cream (light)** visual identity and system Sans-Serif typography (with Monospace reserved strictly for numeric timers and cooldowns in the in-match HUD).

---

## 1. Home Screen (Guide Setup & Overlay Launcher)
- **References**:
  - **Material Design 3 "Now in Android" & Settings/Control Center cards** (tonal container hierarchy, exposed/anchored selection controls, bottom-pinned primary CTA).
  - **Mobbin — Strategy Companion & Gaming Utility Dashboards** (single-focus pre-match configuration card, concise status badges, hero pick pills with circular avatars).
  - **Google Play Store / Android Adaptive Layout Showcase** (max-width centered content column on tablets/foldables, clear section rhythm).
- **Borrowed Patterns**:
  - Primary setup card elevated above reference details, using Material 3 `ExposedDropdownMenuBox`-style 48dp+ touch targets for **Position** and **Playstyle**.
  - Semantic **Win** / **Avoid** callout rows (icon + tonal badge + label, never color alone) and horizontally scrollable clickable hero pick cards linking straight to Hero Detail.
  - Collapsed-by-default expandable **Mechanics & Rules** card using smooth height animation (`animateContentSize`) and rotating chevron indicator.
  - Persistent insets-aware bottom primary CTA button (`Launch Widget` / `Enable Widget`) rather than an overlapping bottom-end pill that obscures scrolling content.
- **Rejected Patterns**:
  - Multi-widget telemetry grids with neon gradients or decorative sparklines that fight the core "Position → Playstyle → Launch" job-to-be-done.
  - iOS-style bottom picker wheels or hairline flat boxes lacking touch-target padding.
- **Theme Preservation**:
  - Uses `MaterialTheme.colorScheme.surfaceContainer` cards, `primary` gold (`#FFD45A` dark / `#7A5A00` light) accent edge/CTA, and extended semantic colors (`success`, `warning`, `info`, `error`).

---

## 2. Heroes & Items Explorers + Detail Pages
- **References**:
  - **Mobbin — Pokedex / Esports Roster & Catalog Grids** (persistent full-width search bar, horizontal filter chips, clear result count + sort control, clean portrait tiles with corner tier badges).
  - **Material Design 3 Search + Filter Chips + Primary Tabs** (`OutlinedTextField` search bar, `FilterChip` strips with 48dp touch targets, `PrimaryTabRow` with sliding active indicator).
  - **Dribbble / Behance — RPG Item Crafting & Build Path Trees** (visual component → target item flow and hero cross-link cards).
- **Borrowed Patterns**:
  - Dedicated full-width `AppSearchBar` above horizontally scrollable `FilterChip`s (Role, Tier, Category) and an accessible Sort menu + one-tap `Clear` action when filters are active, paired with a designed `EmptyState` when 0 items match.
  - Responsive `GridCells.Adaptive(minSize = 72.dp)` grid that naturally scales from compact phones (4–5 columns) to tablets/foldables without hardcoding column counts, upgrading grid labels and prices from unreadable 8–10sp to ≥11–12sp (`labelSmall` / `labelMedium`).
  - Detail screens with `AppTopBar` back navigation, hero/item summary header card, M3 `PrimaryTabRow` (`Overview` / `Counters` / `Synergies` and `Overview` / `Build Path` / `Hero Links`), and elevated cards for lore, stats, core builds, and tappable cross-links.
- **Rejected Patterns**:
  - Cramped single-row horizontal scrollbars mixing tiny icon-only search boxes with 32dp custom dropdowns (`OneBarFilter` / `CompactDropdown`).
  - Raw unstyled text lists for stats and missing data states; replaced with `AppCard` containers and structured `EmptyState`s.
- **Theme Preservation**:
  - Role-tinted fallback portraits, gold tier highlights for `S+`/`S` tiers, and `MaterialTheme.colorScheme` surfaces across both light and dark modes.

---

## 3. Patch Notes Screen
- **References**:
  - **Material Design 3 Release Notes / Changelog Feeds** (segmented server switcher, clear version/season subtitle, scannable change cards with semantic status pills).
  - **Page Flows / Screenlane — Competitive Game Balance Changelogs** (Buff / Nerf / Revamp / Adjust pills with icon + color + text, tappable hero/item rows).
- **Borrowed Patterns**:
  - Clean `AppTopBar` + version/season header, M3 segmented/tab server switch (`Current`, `Advanced`, `Nov 01` when populated), semantic Buff/Nerf/Revamp/Adjust/Reversion pills (leading icon + color + uppercase label), and tappable hero/item cards that navigate directly to their detail routes.
  - Structured **Meta heroes by lane** and **Overlay guide coverage** cards (explicitly highlighting stale guides such as `roam_guide.json` at `2.2.16a` vs `2.2.23`).
- **Rejected Patterns**:
  - Unstructured wall-of-text paragraphs or color-only 8dp dots next to hero titles (which fail color-blind accessibility).
- **Theme Preservation**:
  - Uses `LocalAppExtendedColors` semantic patch tokens mapped cleanly for both Dark (bright emerald/crimson/gold on `#151B27`) and Light (WCAG-compliant deep emerald/crimson/amber on `#FFFFFF` / `#F0EDE4`).

---

## 4. Settings & Knowledge Base
- **References**:
  - **Material Design 3 Android Settings & Two-Pane List-Detail Canonical Layout** (grouped tonal cards, full-row switch hit targets, `>=840dp` master-detail Knowledge Base reader).
  - **Mobbin — In-App Voice & Storage Management Panels** (visual storage chip list, linear download/unpack progress bar, test playback action).
- **Borrowed Patterns**:
  - Grouped M3 cards for **Floating Guide Permission**, **Startup Auto-Launch**, **Voice Guide** (master toggle, voice speaker chips, speed chips, offline/system engine selector, on-device model rows with storage size & delete, install/test buttons), **Help & Knowledge Base**, and expandable **Data Status & Diagnostics**.
  - Adaptive **Knowledge Base**: single-column topic list → article reader on phones (`<840dp`) and side-by-side master-detail two-pane layout on tablets/foldables (`>=840dp`), with structured reference tables, "Common Mistakes" warning cards, "Using this in MLBB Plus" tip cards, related-topic chips, and Previous/Next navigation.
- **Rejected Patterns**:
  - Hidden nested sub-state inside `SettingsScreen` without a proper top-level route or clear back-bar hierarchy, and ungrouped flat dividers.
- **Theme Preservation**:
  - Preserves exact `SharedPreferences` keys (`auto_start_last_role`), `VoiceGuideSettings` persistence, and gold-accented active selection chips in both Light and Dark themes.

---

## 5. Floating In-Match Overlay HUD (`OverlayWidgets.kt`)
- **References**:
  - **Mobile MOBA / Esports Live Broadcast HUDs** (ultra-compact translucent dark pill containers, clear numeric countdown typography, zero-distraction idle dimming).
  - **Material Design 3 Compact Chips & Progress Bars** (inline progress fill inside timer pill, slim bottom cooldown progress indicator on spell cells).
- **Borrowed Patterns**:
  - Keeps `MLBBPlusTheme` dark translucent high-contrast palette (`0.6f` idle alpha, `displayMetrics` 52% landscape / 92% portrait width) so it remains readable over any bright or dark gameplay frame regardless of system light/dark mode.
  - Switches prose/step instructions to `FontFamily.SansSerif` for crisp mid-match scanning while keeping `FontFamily.Monospace` strictly for numeric match clock and spell cooldown timers.
  - Refines timer pill, role/type selector badge, urgent-only Gold/Red objective button, 3-state expand/collapse/hide controls, 48dp mid-fight `VoiceControlRow`, legible `>=11sp` help menu tips, and 5-lane `EnemySpellTrackerWidget` cells with cleaner hierarchy and bottom progress bars.
- **Rejected Patterns**:
  - Light-mode or wallpaper-dynamic overlay backgrounds (would glare/wash out over gameplay) and opaque heavy cards that block minimap/lane vision.
