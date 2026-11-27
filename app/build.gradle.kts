// Explicit imports: inside a module that applies the Android plugin, the bare
// name `java` resolves to the `java` extension (JavaPluginExtension) added by
// AGP's JavaBasePlugin, so qualified names like `java.io.File` silently compile
// to `JavaPluginExtension.io.File` and fail the script. Importing the classes
// keeps every reference unambiguous.
import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // namespace must match the Kotlin packages (com.mlbb.plus.*).
    namespace = "com.mlbb.plus"

    // Compose 1.12 (BOM 2026.06.00) requires compileSdk 37 + AGP 9.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mlbb.plus"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // Kotlin's jvmTarget follows this; no separate kotlin { } block needed with AGP 9.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    // The sherpa-onnx AAR is ~32 MB of .so files per ABI. Uncomment to ship
    // device-specific APKs instead of one universal build (see
    // TTS_IMPLEMENTATION.md "APK size"):
    //
    // splits {
    //     abi {
    //         isEnable = true
    //         reset()
    //         include("arm64-v8a", "armeabi-v7a")
    //         isUniversalApk = false
    //     }
    // }

    // Voice Guide: the TTS voice packs ship inside the APK (speech works with
    // no network at all). The archives are staged by the bundleTtsModels task
    // into this generated directory; src/main/assets/tts/ holds only the small
    // tts-models.json manifest that describes them.
    sourceSets {
        getByName("main") {
            assets.directories.add(layout.buildDirectory.dir("generated/tts-assets").get().asFile.absolutePath)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// ============================================================
// Voice Guide: sherpa-onnx AAR provisioning
// ------------------------------------------------------------
// Not on Maven Central or JitPack - k2-fsa publishes it as a GitHub release
// asset. The task below fetches it once, verifies the published SHA-256, and
// leaves it in app/libs (gitignored). To place it by hand instead, drop the
// file at app/libs/sherpa-onnx-1.13.8.aar; the task then does nothing.
// ============================================================
val sherpaVersion = "1.13.8"
val sherpaAarName = "sherpa-onnx-$sherpaVersion.aar"
val sherpaAarSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaAarUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/$sherpaAarName"
val sherpaAarFile = layout.projectDirectory.file("libs/$sherpaAarName")

val downloadSherpaAar = tasks.register("downloadSherpaAar") {
    // Capture plain values before the task actions: a doLast closure that
    // touches script-level `val`s would serialize the script object itself,
    // which the configuration cache rejects ("cannot serialize Gradle script
    // object references"). File/String locals are fully serializable.
    val target: File = sherpaAarFile.asFile
    val url: String = sherpaAarUrl
    val expectedSha: String = sherpaAarSha256
    group = "voice guide"
    description = "Downloads and checksum-verifies the sherpa-onnx AAR into app/libs."
    outputs.file(target)
    onlyIf { !target.exists() }
    doLast {
        target.parentFile.mkdirs()
        logger.lifecycle("Voice Guide: downloading $url (50 MB, once)")
        val partial = File(target.absolutePath + ".part")
        URI(url).toURL().openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 120_000
        }.getInputStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expectedSha, ignoreCase = true)) {
            partial.delete()
            throw GradleException(
                "sherpa-onnx AAR checksum mismatch.\n  expected $expectedSha\n  actual   $actual\n" +
                    "Delete any partial file and retry, or place the AAR at ${target.path} yourself."
            )
        }
        if (!partial.renameTo(target)) {
            throw GradleException("Could not move the downloaded AAR to ${target.path}")
        }
        logger.lifecycle("Voice Guide: sherpa-onnx AAR verified and installed")
    }
}

// ============================================================
// Voice Guide: bundled TTS voice packs
// ------------------------------------------------------------
// The voice packs are compiled into the APK so speech is fully offline at
// runtime. Their archives are deliberately NOT committed to git (the Kokoro
// archive alone is 103 MB, over GitHub's per-file limit): this task fetches
// them into app/tts-models-cache/ (gitignored), verifies each SHA-256 against
// app/src/main/assets/tts/tts-models.json, and copies them into the generated
// assets directory wired into the android block above. That manifest is the
// single source of truth; scripts/update_tts_models.py maintains it.
// ============================================================
val ttsManifestFile = layout.projectDirectory.file("src/main/assets/tts/tts-models.json")
val ttsCacheDir = layout.projectDirectory.dir("tts-models-cache")
val ttsGeneratedAssets = layout.buildDirectory.dir("generated/tts-assets")

val bundleTtsModels = tasks.register("bundleTtsModels") {
    // Same configuration-cache rule as downloadSherpaAar: capture plain
    // values, never script-level `val` references, inside the task actions.
    val manifestFile: File = ttsManifestFile.asFile
    val cacheRoot: File = ttsCacheDir.asFile
    val assetsRoot: File = ttsGeneratedAssets.get().asFile
    group = "voice guide"
    description = "Fetches and checksum-verifies the bundled TTS voice packs into generated assets."
    inputs.file(manifestFile)
    outputs.dir(assetsRoot)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val manifest = groovy.json.JsonSlurper().parse(manifestFile) as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val models = manifest["models"] as? List<Map<String, Any?>>
            ?: throw GradleException("tts-models.json: missing or invalid 'models' array")

        val sha256Of = { f: File ->
            if (!f.isFile) "" else {
                val digest = MessageDigest.getInstance("SHA-256")
                f.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }

        val cache = cacheRoot.apply { mkdirs() }
        val outDir = File(assetsRoot, "tts").apply { mkdirs() }

        for (model in models) {
            val archive = model["archive"] as? String
                ?: throw GradleException("tts-models.json: a model entry has no 'archive'")
            val url = model["url"] as? String
                ?: throw GradleException("tts-models.json: $archive has no 'url'")
            val expected = (model["sha256"] as? String ?: "").lowercase()
            if (expected.isEmpty()) throw GradleException("tts-models.json: $archive has no 'sha256'")

            val cached = File(cache, archive)
            if (!expected.equals(sha256Of(cached), ignoreCase = true)) {
                logger.lifecycle("Voice Guide: downloading $url")
                val partial = File(cache, "$archive.part")
                URI(url).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 300_000
                }.getInputStream().use { input ->
                    partial.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
                }
                val actual = sha256Of(partial)
                if (!expected.equals(actual, ignoreCase = true)) {
                    partial.delete()
                    throw GradleException(
                        "TTS pack checksum mismatch for $archive.\n  expected $expected\n  actual   $actual"
                    )
                }
                if (!partial.renameTo(cached)) {
                    throw GradleException("Could not move the downloaded pack to ${cached.path}")
                }
                logger.lifecycle("Voice Guide: $archive verified (${cached.length() / (1 shl 20)} MB)")
            }

            val target = File(outDir, archive)
            if (!target.exists() || !expected.equals(sha256Of(target), ignoreCase = true)) {
                cached.copyTo(target, overwrite = true)
            }
        }
        logger.lifecycle("Voice Guide: ${models.size} voice pack(s) staged into ${outDir.path}")
    }
}

// Wired with matching{} rather than named{} so the build never fails if a future
// AGP renames these tasks.
tasks.matching { it.name == "preBuild" || (it.name.contains("compile") && it.name.contains("Kotlin")) }
    .configureEach { dependsOn(downloadSherpaAar) }
tasks.matching { it.name == "preBuild" || (it.name.contains("merge") && it.name.contains("Assets")) }
    .configureEach { dependsOn(bundleTtsModels) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    // OverlayWidgets.kt builds its own SavedStateRegistryOwner for the overlay ComposeView.
    implementation(libs.androidx.savedstate)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.coil.compose)

    // ---------------------------------------------------------------
    // Voice Guide (floating widget TTS)
    // ---------------------------------------------------------------
    // On-device neural TTS. The AAR is 50 MB of native libraries for four ABIs,
    // so it is fetched into app/libs on demand by the downloadSherpaAar task
    // above instead of being committed. Pinned to 1.13.8 ON PURPOSE: 2.0.x
    // removed the built-in espeak-ng frontend, which breaks the Piper VITS packs
    // ("no lexicon, and no frontend available") and forces a separate
    // lexicon-en-us.txt download for Kokoro. Both packs were verified to
    // synthesise on 1.13.8 from their self-contained archives.
    implementation(files("libs/$sherpaAarName"))
    // Voice packs ship as .tar.bz2; Android has java.util.zip but no bzip2.
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
}
