# MLBB Plus - ProGuard/R8 rules.
# The default android-optimize rules + AndroidX/Compose consumer rules cover most of this.
# Entry points are kept via the manifest automatically; these are explicit for safety.

-keep class com.mlbb.plus.ui.MainActivity { *; }
-keep class com.mlbb.plus.ui.WidgetOverlayService { *; }

# JetOverlay / OverlayLifecycleOwner are created from a Service, not referenced from the manifest.
-keep class com.mlbb.plus.ui.widgets.** { *; }

# Voice Guide: sherpa-onnx reaches its Kotlin API through JNI, so the class and
# member names must survive R8 exactly as the native side expects them.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# VoiceGuideManager is driven from the overlay Compose tree and from the service;
# keep the package so state classes are not renamed away from Compose's lookups.
-keep class com.mlbb.plus.tts.** { *; }

# commons-compress (used only for .tar.bz2 voice packs) references optional
# codecs we never touch: xz, brotli, zstd, asm. Silence the missing-class errors.
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.objectweb.asm.**
-dontwarn javax.inject.**
-dontwarn org.osgi.**

-keep class kotlin.Metadata { *; }
-keepattributes Signature
-dontwarn kotlin.**
