# MLBB Plus - ProGuard/R8 rules.
# The default android-optimize rules + AndroidX/Compose consumer rules cover most of this.
# Entry points are kept via the manifest automatically; these are explicit for safety.

-keep class com.mlbb.plus.ui.MainActivity { *; }
-keep class com.mlbb.plus.ui.WidgetOverlayService { *; }

# JetOverlay / OverlayLifecycleOwner are created from a Service, not referenced from the manifest.
-keep class com.mlbb.plus.ui.widgets.** { *; }

-keep class kotlin.Metadata { *; }
-keepattributes Signature
-dontwarn kotlin.**
