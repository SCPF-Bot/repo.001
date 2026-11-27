# Voice Guide (floating TTS coach) — implementation record

Written 2026-11-27 against the spec in `uploads/TTS-floating-widget-prompt.md` (§1–§8).
Everything below was built and verified in a sandbox **without an Android device or SDK**, so
§9 separates what is already proven from what still needs a phone.

---

## 1. What shipped

A spoken coach inside the existing floating guide overlay. It reads the step the player is on —
condensed to something a human would say out loud — and can auto-speak each new step as the match
timer advances.

| | |
|---|---|
| Default state | **OFF** (`enabled = false`). With it off the overlay composes exactly what it did before: no extra row, no engine, no audio focus, no model on disk. |
| Where it appears | One 48dp control row at the bottom of the guide overlay (Speak/Pause/Resume · Repeat · Prev · Next · Stop) plus a single status line for progress, fallback notices and errors. |
| Notification | Two extra actions while the feature is on: **Play/Pause voice** and **Stop voice**, reusing the existing foreground-service notification (no second service). |
| Settings | `Settings → Voice Guide`: engine (Auto / On-device neural / System), voice, speed 0.85×–1.25×, auto-speak, announce enemy spells, install voice (bundled size + progress + cancel), play a test line. |
| Knowledge Base | New entry `voice-guide` — "Voice Guide: Hear Your Next Play" (19 entries total). |
| Storage | Voice packs are **compiled into the APK** (`assets/tts/`, staged at build time by the `bundleTtsModels` Gradle task from `app/tts-models-cache/`) and unpacked into `filesDir/tts/` on first use. No network is needed at runtime; the archives are never committed to git. |

---

## 2. Engine choice

**sherpa-onnx 1.13.8, pinned** — on-device neural TTS, no cloud API, no runtime synthesis over the
network, no API key.

| Layer | Engine | Notes |
|---|---|---|
| Preferred | `SherpaOnnxVoiceEngine` — Kokoro int8 | 24 kHz, 4 threads, 11 speakers. Most natural. |
| Fallback 1 | `SherpaOnnxVoiceEngine` — Piper VITS (lessac / ryan) | 22.05 kHz, 2 threads, 1 speaker. ~5× smaller, faster on low-end hardware. |
| Fallback 2 | `SystemTtsVoiceEngine` — Android `TextToSpeech` | Always present, zero setup. Used when no model is installed or a model fails to load. |

`resolveEngine()` implements that chain: a player who explicitly picks **System** gets it
immediately; **Auto** uses the first installed model and falls through to System on load failure;
**On-device neural** refuses to fall back and instead surfaces a one-line reason
("voice model missing" / "voice model failed to load") so silence is never unexplained.

**Why 1.13.8 and not the current 2.0.3:** on 2.0.x the Piper voices fail to initialise
(`phoneme_codepoints is empty`) and Kokoro additionally requires a `lexicon-en-us.txt` that the
1.x archives do not need. Both Piper and Kokoro were verified working on 1.13.8 by synthesising
real WAVs off-device. **Do not upgrade without shipping the lexicon and dropping Piper.**

Speaker identity: `voices.bin` carries **no speaker names**, so the spec's `af_heart` cannot be
resolved by name. Speaker ids were derived from a median-F0 sweep (165 Hz cut) and matched against
kokoro-onnx alphabetical order: **sid 1 = `af_heart`** (female), **sid 5 = `am_adam`** (male);
female sids {0,1,3,4,7,8}, male sids {2,5,6,9,10}. Piper archives always use **sid 0**. The
user-facing ids stay the names (`af_heart`, `lessac-medium`, …) and are mapped internally.

---

## 3. Voice models — pins, bundling, checksums, storage

Base URL: `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/` (a permanent
release tag, not a moving branch). The archives are **compiled into the APK**: the `bundleTtsModels`
Gradle task downloads them into `app/tts-models-cache/` (gitignored) at build time, stages them into
`assets/tts/`, and the app unpacks them into `filesDir/tts/` on first use — speech needs no network
at all. Checksums are the official ones from that tag's `checksum.txt` and are re-verified twice:
at build time by `bundleTtsModels`, and again in-app while streaming (`DigestInputStream`), so a
corrupt or truncated pack is rejected rather than installed.

The catalogue lives in `app/src/main/assets/tts/tts-models.json` — the single source of truth read
by the app, by Gradle and by `scripts/update_tts_models.py` (the only writer). Pins (id, sha256,
layout, speaker map) live in that script; adopting a new upstream version is a script-table edit
plus one run. The `.github/workflows/update-tts-models.yml` workflow runs the script weekly and on
dispatch, commits a refreshed manifest, and chains `Build Release` (see §4 and
`.github/workflows/release.yml`).

| Voice | File | sha256 | Archive | Unpacked |
|---|---|---|---|---|
| Kokoro (most natural) | `kokoro-int8-en-v0_19.tar.bz2` | `c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd` | 103,248,205 B | ~157.9 MB (398 entries; `model.int8.onnx` 134.2 MB, `voices.bin` 5.8 MB, `tokens.txt`, 392 `espeak-ng-data/`) |
| Piper female | `vits-piper-en_US-lessac-medium-int8.tar.bz2` | `f1c6d0295cf16087b05f80fdca5b44daca5cd78e2c425d419a42ba34929805f9` | 20,969,179 B | ~36.6 MB (397 entries; `.onnx` 18.6 MB) |
| Piper male | `vits-piper-en_US-ryan-medium-int8.tar.bz2` | `376eb489d42e98cb49f0e13a633e8d88580bd247ed8aacc298a733210404f771` | 21,083,446 B | ~36.6 MB |

Storage layout, all under `filesDir/tts/`:

```
filesDir/tts/<dirName>/                      installed model
                    /.mlbb-voice-installed    marker written last, only after verification
                    /<dirName>.partial-<nanoTime>/   extraction scratch, renamed atomically
```

Extraction streams `BZip2CompressorInputStream → TarArchiveInputStream` (commons-compress) — no
full archive is ever buffered, and entries are path-checked (`safeChild`) against zip-slip. The
`.partial-*` directory is renamed into place only after the checksum matched, and stale partials
from an interrupted install are cleaned up on the next attempt. The UI shows the size from
`model.sizeBytes`, never a hardcoded number (the spec's "82 MB" figure is wrong for all three).

`.gitignore` covers `app/libs/*.aar`, `app/tts-models-cache/` and the model binaries (`*.onnx`,
`*.tar.bz2`, `voices.bin`, `espeak-ng-data/`); no model binary is committed. They do not need to
be: the manifests pins plus the update script reproduce every byte, and GitHub blocks per-file
uploads over 100 MB anyway (the Kokoro archive alone is 103 MB).

---

## 4. Dependency provisioning

sherpa-onnx is **not** on Google Maven and JitPack has no builds for it (all 404), so the AAR is
vendored:

- `gradle/libs.versions.toml` — `commonsCompress = "1.25.0"` (1.26+ pulls commons-io/lang3),
  `junit = "4.13.2"`.
- `app/build.gradle.kts` — `implementation(files("libs/sherpa-onnx-1.13.8.aar"))`,
  `libs.commons.compress`, `testImplementation(libs.junit)`, and two provisioning tasks. Both
  capture plain `File`/`URI`/`MessageDigest` references via explicit imports — inside a module
  that applies the Android plugin the bare name `java` resolves to the `java` extension
  (JavaPluginExtension), which is what previously broke the script with "Unresolved reference 'io'".
  `downloadSherpaAar` fetches the AAR from the v1.13.8 release, verifies its sha256
  (`633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`, 50,129,134 B) with
  `MessageDigest`, writes `.part` then renames, and is `onlyIf { !exists() }`. `bundleTtsModels`
  reads `app/src/main/assets/tts/tts-models.json`, fetches/verifies each voice pack into
  `app/tts-models-cache/`, and copies the archives into
  `app/build/generated/tts-assets/tts/` (registered as an extra assets source set), so
  the packs land in `assets/tts/` of every APK. Both are wired as dependencies of `preBuild`
  (`bundleTtsModels` also of `*merge*Assets*`), so a fresh clone builds without a manual step.
  Committing `app/libs/sherpa-onnx-1.13.8.aar` yourself is also fine — the task then does nothing.
- `app/src/main/AndroidManifest.xml` — no network permissions at all: the voice packs ship in the
  APK, so `INTERNET`/`ACCESS_NETWORK_STATE` are not needed (they were removed).
- `testOptions { unitTests.isReturnDefaultValues = true }` so the normalizer/manager tests can run
  on the JVM.
- ABI splits: the AAR ships `arm64-v8a` (31.9 MB), `armeabi-v7a` (22.5 MB), `x86`, `x86_64`. A
  commented-out `splits { abi { } }` block is in place; enabling `abiFilters` for arm64 + armv7 is
  the recommended release-size move and needs a device/CI check first.

---

## 5. Code map

`app/src/main/java/com/mlbb/plus/tts/` (new, 7 files, 3,029 LOC):

| File | Responsibility |
|---|---|
| `TtsTextNormalizer.kt` | Pure JVM. Guide JSON → spoken English: clock phrasing, abbreviation expansion, symbol cleanup, condensation to a word budget, truncation on a sentence/clause boundary. No Android imports. |
| `VoiceGuideSettings.kt` | SharedPreferences `mlbb_plus_voice_guide` (keys `enabled`, `engine`, `voice`, `rate`, `auto_speak`, `announce_enemy_spells`, `installed_model`, `fallback_notice`) plus a `revision` counter that the manager observes. |
| `VoiceEngine.kt` | The engine contract: `prepare`, `speak`, `pause`/`resume` where supported (`supportsPause`), `stop`, `setSpeechRate(newRate)`, `release`. |
| `SystemTtsVoiceEngine.kt` | Android `TextToSpeech` adapter. Initialisation is sequenced on `Dispatchers.Main.immediate` so the status callback cannot race the reference assignment. |
| `VoiceModelInstaller.kt` | Manifest-driven model catalogue (§3, parsed from `assets/tts/tts-models.json`), streaming unpack from the APK bundle + checksum + tar.bz2 extraction + marker file, sealed `Progress` (`Extracting`/`Verifying`/`Done`/`Failed`). |
| `SherpaOnnxVoiceEngine.kt` | Kokoro/Piper config, fallback between installed models, `AudioTrack` MONO PCM_FLOAT playback with `generateWithCallback`. |
| `VoiceGuideManager.kt` | Owns everything the UI touches: `VoiceUiState`, `Phase`, speak/pause/resume/repeat/relative-step/install APIs, audio focus, TalkBack, idle release — and `object VoiceGuideTransitions`, the pure decision table shared with the widget and the tests. |

Wiring into existing files (no new screens, no new service, no DI):

- `ui/widgets/OverlayWidgets.kt` — `VoiceControlRow`, `GuideStep.toSpoken(planId, index)`, the
  auto-speak `LaunchedEffect`, the gold tint on the line being read, spell announcements.
- `ui/WidgetOverlayService.kt` — owns the manager, `ACTION_VOICE_TOGGLE` / `ACTION_VOICE_STOP`,
  `observeVoiceForNotification()`, single `NOTIFICATION_ID`.
- `ui/screens/SettingsScreen.kt` — `VoiceGuideSection` + 5 helpers. The switches are top-level
  siblings of `SectionLabel("VOICE GUIDE")` because `SettingsRow` already draws its own card.
- `ui/model/KnowledgeBase.kt` — the `voice-guide` entry.

**Step identity** is `"$planId#$index#$startSeconds"`, so a recomposition or a 1 Hz clock tick can
never re-trigger auto-speak, while switching plan or role always does.

**Two manager instances exist by design** (Settings screen and overlay service), each refreshing
prefs at construction. `VoiceGuideSettings.revision` + `bump()` keeps them in step — and only
*user-facing* setters bump. `markInstalled` / `clearInstalled` / `setFallbackNotice` must not, or
the manager's own observer would release its engine mid-sentence or cancel its own install.

---

## 6. Text normalisation contract

The overlay already displays the full step; the coach says a **condensed** version. Budgets:
`MIN_WORDS = 8`, `MAX_WORDS = 18`, spell announcements 8 words, objective/don't lines 14.

Rules, in order: strip identifier-shaped tokens (`objective_contest`, camelCase) **first**, then
markdown/bullets, then symbols (`<->` before `->`), then time phrasing, then abbreviation
expansion, then condense/truncate.

| Input | Spoken |
|---|---|
| `startSeconds = 120` | `At 2 minutes. …` (prefix, then the step within the remaining budget) |
| `Use Retri … at 2:00` | `Use Retribution on the enemy jungler at two minutes to steal Turtle.` |
| `AoE from the enemy MM will shred you; keep HP high, MS boots` | `area damage from the enemy marksman will shred you, keep H P high and movement speed boots.` |
| `5v5` | `5 versus 5` |
| `Lv4`, `DPS` | `level 4`, `D P S` |
| `Gold` / `Mid` alone | `gold lane` / `mid lane` — but `Gold tower`, `Mid Game` are left alone (the lookahead is case-insensitive, the match is not) |
| `timeLabel "0:00-0:40"` | dropped — it is a window, not a timestamp; phrasing comes from `startSeconds` |

Verified by running the normalizer over **all 286 real guide steps × 15 fields**: word count
min 9 / max 18 / avg 15.6, nothing over budget, nothing empty, no leaked identifiers, URLs or raw
`M:SS`, and 16 probes are idempotent (`spoken(spoken(x)) == spoken(x)`). Clock prefixes were
checked against all 11 distinct `startSeconds` values in the data (0, 40, 80, 120, 240, 480, 900 …).

`numberWord()` only spells 0–59; larger values stay as digits ("120 seconds"), which both neural
engines and Android TTS read correctly.

---

## 7. State machine and control table

`Phase = IDLE | PREPARING | DOWNLOADING | SPEAKING | PAUSED | ERROR`.

`object VoiceGuideTransitions` (in `VoiceGuideManager.kt`) is the single source of truth for the
button label, the click action and the auto-speak gate — the manager, the widget and the tests all
call it, so the UI cannot drift from the state machine:

| Phase | Label | Action |
|---|---|---|
| downloading | `Cancel` | `CANCEL_DOWNLOAD` |
| preparing | `Loading` | `NONE` (disabled) |
| `SPEAKING` | `Pause` | `PAUSE` (only if the engine `supportsPause`) |
| `PAUSED` | `Resume` | `RESUME` |
| otherwise | `Speak` | `SPEAK` |

`shouldAutoSpeak(enabled, newKey, lastKey, fragmentsChanged)` = enabled **and** the key is not
blank **and** (the key changed **or** the fragments changed). `phaseAfterPause` /
`phaseAfterResume` move only between `SPEAKING` and `PAUSED`.

`spokenLine(fragments, startSeconds)` builds the clock prefix, then spends the *remaining* word
budget on the body, so a late step is not truncated harder than an early one.

---

## 8. Audio focus, TalkBack, lifecycle

- **Audio focus** is `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`: the game is ducked, never paused or
  muted, and focus is abandoned on stop. `AUDIOFOCUS_LOSS` / `LOSS_TRANSIENT` (a phone call, an
  alarm) stops the coach immediately.
- **TalkBack**: a `TouchExplorationStateChangeListener` watches for screen-reader state. When
  TalkBack is on the coach refuses to speak and shows a hint instead, so TTS never fights the
  screen reader. The listener is removed on release.
- **Idle release**: `IDLE_RELEASE_MS = 30_000` — after 30s of silence the engine is released and
  its memory given back; the next Speak re-prepares (a visible `Loading` phase).
- No second foreground service, no database, no analytics, no DI. The existing overlay service owns
  the manager and its notification.

---

## 9. How to test

### Unit tests (already written and passing)

`app/src/test/java/com/mlbb/plus/tts/` — **36 tests**:

- `TtsTextNormalizerTest` (21): clock phrasing, `spokenTime`, ranges and durations, MLBB
  abbreviation expansion, case-sensitive lane names, truncation on a sentence/clause boundary, no
  raw field names / URLs / markdown, idempotency, empty input.
- `VoiceGuideManagerTest` (15): spoken-line construction and word budget, auto-speak once per step,
  no duplicate, re-speak when fragments change, silent when off or when the key is blank, and the
  primary label/action per phase including Pause ↔ Resume.

```bash
./gradlew testDebugUnitTest --tests "com.mlbb.plus.tts.*"
```

The pure decision logic was deliberately extracted (`VoiceGuideTransitions`, and the normalizer
having no Android imports) so both classes test on the JVM with no Robolectric and no device.

### Build

```bash
./gradlew downloadSherpaAar     # optional; assemble* runs it automatically
./gradlew bundleTtsModels       # optional; assemble* runs it automatically
./gradlew assembleDebug assembleRelease
python3 scripts/validate_assets.py
python3 scripts/update_tts_models.py   # refresh/verify the bundled voice packs
```

### Manual device checklist (maps to spec §7 — not yet run)

1. Feature OFF: overlay pixel-identical to before; no audio; layout unchanged at 360dp and in
   landscape.
2. First use: tap Speak with no model → size + progress + cancel → after the unpack, speaks the
   current step in under 2s on a mid-range device.
3. Coach flow: Speak → Next (immediate, and the spoken line is tinted gold) → Repeat → Pause
   mid-sentence → Resume continues cleanly.
4. Auto-speak: advancing to the next step, and Turtle at 2:00, each speak **once**.
5. In-game over MLBB: game audio ducked not muted; overlay Stop and notification Stop both work;
   no crash when backgrounded.
6. Fallback: delete `filesDir/tts/kokoro-int8-en-v0_19` → falls back to Piper or System, shows the
   notice, no crash. Airplane mode with a model installed → still speaks.
7. Persistence: voice, rate and toggles survive restart; auto-start-last-role and auto-start Voice
   Guide both respect a revoked overlay permission.
8. Edge cases: empty guide / missing step / very long step → short fallback, never hangs; rapid
   Next/Prev taps cancel the previous utterance and speak only the latest; incoming call stops it.
9. Accessibility: all controls ≥48dp, speaking state visible, text readable in compact and
   expanded.

---

## 10. Known limitations

1. **Kokoro is the slow engine.** On low-end hardware prefer Piper (~5× smaller, measurably faster
   to first audio). The Auto chain already falls back; there is no automatic per-device downgrade
   yet — a first-run latency probe would be the natural follow-up.
2. **~18 MB of `espeak-ng-data/` is duplicated per model.** Installing Kokoro plus both Piper
   voices costs ~230 MB unpacked. Sharing one espeak data directory across models would save ~36 MB
   but needs verification that all three archives ship byte-identical copies.
3. **sherpa-onnx is pinned to 1.13.8** for the reason in §2. Upgrading is a deliberate project, not
   a version bump.
4. **Speaker names are not in the model.** `voices.bin` has no name table, so the `af_heart` /
   `am_adam` mapping is a derived sid (1 and 5). If kokoro-onnx ever reorders its voice list the
   mapping shifts; re-derive with an F0 sweep rather than guessing.
5. **`numberWord()` covers 0–59 only.** Larger numbers are left as digits and read by the engine.
6. **`roam_guide.json` is still pinned to 2.2.16a** (see `docs/overview.md` §5), so the Roam coach
   reads stale content — e.g. Sprint's cooldown as 100s instead of 75s. This is a data problem, not
   a TTS one, and it is the top open data item.
7. **No compile-verified Gradle build here.** The sandbox has no Android SDK, so everything was
   verified with a standalone `kotlinc` + Compose compiler plugin harness (§11) and JVM unit tests.
   The first `./gradlew assembleDebug` on a real toolchain is still to be run.
8. **ktlint formatting of the new files only.** `tts/` and the tests are ktlint-clean under the
   project style (`ktlint_code_style = android_studio`,
   `ktlint_function_naming_ignore_when_annotated_with = Composable`, `max_line_length = off`). The
   four pre-existing UI files were left in their compact legacy style: reformatting their ~110
   findings would bury this feature's real diff in whitespace, so it stays a separate standalone
   pass. Net effect of this work on those files: HEAD 113 findings → 112.

---

## 11. Verification record (no device, 2026-11-27)

| Check | Result |
|---|---|
| `kotlinc 2.3.21` + Compose compiler plugin over `tts/` + `ui/theme` + `ui/components` + `ui/model` + `ui/widgets/JetOverlay.kt` + `OverlayWidgets.kt` + `SettingsScreen.kt` | **0 errors, 0 warnings**, 137 classes |
| `tts/` alone | 0 errors, 52 classes |
| JUnit run of both test classes | **OK (36 tests)** |
| detekt 1.23.8 with the project's Compose-tuned config (below), on the 9 new files | **0 code smells** (3,029 LOC) |
| detekt, same config, on the 4 changed legacy files | **0 code smells** |
| ktlint 1.8.0 (`android_studio` style) on the 9 new files | **0 findings** |
| ktlint on the 4 changed legacy files | HEAD 113 → now 112 (net **−1**; the one remaining finding on an added line sits inside a region ktlint already flags wholesale in `OverlayWidgets.kt`) |
| Normalizer over all 286 real guide steps | min 9 / max 18 / avg 15.6 words, 0 empty, 0 leaked identifiers or URLs |
| Model archives | all three sha256 values match the official `checksum.txt`; extraction and playback verified off-device with the `sherpa-onnx` Python wheel (Kokoro 24 kHz, Piper 22.05 kHz) |
| Knowledge Base integrity | 19 entries, 19 unique ids, **0 dangling `relatedIds`** |

Two real bugs were caught by the standalone compile rather than by review, and are fixed: a
`@Composable` annotation that ended up stacked on `VoiceControlRow` because the block was inserted
between `GuideControl`'s annotation and its declaration, and `toSpoken()` reading a non-existent
`instruction` field on the widget's `GuideStep` (the widget's `parseStep` already folds the JSON
`instruction` key into `description`, so the fallback chain is `phase → callout → description →
compact`).

### Reproducing these checks

The detekt config used above is **not committed** (the project has no `config/detekt/detekt.yml` and
does not apply the detekt Gradle plugin); save it as such and wire
`detekt { config = files("config/detekt/detekt.yml") }` if you want it as a CI gate:

```yaml
config:
  validation: true
naming:
  FunctionNaming:
    ignoreAnnotated: ['Composable']   # Compose composables are PascalCase
  MatchingDeclarationName:
    active: false                     # token files group declarations by concern
style:
  MaxLineLength:
    maxLineLength: 160                # codebase is intentionally compact
  MagicNumber:
    ignorePropertyDeclaration: true   # colour/dp token files
    ignoreNamedArgument: true
    ignoreConstantDeclaration: true
  WildcardImport:
    active: false                     # legacy wildcards; fix in-IDE
complexity:
  LongParameterList:
    ignoreAnnotated: ['Composable']
    functionThreshold: 8              # modifier + state + callbacks is normal
  LongMethod:
    ignoreAnnotated: ['Composable']
    threshold: 90
  CyclomaticComplexMethod:
    ignoreAnnotated: ['Composable']
    threshold: 20
  TooManyFunctions:
    thresholdInFiles: 30
    thresholdInClasses: 20
```

Under detekt's **default** rules the same 9 files report 70 findings, all of them in buckets the
project already accepted for the legacy code: `ReturnCount` (guard-clause style), `MagicNumber`
in non-property positions, `TooGenericExceptionCaught` (deliberate at the JNI / `org.json` /
`TextToSpeech` boundaries — an offline companion app must not crash), and
`LoopWithTooManyJumpStatements`. None is a defect; none was tuned away silently.

ktlint style file:

```ini
root = true

[*.{kt,kts}]
ktlint_code_style = android_studio
ktlint_function_naming_ignore_when_annotated_with = Composable
max_line_length = off
```

Standalone compile without an Android SDK (how §11 was produced — needs the Compose jars from the
BOM, an `android.jar`, a generated `R` stub, and **`-Xplugin=…/compose-compiler-plugin.jar`**,
without which codegen dies inlining `Column`):

```bash
kotlinc -Xplugin="$KOTLIN_HOME/lib/compose-compiler-plugin.jar" \
        -cp "$COMPOSE_JARS:$COROUTINES:$COMMONS_COMPRESS:$SHERPA_CLASSES:$ANDROID_JAR" \
        -jvm-target 17 -d /tmp/out \
        tts/*.kt ui/theme/*.kt ui/components/*.kt ui/model/*.kt \
        ui/widgets/JetOverlay.kt ui/widgets/OverlayWidgets.kt ui/screens/SettingsScreen.kt R-stub.kt
```

This is a review aid, not a build: it type-checks and codegens the changed layer, it does not
replace `./gradlew assembleDebug`, which still has to be run once on a real toolchain (§10 item 7).
