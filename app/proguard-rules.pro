# Capvid keeps the JNI entry points and JSON model names stable in release builds.
-keep class com.saad.capvid.whisper.WhisperNative { *; }
-keep class com.saad.capvid.whisper.WhisperNative$ProgressCallback { *; }
-keep class com.saad.capvid.model.** { *; }
-keep class com.arthenica.ffmpegkit.** { *; }
-keepattributes *Annotation*
