# Roam Guide - ProGuard rules for minify
# Only for the new Roam-only app. Old Gson / Room / data rules removed.

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Keep app entry points - do not obfuscate these
-keep class com.mlbb.ui.MainActivity { *; }
-keep class com.mlbb.ui.WidgetOverlayService { *; }
-keep class com.mlbb.ui.widgets.JetOverlay { *; }
-keep class com.mlbb.ui.widgets.* { *; }

# Keep org.json - used by OverlayWidgets to parse roam_guide.json
-keep class org.json.** { *; }
-keepattributes Signature, *Annotation*

# Keep Compose - AGP handles most, keep this for safety
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
