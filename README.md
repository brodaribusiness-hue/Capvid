# Capvid

Capvid is a native Kotlin Android caption editor. It imports or records video, extracts audio locally, transcribes it with the multilingual `ggml-tiny.bin` whisper.cpp model, lets the user edit word timings and styling, and burns the selected animated ASS subtitle style into an MP4 with FFmpegKit.

## Build prerequisites

- JDK 17
- Android SDK platform 35, build-tools 35.0.0
- Android NDK 27.0.12077973 and CMake 3.22.1
- Gradle 8.10.2 (there is intentionally no Gradle wrapper)

The whisper.cpp source is intentionally not committed. Before a local build, clone it exactly as CI does:

```bash
git clone --depth 1 https://github.com/ggml-org/whisper.cpp.git app/src/main/cpp/whisper
gradle :app:assembleDebug
gradle :app:test
```

The first caption generation downloads the multilingual `ggml-tiny.bin` model into app-private storage. The download is only for the model bootstrap; audio extraction, transcription after installation, preview, project files, and export run on-device without a service or API key.

## Architecture

- `caption/`: word grouping, style catalog, and the Canvas preview.
- `whisper/`: model bootstrap, FFmpeg PCM extraction, and the Kotlin/JNI boundary.
- `export/`: per-style ASS generation and FFmpeg burn-in/gallery publishing.
- `history/`: immutable project snapshots exposed through the command pattern.
- `project/`: versioned JSON `.capvid` project files.
- `editor/`, `timeline/`, `trim/`, `scale/`, and `style/`: the native Android editor UI.

The preview and exporter share `CaptionEffect` and the same preset definitions. Export is not a plain-text fallback: ASS events contain effect-specific karaoke, scale, blur, rotation, shadow, outline, and timing tags for all 18 built-in presets.

## CI

`.github/workflows/android-ci.yml` installs the Android SDK packages from the runner's pre-installed SDK, installs Gradle directly, fetches whisper.cpp, builds the debug APK, runs JVM tests, boots an API 35 emulator, and runs instrumented tests. No cloud transcription service is used.
