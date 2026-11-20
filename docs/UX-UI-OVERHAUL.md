# MLBB Plus UX / UI overhaul

This refresh improves the app screens while intentionally preserving the floating overlay implementation.

## What changed

- Added a short Quick Start guide on Home with plain-language steps for choosing a position, selecting a playstyle, and enabling the floating guide.
- Reworded setup and guide controls using clearer labels such as Position, Playstyle, Enable Widget, and Display over other apps.
- Replaced the misleading overlay-permission switch with a direct Android Settings action and a clear explanation of the permission.
- Made the startup preference functional: the app now remembers the last selected position and playstyle and can start the guide when the app opens, provided overlay permission is granted.
- Added proper system Back handling for detail pages and section navigation.
- Preserved list search, filters, sort choices, and the selected patch tab while navigating between sections.
- Added sorting options to hero and item explorers, clear-filter actions, result counts, and helpful empty states.
- Added a responsive navigation rail for wider windows while retaining bottom navigation on phones.
- Limited the Home content width on larger screens for easier reading.
- Grouped technical data-file diagnostics behind an expandable troubleshooting section.
- Used Compose toggle semantics for the startup setting and kept interactive controls at accessible minimum sizes where practical.

## Floating overlay preservation

The following overlay-related files were not changed in this refresh:

- `app/src/main/java/com/mlbb/plus/ui/WidgetOverlayService.kt`
- `app/src/main/java/com/mlbb/plus/ui/widgets/JetOverlay.kt`
- `app/src/main/java/com/mlbb/plus/ui/widgets/OverlayWidgets.kt`
- `app/src/main/java/com/mlbb/plus/ui/theme/MLBBPlusTheme.kt`

## Design references

- Android adaptive layouts: https://developer.android.com/design/ui/mobile/guides/layout-and-content/adapt-layout
- Android accessibility for Compose: https://developer.android.com/guide/topics/ui/accessibility/composables
- Compose accessibility semantics: https://developer.android.com/develop/ui/compose/accessibility/semantics
- Android minimum touch targets: https://developer.android.com/guide/topics/ui/accessibility/apps
- Nielsen Norman Group usability heuristics: https://www.nngroup.com/articles/ten-usability-heuristics/
