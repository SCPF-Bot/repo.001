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

  // Release signing. A real upload key can be supplied through KEYSTORE_PATH, STORE_PASSWORD,
  // KEY_PASSWORD and (optionally) KEY_ALIAS, for example from CI secrets. Without them the
  // release is signed with the committed TEST key in app/keystore/. Its password is public
  // on purpose, so a test-signed APK is for testing and sideloading only. Never publish it.
  // Debug builds keep AGP's auto-generated debug key.
  val uploadKeystorePath: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
  val testStorePassword = "mlbb-test"
  val testKeyAlias = "mlbb-test"
  signingConfigs {
    create("release") {
      if (uploadKeystorePath != null) {
        storeFile = file(uploadKeystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        storeFile = file("keystore/mlbb-test-release.jks")
        storePassword = testStorePassword
        keyAlias = testKeyAlias
        keyPassword = testStorePassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      // Native code for 64-bit ARM only (about 90 MB smaller than four ABIs). Debug builds keep
      // every ABI so x86 emulators still run them. Add armeabi-v7a here if 32-bit phones matter.
      ndk {
        abiFilters += listOf("arm64-v8a")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
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
// Voice Guide: voice packs are NOT bundled. The app downloads the selected pack from the
// upstream URL in assets/tts/tts-models.json the first time Voice Guide is enabled.
// ============================================================

tasks.matching { it.name == "preBuild" || (it.name.contains("compile") && it.name.contains("Kotlin")) }
  .configureEach { dependsOn(downloadSherpaAar) }

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
  // Compose layout tests (JVM, Robolectric): measure the overlay card's real width and height.
  testImplementation(platform(libs.compose.bom))
  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
