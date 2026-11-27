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
    group = "voice guide"
    description = "Downloads and checksum-verifies the sherpa-onnx AAR into app/libs."
    outputs.file(sherpaAarFile)
    onlyIf { !sherpaAarFile.asFile.exists() }
    doLast {
        val target = sherpaAarFile.asFile
        target.parentFile.mkdirs()
        logger.lifecycle("Voice Guide: downloading $sherpaAarUrl (50 MB, once)")
        val partial = java.io.File(target.absolutePath + ".part")
        java.net.URI(sherpaAarUrl).toURL().openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 120_000
        }.getInputStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(sherpaAarSha256, ignoreCase = true)) {
            partial.delete()
            throw GradleException(
                "sherpa-onnx AAR checksum mismatch.\n  expected $sherpaAarSha256\n  actual   $actual\n" +
                    "Delete any partial file and retry, or place the AAR at ${target.path} yourself."
            )
        }
        if (!partial.renameTo(target)) {
            throw GradleException("Could not move the downloaded AAR to ${target.path}")
        }
        logger.lifecycle("Voice Guide: sherpa-onnx AAR verified and installed")
    }
}

// Wired with matching{} rather than named{} so the build never fails if a future
// AGP renames these tasks.
tasks.matching { it.name == "preBuild" || (it.name.contains("compile") && it.name.contains("Kotlin")) }
    .configureEach { dependsOn(downloadSherpaAar) }

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
    // below instead of being committed. Pinned to 1.13.8 ON PURPOSE: 2.0.x
    // removed the built-in espeak-ng frontend, which breaks the Piper VITS packs
    // ("no lexicon, and no frontend available") and forces a separate
    // lexicon-en-us.txt download for Kokoro. Both packs were verified to
    // synthesise on 1.13.8 from their self-contained archives.
    implementation(files("libs/$sherpaAarName"))
    // Voice packs ship as .tar.bz2; Android has java.util.zip but no bzip2.
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
}
