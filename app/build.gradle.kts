import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

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

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isMinifyEnabled = false
      isShrinkResources = false
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
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
// Voice Guide: sherpa-onnx AAR provisioning (pinned 1.13.8)
// ============================================================
val sherpaVersion = "1.13.8"
val sherpaAarName = "sherpa-onnx-$sherpaVersion.aar"
val sherpaAarSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaAarUrl =
  "https://github.com/k2fsa/sherpa-onnx/releases/download/v$sherpaVersion/$sherpaAarName"
val sherpaAarFile = layout.projectDirectory.file("libs/$sherpaAarName")

val downloadSherpaAar = tasks.register("downloadSherpaAar") {
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
    val client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofSeconds(30))
      .build()
    val req = HttpRequest.newBuilder(URI(url))
      .timeout(Duration.ofSeconds(180))
      .header("User-Agent", "Mozilla/5.0")
      .GET()
      .build()
    val resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
    if (resp.statusCode() !in 200..299) {
      throw GradleException("HTTP ${resp.statusCode()} downloading $url")
    }
    resp.body().use { input ->
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
        "sherpa-onnx AAR checksum mismatch.\n  expected $expectedSha\n  actual   $actual"
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
