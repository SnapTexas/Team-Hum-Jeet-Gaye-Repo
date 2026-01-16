# Add project specific ProGuard rules here.

# Keep TensorFlow Lite classes
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }

# Keep ML models
-keep class com.healthtracker.ml.model.** { *; }
