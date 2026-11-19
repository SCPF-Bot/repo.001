# Repository directory tree

This tree reflects the repository contents on the `main` branch when this file was generated. Portrait filenames are shown using the range notation `1.webp`–`132.webp` (all 132 files are present).

```text
.
├── .github
│   └── workflows
│       ├── debug.yml
│       ├── extract.yml
│       ├── release.yml
│       └── train.yml
├── app
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src
│       ├── main
│       │   ├── AndroidManifest.xml
│       │   ├── assets
│       │   │   ├── counter_lookup.json
│       │   │   ├── draft_ui_map.json
│       │   │   ├── emblems.json
│       │   │   ├── hero_archetypes.json
│       │   │   ├── hero_classifier_labels.txt
│       │   │   ├── hero_thresholds.json
│       │   │   ├── items.json
│       │   │   ├── mlbb_hero_classifier.tflite
│       │   │   ├── mlbb_ui_detector.tflite
│       │   │   ├── portraits
│       │   │   │   └── 1.webp–132.webp
│       │   │   └── spells.json
│       │   ├── java
│       │   │   └── com/mlbb/assistant
│       │   │       ├── MLBBApplication.kt
│       │   │       ├── di
│       │   │       │   ├── AppModule.kt
│       │   │       │   ├── DatabaseModule.kt
│       │   │       │   ├── NetworkModule.kt
│       │   │       │   ├── OverlayModule.kt
│       │   │       │   └── RepositoryModule.kt
│       │   │       └── presentation
│       │   │           ├── herodetail/HeroDetailScreen.kt
│       │   │           ├── herolist
│       │   │           │   ├── HeroListScreen.kt
│       │   │           │   ├── HeroListState.kt
│       │   │           │   └── HeroListViewModel.kt
│       │   │           ├── heropool
│       │   │           │   ├── HeroPoolScreen.kt
│       │   │           │   └── HeroPoolViewModel.kt
│       │   │           ├── history
│       │   │           │   ├── DraftHistoryScreen.kt
│       │   │           │   ├── DraftHistoryViewModel.kt
│       │   │           │   └── DraftReplayScreen.kt
│       │   │           ├── home
│       │   │           │   ├── HomeScreen.kt
│       │   │           │   └── HomeViewModel.kt
│       │   │           ├── logviewer
│       │   │           │   ├── LogViewModel.kt
│       │   │           │   └── LogViewerActivity.kt
│       │   │           ├── main/MainActivity.kt
│       │   │           ├── metaboard/MetaBoardScreen.kt
│       │   │           ├── navigation
│       │   │           │   ├── AppNavGraph.kt
│       │   │           │   └── AppRoute.kt
│       │   │           ├── shell
│       │   │           │   ├── AppShell.kt
│       │   │           │   └── AppShellViewModel.kt
│       │   │           └── welcome/PermissionWizardScreen.kt
│       │   └── res
│       │       ├── drawable
│       │       │   ├── ic_launcher_background.xml
│       │       │   ├── ic_launcher_foreground.xml
│       │       │   └── ic_log_viewer.xml
│       │       ├── mipmap-anydpi-v26
│       │       │   ├── ic_launcher.xml
│       │       │   └── ic_launcher_round.xml
│       │       ├── raw
│       │       │   ├── default_heroes.json
│       │       │   ├── lottie_ban_warning.json
│       │       │   ├── lottie_pick_success.json
│       │       │   └── lottie_scanning.json
│       │       ├── values
│       │       │   ├── colors.xml
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       ├── values-fil/strings.xml
│       │       ├── values-in/strings.xml
│       │       ├── values-ms/strings.xml
│       │       ├── values-th/strings.xml
│       │       ├── values-vi/strings.xml
│       │       └── xml
│       │           ├── accessibility_service_config.xml
│       │           └── file_paths.xml
│       └── test
│           └── java/com/mlbb/assistant
│               ├── capture
│               │   ├── PerceptualHashTest.kt
│               │   └── SlotAwareHasherTest.kt
│               ├── data/export/DraftExporterTest.kt
│               └── domain
│                   ├── advisor
│                   │   ├── BanRecommenderTest.kt
│                   │   ├── BanValueScorerTest.kt
│                   │   ├── BuildAdvisorTest.kt
│                   │   ├── CompositionAnalyzerTest.kt
│                   │   ├── DraftScoreCalculatorTest.kt
│                   │   ├── EnemyIntentAnalyzerTest.kt
│                   │   ├── TraitCounterEngineTest.kt
│                   │   └── WinConditionGeneratorTest.kt
│                   ├── engine
│                   │   ├── DraftPatternAnalyzerTest.kt
│                   │   ├── DraftSessionManagerTest.kt
│                   │   ├── DraftSessionSerializationTest.kt
│                   │   ├── PickSequenceEngineTest.kt
│                   │   ├── RankRuleEngineTest.kt
│                   │   └── WeightCalibratorTest.kt
│                   └── scoring/DraftScorerTest.kt
├── build.gradle.kts
├── config
│   └── detekt
│       ├── baseline.xml
│       └── detekt.yml
├── core
│   ├── cv
│   │   ├── build.gradle.kts
│   │   └── src/main
│   │       ├── AndroidManifest.xml
│   │       └── java/com/mlbb/assistant
│   │           ├── capture
│   │           │   ├── AspectRatioPreset.kt
│   │           │   ├── CvFeatureFlags.kt
│   │           │   ├── FirstPickDetector.kt
│   │           │   ├── FrameProcessor.kt
│   │           │   ├── HeroClassifier.kt
│   │           │   ├── HeroPortraitObjectDetector.kt
│   │           │   ├── HeroThresholds.kt
│   │           │   ├── PerceptualHash.kt
│   │           │   ├── PhaseDetectionConfig.kt
│   │           │   ├── PhaseDetector.kt
│   │           │   ├── PhaseOcrDetector.kt
│   │           │   ├── PortraitMatcher.kt
│   │           │   ├── PortraitNormalizer.kt
│   │           │   ├── RankDetector.kt
│   │           │   ├── SlotAwareHasher.kt
│   │           │   ├── SlotConsensusManager.kt
│   │           │   └── SlotRegions.kt
│   │           │   └── TemporalConsensusBuffer.kt
│   │           └── service
│   │               ├── MLBBAccessibilityService.kt
│   │               ├── ScreenCaptureManager.kt
│   │               └── VoiceAlertService.kt
│   ├── data
│   │   ├── build.gradle.kts
│   │   └── src/main
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/mlbb/assistant
│   │       │   ├── AppDataStore.kt
│   │       │   ├── data
│   │       │   │   ├── export/DraftExporter.kt
│   │       │   │   ├── local
│   │       │   │   │   ├── crashlog
│   │       │   │   │   │   ├── AppLogTree.kt
│   │       │   │   │   │   └── CrashLogStore.kt
│   │       │   │   │   ├── database
│   │       │   │   │   ├── AppDatabase.kt
│   │       │   │   │   ├── Converters.kt
│   │       │   │   │   ├── CounterLookupDao.kt
│   │       │   │   │   ├── CounterLookupEntity.kt
│   │       │   │   │   ├── DraftSessionDao.kt
│   │       │   │   │   ├── DraftSessionEntity.kt
│   │       │   │   ├── HeroDao.kt
│   │       │   │   ├── HeroEntity.kt
│   │       │   │   ├── HeroPoolDao.kt
│   │       │   │   └── HeroPoolEntity.kt
│   │       │   │   ├── datastore/PreferencesDataStore.kt
│   │       │   │   ├── preferences/WizardPreference.kt
│   │       │   │   └── remote
│   │       │   │       ├── api/MetaApi.kt
│   │       │   │       └── dto/MetaSnapshotDto.kt
│   │       │   ├── data/repository
│   │       │   │   ├── DraftSessionRepositoryImpl.kt
│   │       │   │   └── HeroRepositoryImpl.kt
│   │       │   └── utils
│   │       │       ├── DevModeManager.kt
│   │       │       ├── Extensions.kt
│   │       │       ├── JsonParser.kt
│   │       │       └── NetworkMonitor.kt
│   │       └── res/raw/default_heroes.json
│   ├── designsystem
│   │   ├── build.gradle.kts
│   │   └── src/main
│   │       ├── AndroidManifest.xml
│   │       └── java/com/mlbb/assistant/presentation
│   │           ├── common/components
│   │           │   ├── BackButton.kt
│   │           │   ├── ConnectivityBanner.kt
│   │           │   ├── HeroGrid.kt
│   │           │   ├── HeroPortrait.kt
│   │           │   ├── LoadingSpinner.kt
│   │           │   ├── MLBBButton.kt
│   │           │   ├── MLBBTextField.kt
│   │           │   └── RoleDashboard.kt
│   │           └── common/theme
│   │               ├── Color.kt
│   │               ├── Theme.kt
│   │               └── Type.kt
│   └── scoring
│       ├── build.gradle.kts
│       └── src/main
│           ├── AndroidManifest.xml
│           └── java/com/mlbb/assistant
│               ├── domain
│               │   ├── OverlayController.kt
│               │   ├── advisor
│               │   │   ├── BanRecommender.kt
│               │   │   ├── BanThresholds.kt
│               │   │   ├── BanUrgencyScorer.kt
│               │   │   ├── BanValueScorer.kt
│               │   │   ├── BuildAdvisor.kt
│               │   │   ├── CompositionAnalyzer.kt
│               │   │   ├── CompositionArchetype.kt
│               │   │   ├── DraftScoreCalculator.kt
│               │   │   ├── EnemyIntentAnalyzer.kt
│               │   │   ├── HeroArchetypeService.kt
│               │   │   ├── TraitCounterEngine.kt
│               │   │   └── WinConditionGenerator.kt
│               │   ├── engine
│               │   │   ├── DraftPatternAnalyzer.kt
│               │   │   ├── DraftSessionManager.kt
│               │   │   ├── PickSequenceEngine.kt
│               │   │   ├── RankRuleEngine.kt
│               │   │   └── WeightCalibrator.kt
│               │   ├── model
│               │   │   ├── DraftHistoryItem.kt
│               │   │   ├── DraftOutcome.kt
│               │   │   ├── Hero.kt
│               │   │   ├── HeroRole.kt
│               │   │   └── Proficiency.kt
│               │   ├── repository
│               │   │   ├── DraftSessionRepository.kt
│               │   │   └── HeroRepository.kt
│               │   ├── scoring
│               │   │   ├── DraftScorer.kt
│               │   │   └── ScoreWeights.kt
│               │   └── usecase
│               │       ├── GetDraftHistoryUseCase.kt
│               │       ├── GetHeroesUseCase.kt
│               │       ├── GetPagedHeroesUseCase.kt
│               │       ├── GetSuggestionsUseCase.kt
│               │       ├── SaveDraftSessionUseCase.kt
│               │       ├── SyncHeroesUseCase.kt
│               │       └── ToggleOverlayUseCase.kt
│               └── utils
│                   ├── AppConstants.kt
│                   ├── DateFormatter.kt
│                   ├── NetworkResult.kt
│                   └── ScoringExtensions.kt
├── docs
│   ├── mission.md
│   ├── plan.md
│   └── temp/latest.md
├── feature
│   ├── draft
│   │   ├── build.gradle.kts
│   │   └── src/main
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/mlbb/assistant/presentation/draft
│   │       │   ├── DraftScreen.kt
│   │       │   ├── DraftState.kt
│   │       │   ├── DraftViewModel.kt
│   │       │   ├── ScoreExplanationSheet.kt
│   │       │   └── components
│   │       │       ├── HeroChip.kt
│   │       │       └── SuggestionCard.kt
│   │       └── res/values/strings.xml
│   ├── overlay
│   │   ├── build.gradle.kts
│   │   └── src/main
│   │       ├── AndroidManifest.xml
│   │       ├── assets/roam_guide.json
│   │       ├── java/com/mlbb/assistant/presentation/overlay
│   │       │   ├── BanPhaseContent.kt
│   │       │   ├── DraftOverlayContent.kt
│   │       │   ├── DraftPanel.kt
│   │       │   ├── FinalReportContent.kt
│   │       │   ├── JetOverlay.kt
│   │       │   ├── MiniWidget.kt
│   │       │   ├── OverlayBubbles.kt
│   │       │   ├── OverlayCaptureCoordinator.kt
│   │       │   ├── OverlayPermissionActivity.kt
│   │       │   ├── OverlayService.kt
│   │       │   ├── OverlayStateHolder.kt
│   │       │   ├── PickPhaseContent.kt
│   │       │   ├── RoamOverlayWidgets.kt
│   │       │   ├── TradingPhaseContent.kt
│   │       │   └── components
│   │       │       ├── ActiveDraftBody.kt
│   │       │       ├── BottomActionBar.kt
│   │       │       ├── CompositionInsightsPanel.kt
│   │       │       ├── IdleWidgetBody.kt
│   │       │       ├── RecommendedRows.kt
│   │       │       ├── SlotRows.kt
│   │       │       ├── WidgetHeaderBar.kt
│   │       │       └── WidgetScorePanel.kt
│   │       └── res
│   │           ├── raw
│   │           │   ├── lottie_ban_warning.json
│   │           │   ├── lottie_pick_success.json
│   │           │   └── lottie_scanning.json
│   │           └── values/strings.xml
│   └── settings
│       ├── build.gradle.kts
│       └── src/main
│           ├── AndroidManifest.xml
│           ├── java/com/mlbb/assistant/presentation/settings
│           │   ├── SettingsScreen.kt
│           │   ├── SettingsState.kt
│           │   ├── SettingsViewModel.kt
│           │   └── components
│           │       ├── AspectRatioSection.kt
│           │       ├── BanCountRow.kt
│           │       ├── CalibrationSection.kt
│           │       ├── ScoringWeightsSection.kt
│           │       └── SettingsPrimitives.kt
│           └── res/values/strings.xml
├── gradle
│   ├── libs.versions.toml
│   └── wrapper
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradle.properties
├── gradlew
├── gradlew.bat
├── readme.md
├── scripts
│   ├── requirements.txt
│   ├── temp
│   │   ├── mlbb_hero_classifier.tflite
│   │   ├── mlbb_hero_classifier_train.ipynb.txt
│   │   └── readme.txt
│   └── train.py
└── settings.gradle.kts
```