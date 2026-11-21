// Top-level build file. Only plugins the :app module actually applies are declared here.
// (ksp / hilt / detekt / serialization / android-library were declared but never used;
// `apply false` still forces Gradle to resolve them, so an unresolvable version broke
// the build before any Kotlin was compiled.)
plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin.android is intentionally absent: AGP 9.x has built-in Kotlin support.
    alias(libs.plugins.kotlin.compose) apply false
}
