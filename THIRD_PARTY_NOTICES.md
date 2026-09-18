# Third-party notices and licensing

Capvid ships or links the components below. This file exists so the licensing
position is written down before release rather than discovered during a Play
review.

Two entries are **unresolved and blocking** and are called out at the end.

> Nothing here is legal advice. The final determination is the developer's.

---

## Native

| Component | Version | Licence | How it is used |
|---|---|---|---|
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) | v1.9.4 (`927cfce3…`), git submodule | **MIT** (confirmed via the GitHub API: `spdx_id: MIT`) | Compiled from source into `libcapvid_native.so` for on-device speech recognition |
| [ggml](https://github.com/ggml-org/ggml) | vendored inside whisper.cpp | **MIT** | Tensor backend for whisper.cpp |
| [FFmpeg](https://ffmpeg.org) | via `ffmpeg-kit-full-gpl:8.1.7` | **LGPL-2.1+ or GPL-2.0+ depending on build configuration — this variant is GPL.** See below | Subtitle burn-in and re-encode at export |
| [libass](https://github.com/libass/libass) | via FFmpegKit | **ISC** | Renders the generated `.ass` captions |
| libx264 | via FFmpegKit `full-gpl` | **GPL-2.0+** | H.264 encoding of the exported video |
| Android NDK / libc++ | 26.1.10909125 | Apache-2.0 with LLVM exception | `c++_shared` STL |

## Java / AndroidX

| Component | Version | Licence |
|---|---|---|
| `androidx.appcompat:appcompat` | 1.6.1 | Apache-2.0 |
| `com.google.android.material:material` | 1.11.0 | Apache-2.0 |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | Apache-2.0 |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Apache-2.0 |
| `androidx.camera:*` (core, camera2, lifecycle, video, view) | 1.3.1 | Apache-2.0 |
| `com.arthenica:smart-exception-java` | 0.2.1 | **LGPL-3.0** — confirm; arthenica libraries are LGPL |
| `androidx.test.ext:junit`, `androidx.test:runner` | 1.1.5 / 1.5.2 | Apache-2.0 (test only, not shipped) |
| `junit:junit` | 4.13.2 | EPL-1.0 (test only, not shipped) |

## Build tooling

| Component | Version | Licence |
|---|---|---|
| Gradle | 8.5 (wrapper committed) | Apache-2.0 |
| Android Gradle Plugin | 8.2.0 | Apache-2.0 |

## Model weights

| Asset | Licence | Notes |
|---|---|---|
| `ggml-tiny.en.bin` | **MIT** per the whisper.cpp model releases | **Not** redistributed in the APK. Downloaded by the user, or imported by the user, at first-time setup. Confirm the terms on the distribution page before enabling the download path in a shipped build. |

---

## BLOCKING ITEM 1 — Bundled font licences are unverified

`app/src/main/assets/fonts/` contains ten font files with **no accompanying
licence text**:

```
All-Genders-Regular-v4.otf      Calligrapher-JRxaE.ttf
ChauPhilomeneOne-Regular.ttf    JavaCalligraphy-w1Pw6.ttf
Jost-Black.ttf                  KhatijaCalligraphy-0Z5o.otf
RobotoMono-Bold.ttf             RobotoMono-Regular.ttf
SoulDaisy.otf                   solid 3d.ttf
```

What can and cannot be said about them:

- **`Jost-Black.ttf`** — Jost is OFL-1.1 upstream. This copy is renamed and
  repackaged; the licence text does not travel with it here.
- **`RobotoMono-Bold.ttf` / `RobotoMono-Regular.ttf`** — Roboto Mono is
  Apache-2.0 upstream. Same caveat.
- **`All Genders v4`, `Calligrapher`, `Java Calligraphy`, `Khatija Calligraphy`,
  `Soul Daisy`, `Solid 3d`, `Chau Philomene One`** — provenance **unknown**.
  Several of these names match font-sharing sites whose per-designer terms range
  from free-for-commercial-use to personal-use-only. **Assume nothing.**

Required before shipping: for each file, either locate and bundle its licence
(and satisfy its attribution terms, e.g. an OFL notice), or replace it with an
OFL/Apache original.

If any font is replaced, update `StyleFontMap.ASS_FAMILY_NAMES` to the new file's
real family name. `tools/audit_static.py` reads the actual `name` table out of
each binary and fails the build on a mismatch, so this cannot silently regress.

## BLOCKING ITEM 2 — FFmpeg GPL

The dependency is `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.7`. The
`full-gpl` variants link FFmpeg configured with GPL-only components (including
libx264), which puts the resulting FFmpeg — and therefore the application that
links it — under **GPL**.

The developer must choose one of:

1. Distribute under the GPL, including a compliant written offer to provide the
   complete corresponding source of the FFmpeg build used; or
2. Switch to an LGPL FFmpegKit variant and drop the GPL-only encoders. Note that
   this constrains the available encoders for export, so it is a functional
   decision and not merely a paperwork change.

This is a legal decision. It is recorded here as unresolved and is **not**
something the code can settle.

---

## Attribution requirements summary

If the release proceeds, the shipped notice file must include, at minimum:

- The MIT licence text for **whisper.cpp** and **ggml**, with copyright to their
  respective authors.
- The Apache-2.0 licence text and the AndroidX NOTICE contents for the AndroidX
  and Material Components libraries.
- The licence text for **FFmpeg**, **libx264** and **libass** matching whichever
  variant is finally chosen.
- The LGPL-3.0 text for `smart-exception-java` if it is retained.
- Per-font attribution for every font retained (OFL requires the copyright and
  licence notice to accompany the font).
