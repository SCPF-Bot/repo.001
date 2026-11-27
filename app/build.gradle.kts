import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.mlbb.plus"
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

  // Release signing is opt-in: CI (or a developer) exports KEYSTORE_PATH, STORE_PASSWORD and
  // KEY_PASSWORD. Without them, release builds are produced unsigned rather than signed with
  // a known key. Debug builds use AGP's auto-generated ~/.android/debug.keystore, so a fresh
  // clone builds without any keystore file in the repository.
  val releaseKeystorePath: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
  signingConfigs {
    if (releaseKeystorePath != null) {
      create("release") {
        storeFile = file(releaseKeystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (releaseKeystorePath != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      isMinifyEnabled = false
      isShrinkResources = false
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
  }

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
    // Non-fatal on purpose for now: lint findings are reported in the CI artifacts but do
    // not block merges. Flip abortOnError once the baseline is clean (see docs/review).
    abortOnError = false
    checkReleaseBuilds = false
  }

  sourceSets {
    getByName("main") {
      assets.directories.add(layout.buildDirectory.dir("generated/tts-assets").get().asFile.absolutePath)
    }
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// ============================================================
// Voice Guide: shared download + checksum helpers
// ============================================================

/**
 * Checksum and download helpers. Declared as a nested object (not top-level functions) so the
 * configuration cache can serialise the task actions that use them.
 */
object VoiceGuideFetch {
  /** Lower-case hex SHA-256 of [file], or "" when it does not exist. */
  fun fileSha256(file: File): String {
    if (!file.isFile) return ""
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(1 shl 16)
      while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /**
   * Downloads [url] to [dest] through a `.part` file and only moves it into place once its
   * SHA-256 matches [expectedSha256]. A failed or tampered download never replaces [dest].
   */
  fun downloadVerified(url: String, dest: File, expectedSha256: String, label: String) {
    dest.parentFile.mkdirs()
    val partial = File(dest.absolutePath + ".part")
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 30_000
    connection.readTimeout = 300_000
    connection.setRequestProperty("User-Agent", "MLBB-Plus-build")
    val status = connection.responseCode
    if (status !in 200..299) {
      throw GradleException("HTTP $status downloading $url")
    }
    connection.inputStream.use { input ->
      partial.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
    }
    val actual = VoiceGuideFetch.fileSha256(partial)
    if (!expectedSha256.equals(actual, ignoreCase = true)) {
      partial.delete()
      throw GradleException(
        "$label checksum mismatch.\n  expected $expectedSha256\n  actual   $actual"
      )
    }
    dest.delete()
    if (!partial.renameTo(dest)) {
      throw GradleException("Could not move the downloaded $label to ${dest.path}")
    }
  }
}

// ============================================================
// Voice Guide: sherpa-onnx AAR provisioning (pinned 1.13.8)
// ============================================================
val sherpaVersion = "1.13.8"
val sherpaAarName = "sherpa-onnx-$sherpaVersion.aar"
val sherpaAarSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
// Published under the k2-fsa GitHub organisation. The k2fsa spelling returns HTTP 404.
val sherpaAarUrl =
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/$sherpaAarName"
val sherpaAarFile = layout.projectDirectory.file("libs/$sherpaAarName")

val downloadSherpaAar = tasks.register("downloadSherpaAar") {
  // Copy script-level values into locals: a lambda that reads a script property captures the
  // script instance, which the configuration cache cannot serialise.
  val target: File = sherpaAarFile.asFile
  val url: String = sherpaAarUrl
  val pinnedSha256: String = sherpaAarSha256
  group = "voice guide"
  description = "Downloads and checksum-verifies the sherpa-onnx AAR into app/libs."
  outputs.file(target)
  // Re-download when the AAR is missing or no longer matches its pin, so a truncated or
  // tampered file is never compiled in silently.
  onlyIf { !pinnedSha256.equals(VoiceGuideFetch.fileSha256(target), ignoreCase = true) }
  doLast {
    logger.lifecycle("Voice Guide: downloading $url (50 MB, once)")
    VoiceGuideFetch.downloadVerified(url, target, pinnedSha256, "sherpa-onnx AAR")
    logger.lifecycle("Voice Guide: sherpa-onnx AAR verified and installed")
  }
}

// ============================================================
// Voice Guide: bundled TTS voice packs
// ============================================================
val ttsManifestFile = layout.projectDirectory.file("src/main/assets/tts/tts-models.json")
val ttsCacheDir = layout.projectDirectory.dir("tts-models-cache")
val ttsGeneratedAssets = layout.buildDirectory.dir("generated/tts-assets")

val bundleTtsModels = tasks.register("bundleTtsModels") {
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
      if (!expected.equals(VoiceGuideFetch.fileSha256(cached), ignoreCase = true)) {
        logger.lifecycle("Voice Guide: downloading $url")
        VoiceGuideFetch.downloadVerified(url, cached, expected, archive)
        logger.lifecycle("Voice Guide: $archive verified (${cached.length() / (1 shl 20)} MB)")
      }
      val target = File(outDir, archive)
      if (!target.exists() || !expected.equals(VoiceGuideFetch.fileSha256(target), ignoreCase = true)) {
        cached.copyTo(target, overwrite = true)
      }
    }
    logger.lifecycle("Voice Guide: ${models.size} voice pack(s) staged into ${outDir.path}")
  }
}

tasks.matching { it.name == "preBuild" || (it.name.contains("compile") && it.name.contains("Kotlin")) }
  .configureEach { dependsOn(downloadSherpaAar) }
tasks.matching { it.name == "preBuild" || (it.name.contains("merge") && it.name.contains("Assets")) }
  .configureEach { dependsOn(bundleTtsModels) }

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.savedstate)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.serialization.json)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  implementation(libs.coil.compose)
  implementation(files("libs/$sherpaAarName"))
  implementation(libs.commons.compress)

  testImplementation(libs.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
}
