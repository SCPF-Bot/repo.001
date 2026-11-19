plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // FIX: namespace must match applicationId base + Kotlin packages
    // (com.mlbb.plus.ui.*). Old namespace "com.mlbb" + applicationId
    // "com.mlbb.plus" + packages "com.mlbb.ui" was a 3-way split that
    // breaks Manifest relative class names (.ui.MainActivity) and R-class
    // expectations. All three are now com.mlbb.plus.
    namespace = "com.mlbb.plus"
    // FIX: compileSdk 37 -> 36 to match targetSdk 36. compileSdk 37
    // needs bleeding-edge AGP and offers no API your code uses.
    // If you deliberately need 37, bump targetSdk to 37 as well and
    // use AGP 8.7+.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mlbb.plus"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // Standard runner - was missing; needed for androidTest.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // FIX: isShrinkResources was false while isMinifyEnabled
            // was true - inconsistent. With R8 on, shrinking resources
            // is what actually drops unused drawables / strings.
            // If your release APK ever loses a needed resource, add it
            // to res/raw/keep.xml instead of turning this off.
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // IMPORTANT: proguard-rules.pro must keep your JSON models,
            // otherwise R8 strips fields that GameData parses by
            // reflection / kotlinx.serialization:
            //   -keep class com.mlbb.plus.ui.model.** { *; }
            //   -keepclassmembers class com.mlbb.plus.ui.model.** { *; }
            //   -keep class com.mlbb.plus.ui.screens.** { *; }
            // If GameData uses kotlinx.serialization, also add:
            //   -keepattributes *Annotation*, InnerClasses
            //   -dontnote kotlinx.serialization.**
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            // FIX: debug suffix so debug + release can be installed
            // side-by-side on the same phone.
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
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    // Don't fail a release build on a lint warning - lint still runs
    // in CI, it just won't block `assembleRelease` for a docs typo.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // FIX: explicit jvmTarget so Kotlin bytecode matches
        // compileOptions Java 17. Without this, Kotlin can default
        // to JVM 1.8 and you get "class file version" warnings.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    // FIX (new batch): Coil3 was MISSING but AssetImage.kt imports it.
    // AssetImage uses Coil3 API: coil3.compose.AsyncImage + onState.
    // Do NOT use Coil2 (io.coil-kt:coil-compose) - its AsyncImage uses
    // onSuccess/onError, which does NOT compile with coil3 import.
    // Coil3 minSdk is 23, so minSdk 29 is fine. Coil ships its own
    // R8 / Proguard consumer rules, no extra keep rules needed.
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Kept as a hardcoded version on purpose: this alias is not in
    // your version catalog yet. To move it to the catalog, add to
    // libs.versions.toml:
    //   [libraries]
    //   androidx-savedstate = { module = "androidx.savedstate:savedstate", version = "1.2.1" }
    // then replace the line below with:
    //   implementation(libs.androidx.savedstate)
    // Needed for setViewTreeSavedStateRegistryOwner() in OverlayWidgets.kt.
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // OPTIONAL - uncomment ONLY if GameData.load() uses it:
    // If GameData parses heroes.json / items.json / patch_notes.json
    // with kotlinx.serialization, you MUST add this - it is currently
    // missing and would be a compile error in GameData.kt.
    // If GameData uses org.json / Gson, leave it commented out.
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// REMINDER - AndroidManifest.xml (not in this .kts) must declare:
//   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
//   <service android:name=".ui.WidgetOverlayService"
//       android:exported="false"
//       android:foregroundServiceType="specialUse" />
