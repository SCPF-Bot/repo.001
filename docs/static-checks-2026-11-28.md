# Static checks, 2026-11-28

Gradle builds and unit tests were **not run** in this pass. The sandbox ran out of memory during
the Gradle build, so the checks below are static only. Nothing from the uncommitted changes has
been compiled since Build 14.

## Kotlin lint: ktlint 1.8.0

Command (run from the repo root, heap capped to fit the sandbox):

```
JAVA_OPTS="-Xmx768m" ktlint --relative "app/src/**/*.kt"
```

The repo has no `.editorconfig`, so ktlint used its default `ktlint_official` style. The codebase
was written to a different convention, so most findings are formatting, not defects.

Result: 2,408 findings across 2,295 main and 113 test lines (down from 2,414 after the fixes below).

| Rule group | Count | Verdict |
|---|---|---|
| trailing-comma, argument/function-signature, multiline-expression wrapping, chain continuation | ~1,900 | Style only. Not fixed. |
| function-naming (Compose `@Composable` functions in PascalCase) | 62 | False positive for Compose. |
| max-line-length (>140) | 40 | Style. Not fixed, except in lines touched this pass. |
| **no-unused-imports** | **0** | Clean. |
| **no-trailing-spaces** | fixed (1) | `OverlayWidgets.kt`. |
| **no-blank-line-before-rbrace** | fixed (1) | `RoamGuideWidget`. |
| long string lines in the overlay header | fixed | `objectiveDescription`, `lastSlainAt` text. |

Remaining genuine-looking items (not fixed; they do not change behaviour):
- `JetOverlay.kt:51`: an EOL comment directly after a KDoc block (`no-consecutive-comments`).
- `SherpaOnnxVoiceEngine.kt:220`: a redundant `${}` in a string template.
- `OverlayWidgets.kt:1111` and `636`: indentation of continuation lines.

Decision for the owner: add an `.editorconfig` that matches the house style, or keep the default.
Turning the style rules off would cut the report to the handful of real findings above.

## Edits made in this pass (uncompiled)

- `VoiceGuideManager.interruptFor()`: an audio-focus refusal now logs a warning, shows
  "Android refused audio focus, so nothing was spoken." in the overlay status line and in
  Settings, clears the speaking key, and schedules the idle release of the loaded engine.
- `OverlayWidgets.RoamGuideWidget`: the timer, role selector, objective, mode, help, collapse,
  close controls and the five-cell spell tracker now sit in one `overlay_header` column with a 2 dp
  gap. The card no longer has a separate spell-tracker block.
- `JetOverlay.show()`: logs the screen width and the computed guide card width, for diagnosing the
  70% width on a device.
- Stale comment: the 52% width note in `OverlayWidgets.kt` now describes the current 70% width.

## Finding: release APK size (explained)

The release APK fell from 133.7 MB (Build 13) to 37.9 MB (Build 14). The cause is the uncommitted
`abiFilters += listOf("arm64-v8a")` in `app/build.gradle.kts`, under `release`. It is not in HEAD.
The file's comment says it saves about 90 MB. The APK now has five `lib/arm64-v8a/` entries.

Consequence: on 32-bit ARM and x86 phones the neural voices cannot load. The code catches the
`UnsatisfiedLinkError` and falls back to the Android system voice. Those devices hear the system
voice only.

Decision needed: keep arm64-only (smaller), or add `armeabi-v7a` for 32-bit phones (about 90 MB
more).

## Later pass: two-row guide card

Changed files: `OverlayWidgets.kt` (RoamGuideWidget rewritten; VoiceControlRow, GuideDetail and the
abbreviation table removed), `FloatingGuide.kt` (no `onCollapse`), `GuideCardLayoutTest.kt`
(rewritten for the new behaviour), `docs/overview.md`, `docs/tts_implementation.md`,
`docs/structure.md`.

- Row 1 (header): timer | role and plan (`E4`) | turtle or lord timer (`T 00:23`, `T UP`) | `+`/`−`
  (guide) | `On`/`Off` (voice, only when a voice manager is attached) | `x` (exit, stops the service) |
  `>`/`<` (spell tracker).
- Spell tracker: hidden by default. Open, the card widens to 70% of the screen width (`landscapeWidgetWidthDp`);
  hidden, it is 360 dp. The tracker stays composed while hidden, so cooldowns survive the toggle.
  Cells show one lane letter (E J M G R); while cooling down they show seconds left.
- Row 2 (guide): the current step, plus −10s / +10s / Reset / Undo slain while the guide is open.
- No voice transport controls. Auto-speak (`LaunchedEffect` on the step key) is unchanged.

Removed from the widget, per the new layout: the `?` help menu, the three-state detail cycle (the
checklist, vision, decision and avoid details), the ▴ fold-to-bubble button, and the voice row
(Speak, Pause, Resume, Repeat, Prev, Next, Stop). Rotating to portrait still folds the widget to the bubble.

Checks: ktlint 1.8.0 on the changed files. No parse errors and no unused imports. Remaining findings
are the style rules noted above, plus two existing multi-space alignments in the constants block. The
Gradle build and tests were not run, so none of this code is compiled. `GuideCardLayoutTest` asserts
widths (360, 560 and 700 dp) and a folded height of at most 40 dp; the height figure is an estimate.
