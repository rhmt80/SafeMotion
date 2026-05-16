# Keep TensorFlow Lite native interfaces — R8 strips them otherwise.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn org.tensorflow.lite.support.**

# Keep Material Components attribute holders that R8 sometimes drops.
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep our own application classes referenced by the manifest.
-keep class com.example.falldetectapp.** { *; }

# Useful crash-trace info in release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
