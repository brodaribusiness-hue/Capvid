# Capvid

Offline word-level video captioning for Android.

Video → preview → extract audio → **on-device** Whisper transcription → word-level
captions → style/edit → burn into the video → export MP4 → save to Gallery.

No transcription API. No account. No subscription. After the one-time model
setup, nothing in the app makes a network call and no video or audio leaves the
device.

---

## Building

```bash
git clone --recursive https://github.com/brodaribusiness-hue/Capvid.git
# or, if you already cloned:
git submodule update --init --recursive

./gradlew testDebugUnitTest      # caption layout + ASS generation tests
./gradlew assembleDebug
./gradlew assembleRelease
```

`app/src/main/cpp/whisper` is a **git submodule** pinned to whisper.cpp
`v1.9.4` (`927cfce34f31707e17f2bff35c349632fb9e2c3a`). You must initialise it
before building; without it the native build fails.

Requirements: JDK 17, Android SDK 34, NDK `26.1.10909125`, CMake `3.22.1`.
The Gradle wrapper (8.5) is committed, so no separate Gradle install is needed.

CI runs the whole thing on every push and pull request — see
[`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml). The last
green run compiled the native bridge for both ABIs and executed 30 unit tests.
It also posts the executed test names to the pull request, so "the tests passed"
is checkable rather than asserted.

### Contract checks

```bash
pip install fonttools
python3 tools/audit_static.py
```

This verifies things a normal compile does not: package/directory consistency,
`R.*` and `findViewById` resolution, style-catalog ↔ enum coverage, JNI symbol
matching, and — importantly — that every ASS font family name we write matches
the real family name inside the bundled font file. A mismatch there makes libass
silently substitute a different font in the exported video, with no error
anywhere. Exits non-zero on any problem; CI runs it on every build.

---

## First-time model setup

The speech model is **not** bundled (it is ~75 MB). On first use the editor
offers:

- **Download** — one-time, resumable, progress shown, integrity-checked; or
- **Import file** — pick an existing `ggml-*.bin` you already have. **No network
  involved at all.**

Downloaded/imported files are validated before being accepted: the ggml magic
number and the hyper-parameter block that follows it. A captive-portal error page
or a truncated download is rejected and cleaned up rather than failing later at
load time.

Everything after this point is offline.

---

## Preview and export are the same layout

`com.saad.capvid.caption.CaptionLayout` is the single source of truth for which
words share a caption line and when each line is on screen. Both the on-screen
`CaptionOverlayView` and the exported `.ass` file are built from it, so line
composition and timing cannot drift apart.

Geometry is mapped from real metrics rather than guessed: the exported font size
is `previewTextSizePx × videoHeight / previewOverlayHeightPx`, and the preview's
baseline anchor is converted to libass's centre anchor using real
`Paint.FontMetrics`.

**Some effects cannot be reproduced by libass and are not claimed to be.**
Gradient fills burn in as a solid representative colour, glows become libass
blur, Android `Camera` 3D transforms burn in flat, and per-frame animations burn
in as a static treatment. `StyleAssMapper.exportNotes(styleId)` returns the exact
limitation for a style and the editor shows it during export. See `AUDIT.md` §1F
for the full list.

---

## Known limits

- Videos longer than **20 minutes** are refused with a message rather than
  exhausting the Java heap. Chunked transcription is tracked as future work.
- English only (`ggml-tiny.en`).
- Release builds are not minified. Enabling R8 requires keep rules for the JNI
  entry points and the reflectively-inflated custom Views first.

---

## Before publishing

Read `AUDIT.md` §8 and `THIRD_PARTY_NOTICES.md`. Two items are blocking and are
legal rather than engineering questions:

1. **Bundled font licences are unverified.** No licence files ship with the ten
   fonts in `app/src/main/assets/fonts`. Confirm redistribution rights or replace
   them.
2. **`ffmpeg-kit-full-gpl` links a GPL build of FFmpeg**, which makes the whole
   APK GPL. Either comply with GPLv3 (including the source offer) or move to an
   LGPL FFmpegKit variant.

---

## Project status

See [`AUDIT.md`](AUDIT.md) for the full engineering audit, the 28-item defect
list with root causes, and what has and has not been verified.
