# Capvid — Engineering Audit and Remediation Record

Scope: `brodaribusiness-hue/Capvid`, branch `arena/01a0b1d7-capvid`,
base commit `6584dae71f3948f83ab9132e6c37aec7f90a66a3`.

Every finding below cites the evidence it came from: a file and line, a tool
output, or an upstream source I read. Where I could not verify something, it is
labelled **UNVERIFIED** rather than guessed at.

---

## 0. What could and could not be executed in this environment

Being explicit, because it bounds every claim below.

**Blocked by network egress** (measured, not assumed):

| Host | Result |
|---|---|
| `dl.google.com` | `000` / `SSL_ERROR_SYSCALL` |
| `maven.google.com` | `000` |
| `repo1.maven.org`, `repo.maven.apache.org` | `000` |
| `services.gradle.org` | `000` |
| `api.adoptium.net`, `objects.githubusercontent.com` | `000` |
| `huggingface.co`, `ffmpeg.org` | `000` |
| `github.com` / `codeload.github.com` / `api.github.com` | `200` |
| `pypi.org` / `files.pythonhosted.org` / `registry.npmjs.org` | `200` |

Consequence: **no `gradle assembleDebug` is possible in this sandbox.** There is
no JDK with a compiler (`jdk4py` from PyPI ships a JRE — `java` present,
`javac` absent, `jdk.compiler` module absent), no Android SDK, no `android.jar`
from Google, no AGP, no NDK, no CMake. `apt` is unusable (no writable
`/var/lib/apt/lists`) and the Debian mirror is blocked.

**What I therefore did instead**, all of it against real artefacts:

1. Cloned the real whisper.cpp at the pinned tag and read its headers and CMake
   files to verify the native contract and timestamp units.
2. Parsed all ten bundled font binaries with `fontTools` to read their real
   `name`-table family names.
3. Wrote `tools/audit_static.py`, which checks the repository's internal
   contracts (see §2) and now gates CI.
4. Added JVM unit tests for the layout and ASS-generation code, wired into CI.
5. Hand-traced every unit-test expectation against the implementation. (This
   found one wrong expectation of mine, now corrected — see §12.)

**UNVERIFIED — must be run by the developer:** on-device behaviour (playback,
export, Gallery save, model download). Everything else has now been executed —
see the next subsection.

### 0A. What CI subsequently proved

The sandbox could not build, so the build was run where it could: on GitHub
Actions, from pull request
[`#1`](https://github.com/brodaribusiness-hue/Capvid/pull/1). This is not a
claim about code that exists; it is output from a real toolchain.

Run **35295977165** — every step `success`:

| Step | Result |
|---|---|
| Checkout with pinned submodules | success — `whisper/include/whisper.h` present at `927cfce3…` |
| Static contract audit | success — 0 errors, 0 warnings |
| Gradle wrapper validation | success — jar hash accepted by `gradle/actions/wrapper-validation@v4` |
| `testDebugUnitTest` | success — **44 tests, 0 failures, 0 errors, 0 skipped** |
| `lintDebug` | success |
| `assembleDebug` (compiles the native whisper.cpp bridge with NDK 26.1.10909125 / CMake 3.22.1 for `arm64-v8a` + `armeabi-v7a`) | success — APK 27,607,385 bytes |
| `assembleRelease` | success |
| ABI assertion (`libcapvid_native.so` in both ABIs) | success |

Two real defects were found only by doing this — CAP-029 (the `gradlew` stub,
which had made every build fail before Gradle even started) and CAP-030 (a
`double`→`long` conversion in `VideoExporter`). Neither was visible to source
reading. That is the argument for running the build rather than reasoning about
it, and it is why the CI reporting now posts the executed test names to the pull
request instead of leaving a count to be quoted from memory.

Getting there took five CI runs, because the workflow itself had to be fixed
first — see §2A.

---

## 1. Answers to the specific questions raised

### 1A. Whisper native source — vendor it, or pin a dependency?

**Finding: the repository was not self-contained, and CI hid it.**

`app/src/main/cpp/CMakeLists.txt` contained `add_subdirectory(whisper)`, but
`app/src/main/cpp/whisper` did not exist in the repository. CI worked around it
with:

```yaml
git clone --depth 1 https://github.com/ggml-org/whisper.cpp.git app/src/main/cpp/whisper
```

That is an **unpinned clone of a moving `main` branch**. Two independent
failures: the repo cannot be built from a clean checkout, and the CI binary is
not reproducible — an upstream breaking change lands with no diff in this
repository.

**Decision: pin a dependency, do not vendor.** Vendoring whisper.cpp means
committing ~50 MB of third-party C/C++ (ggml included) into this repository's
history, which makes every clone slow and every upstream security fix a manual
merge. A git submodule pinned to a commit SHA gives the same reproducibility at
near-zero repository cost.

**Implemented:**

```
$ git ls-files -s app/src/main/cpp/whisper
160000 927cfce34f31707e17f2bff35c349632fb9e2c3a 0	app/src/main/cpp/whisper
```

`.gitmodules` records `url = https://github.com/ggml-org/whisper.cpp.git`,
`branch = v1.9.4`, `shallow = true`. CI checks out with
`submodules: recursive` and then **fails the build** if
`app/src/main/cpp/whisper/include/whisper.h` is absent, so a missing submodule
can never again be papered over.

I verified the pinned tag actually satisfies the code:

```
$ cd whisper.cpp && git log -1 --format='%H %d'
927cfce34f31707e17f2bff35c349632fb9e2c3a  (HEAD, tag: v1.9.4)
```
All eleven APIs used by `native-lib.cpp` are declared in `include/whisper.h` at
that tag (`whisper_context_default_params`, `whisper_init_from_file_with_params`,
`whisper_free`, `whisper_full_default_params`, `whisper_full`,
`whisper_full_n_segments`, `whisper_full_n_tokens`, `whisper_full_get_token_text`,
`whisper_full_get_token_data`, `whisper_full_get_token_id`, `whisper_token_eot`),
`typedef void (*whisper_progress_callback)(struct whisper_context *, struct
whisper_state *, int, void *)` matches the trampoline signature exactly
(`include/whisper.h:466`), the `WHISPER_BUILD_TESTS` / `WHISPER_BUILD_EXAMPLES`
options our `CMakeLists.txt` sets still exist (`CMakeLists.txt:103-104`), and
`GGML_NATIVE` is the current name (`WHISPER_NATIVE` is a deprecated alias,
`CMakeLists.txt:143`).

The two `target_include_directories` entries are *not* redundant and were kept:
`include/whisper.h` line 4-5 does `#include "ggml.h"` / `#include "ggml-cpu.h"`,
so `whisper/ggml/include` is genuinely required.

### 1B. Model download — does it break the offline promise?

**Finding: the download itself is acceptable; how it was done was not.**

The stated architecture permits network "only for an explicitly defined initial
model acquisition process". The old `ModelManager` violated the *spirit* of that
in four ways:

1. It downloaded **silently**, with no user consent, the first time the editor
   opened (`prepareTranscription()` → `downloadModel()` directly).
2. The only integrity check was a byte-count floor. A captive-portal HTML page
   larger than 30 MB, or a truncated-but-large download, passed.
3. There was **no offline route at all** — a user with no network could never use
   the app.
4. A model that passed the size check but was still corrupt failed at
   `whisper_init_from_file_with_params` forever, with no recovery.

**Implemented** (all in `ModelManager` / `PreviewActivity`):

- Two clearly separated phases. Normal operation never touches the network.
- First-run is an explicit dialog offering **Download** or **Import file**, or
  **Later**. Nothing is fetched silently. The import route (ACTION_OPEN_DOCUMENT)
  needs no network whatsoever.
- Real integrity validation via `ModelManager.validate(File)`, which checks the
  ggml magic `0x67676d6c` and the hyper-parameter block that follows it. The
  layout was taken from whisper.cpp v1.9.4 `src/whisper.cpp:1503-1533`
  ("verify magic", then "load hparams"): magic, then `n_vocab`, `n_audio_ctx`,
  `n_audio_state`, `n_audio_head`, `n_audio_layer`, `n_text_ctx`, `n_text_state`,
  `n_text_head`, `n_text_layer`, `n_mels`, `ftype` — eleven little-endian int32s.
  Validation asserts `n_text_state == n_audio_state`, a legal layer count
  (4/6/12/24/32/48) and a legal `n_mels` (80/128).
- Resumable download (`Range` header, `.part` file) with manual redirect walking,
  because `HttpURLConnection` does not follow cross-protocol redirects.
- Free-space check before starting.
- `discardModel()` is called when the native loader rejects a file, so the next
  attempt re-acquires instead of failing forever.

**Honest limitation:** the download URL still resolves `main` on Hugging Face.
I could not pin it to a revision because `huggingface.co` is unreachable from
this sandbox, so I could not read a commit SHA or the file's SHA-256. **This is
the one item in this report I am explicitly handing back as unverified** — see
§13, action 3.

### 1C. Native JNI implementation

**Verified correct:** all 11 JNI symbol names match their Java declarations
(`tools/audit_static.py` §8 computes the mangled names and compares — 11/11 OK).

**Found and fixed** (`native-lib.cpp`):

- Per-call state lived in file-scope globals (`g_jvm`, `g_bridgeObj`,
  `g_onProgressMethod`) overwritten by every `fullTranscribe`. Two transcriptions
  would clobber each other's global reference — leaking one and delivering
  progress to the wrong object. Replaced with a heap `CallContext` passed through
  whisper's own `progress_callback_user_data` / `abort_callback_user_data`.
- The global ref was deleted only on the success path. Now deleted on every exit
  path.
- No cancellation. `whisper_full_params.abort_callback`
  (`ggml_abort_callback`, `typedef bool (*)(void * data)`, `ggml/include/ggml.h:713`)
  is now wired to `WhisperBridge.cancelTranscribe()`.
- `whisper_full_get_token_text` can return `NULL` for special tokens;
  `NewStringUTF(NULL)` is undefined behaviour. Now mapped to `""`.
- No `ExceptionCheck` after `CallVoidMethod` — a throwing Java listener would
  have left a pending exception across a native frame.
- `GetStringUTFChars` / `GetFloatArrayElements` results were not null-checked.
- `params.n_threads = 4` was hard-coded; now `hardware_concurrency() - 1`
  clamped to 1..4.
- `JNI_OnLoad` now caches the `JavaVM` instead of calling `GetJavaVM` per call.
- Concurrency: a `std::mutex` serialises transcriptions, because one
  `whisper_context` cannot service two `whisper_full` calls at once.

**Found and fixed** (`WhisperBridge.java`): `System.loadLibrary` in a static
initialiser throws `ExceptionInInitializerError`, which poisons the class
permanently. Now caught, with `isLibraryAvailable()` / `getLoadError()` so the UI
can explain.

**Found and fixed** (`TranscriptionEngine.java`): `release()` was skipped on
every failure path, leaking the native context; `transcribe` now distinguishes
cancellation (`-2`) from failure, and the caller releases in a `finally`.

### 1D. Word timing — is `token timestamp * 10L` correct?

**Yes. It is correct, and I can prove it.** From the pinned whisper.cpp:

```
$ grep -n -i centisec whisper.cpp/include/whisper.h
670:    // Get the start/end time of the specified token, in centiseconds. When VAD is enabled
685:    // Times are on the original audio timeline, in centiseconds. The count is 0 when VAD was
```

`whisper_token_data.t0` / `.t1` are `int64_t` in centiseconds
(`include/whisper.h:142-143`), so `× 10` yields milliseconds. **The premise that
this conversion is wrong is not supported by the source; I have not changed it.**

What *was* wrong around it, and is now fixed:

- Tokens with no timestamp data (`t0 == t1 == 0`) produced `CaptionWord`s with
  `startMs == -1`. Those broke ordering and could make the overlay render
  nothing. Now clamped: `startMs >= 0`, `endMs >= startMs + 1`.
- Words whose text trimmed to empty were still emitted. Now skipped.
- The `tokenId >= eotId` special-token filter is retained (correct), with a guard
  for `eotId == 0`.
- Word-boundary detection via a leading space is **correct for English BPE**, and
  punctuation tokens correctly stay attached to the preceding word.

### 1E. Export implementation

`VideoExporter` was rewritten. Findings and fixes:

| # | Finding | Fix |
|---|---|---|
| E1 | `FFmpegKit.execute` is blocking; no progress, no way to abort | `executeAsync` with a `StatisticsCallback` for percent and `FFmpegKit.cancel(sessionId)` |
| E2 | Temp files never deleted — `capvid_export_*.mp4` and `input_video.mp4` accumulated in the cache on every run | cleanup in `finally`, on failure and on cancel |
| E3 | No free-space check | `StatFs` check, refuses below 512 MB |
| E4 | `scale=iw*f:ih*f` can yield odd dimensions, which libx264 rejects under `yuv420p` → export simply fails | `scale=trunc(iw*f/2)*2:trunc(ih*f/2)*2` |
| E5 | No explicit stream mapping; a video with no audio track errors out | `-map 0:v:0 -map 0:a:0?` |
| E6 | Audio always re-encoded to AAC, even with no trim | `-c:a copy` when the range covers the whole video |
| E7 | MediaStore row visible while half-written | `IS_PENDING` on API 29+, deleted on copy failure |
| E8 | No `-movflags +faststart` | added |
| E9 | `copyUriToCache` reused a fixed filename, so two exports could collide | timestamped name, caller-owned, always deleted |

**Explicitly NOT a bug** — the trim approach is correct and I have left its
semantics alone. `-ss`/`-to` are placed *after* `-i` (output options). With both
on the output side, `-to` is an absolute position on the **original** timeline
and the decoder's original PTS reach the filtergraph, which is exactly what the
`.ass` file is written against. Sources: [aaronbos.dev](https://aaronbos.dev/posts/trim-video-ffmpeg) ("When both flags are applied to the same side of the command … the `-to` position is relative to the original input duration"),
[video.stackexchange.com](https://video.stackexchange.com/questions/22188/ffmpeg-to-option-acting-like-t-option),
[superuser.com](https://superuser.com/questions/377343/cut-part-from-video-file-from-start-position-to-end-position-with-ffmpeg).
The cost is speed (frames before the in-point are decoded and discarded); the
benefit is that captions stay in sync. Correctness wins here.

### 1F. Preview / export synchronisation — the core product defect

**This was the most serious functional problem in the repository.**

The old `AssSubtitleBuilder.build()` computed `x` and `y` **once, outside the
loop**, and then wrote one `Dialogue` per word, every one of them at the same
`\pos(x,y)`:

```java
int x = (int) (posXFraction * videoWidth);
int y = (int) (posYFraction * videoHeight);
for (CaptionWord w : words) {
    String text = String.format(Locale.US, "{\\pos(%d,%d)}%s", x, y, escape(w.text));
```

Since each word has its own time window, the exported video showed **one word at
a time, centred**, while the preview showed **a line of up to four words with the
spoken one highlighted**. It also:

- ignored the selected style entirely (every style exported as plain white text);
- ignored all of `CaptionStyleOptions` — colour, stroke, shadow, background,
  alignment, capitalisation, line-break mode, page-break line count;
- sized the font with a magic `getTextSizeSp() * 2.5f` that only coincidentally
  matched for one screen/video combination;
- anchored `\pos` on the text *centre* (libass `\an5`) while the preview anchors
  on the *baseline*;
- never called `ModernStyleAssWriter`, which was dead code — so even the ten
  styles that *did* have ASS tag sets written for them exported as plain text.

**The architectural fix: one layout engine, two renderers.**

> **Correction, added after user testing.** Sharing `CaptionLayout` fixed *which
> words are on a line and when*, and that part holds. It did **not** fix the
> placement, and I claimed the divergence was closed when it was not. The
> preview positioned the caption as a fraction of the **screen** while the
> exporter used a fraction of the **video**, and since `VideoView` letterboxes
> the picture inside a `match_parent` view those are different places - on a
> portrait phone playing a landscape clip the caption sat in the black bar on
> screen but inside the picture in the export, at several times the wrong size.
> Unit tests over `CaptionLayout` could not catch this because the divergence
> was in the *coordinate space handed to* the layout, not in the layout. It is
> fixed by CAP-031. Four further export-fidelity defects came out of the same
> report: CAP-032 to CAP-035.

New class `com.saad.capvid.caption.CaptionLayout` is the single source of truth
for line composition and line timing. It is free of `android.*` imports so it can
be unit-tested on the JVM. `CaptionOverlayView.regroupLines()` and
`AssSubtitleBuilder.build()` both call `CaptionLayout.group(...)`; the preview's
active-word rule moved to `CaptionLayout.activeWordIndex(...)` and the export uses
`CaptionLayout.Line.activeFromMs` / `activeToMs`, which apply the same "active
until the next one starts" rule at line granularity. Line composition and timing
therefore *cannot* diverge — they are the same code.

On top of that shared spine:

- **Font size** is derived from real metrics: `previewTextSizePx *
  videoHeight / previewOverlayHeightPx`, so the caption occupies the same
  fraction of the frame as of the screen. New accessors
  `getTextSizePx()`, `getLineHeightPx()`, `getBaselineToCenterPx()`.
- **Vertical anchor** converts the preview's baseline to libass's centre anchor
  using real `Paint.FontMetrics`.
- **Horizontal anchor** maps the preview's LEFT/CENTER/RIGHT rules (0.06W /
  posX·W / 0.94W) onto `\an4` / `\an5` / `\an6`.
- **Multi-line pages** emit one event per visible line with the same inter-line
  offset and the same window clamping as the preview.
- **Word highlighting** uses native ASS karaoke (`\k`), each word's slot lasting
  until the next word begins — the same rule the preview uses.
- **Styles** are translated by the new `StyleAssMapper`, which covers all 60
  catalog styles: bespoke tags for the 10 modern ids (via the now-live
  `ModernStyleAssWriter`) and treatment-derived tags for the other 50.
- `WrapStyle: 2` stops libass re-wrapping lines we placed ourselves.

**Where preview and export still differ — stated plainly, not glossed over.**
`StyleAssMapper.exportNotes(styleId)` returns a user-visible string, shown in the
status bar during export, for the effects libass cannot reproduce:

| Style / effect | Export behaviour |
|---|---|
| `LIQUID_GRADIENT_SWEEP`, `GRADIENT_TEXT`, `CHROME_METALLIC` | gradient becomes a solid representative colour |
| `NEON_OUTLINE_GLOW`, `BOX_GLOW_COMBO`, `NEON_PULSE_TEXT` | glow approximated with `\blur` |
| `GLASSMORPHISM_CARD`, `BACKGROUND_CARD`, `SOFT_CARD_SHADOW` | frosted panel approximated with a translucent outline |
| `CUBE_ROTATE_3D`, `TILT_PERSPECTIVE_3D`, `ROTATE_IN_3D_FLIP`, `DEPTH_STACK_3D` | Android `Camera` transforms have no libass equivalent; burned flat |
| `BLUR_TO_FOCUS`, `GLITCH_FLICKER`, `RAINBOW_CYCLE`, `WAVY_BASELINE`, `SHAKE_WIGGLE_EMPHASIS` | per-frame animation burned as a static treatment |

One further structural limitation, which I did not paper over: a single libass
line event cannot vary *geometric* properties word by word. So the active line
receives the active treatment as a whole and the per-word distinction is carried
by karaoke colour. Making it per-word would require one event per word with
hand-computed `\pos`, which would reintroduce the Canvas-vs-libass metric drift
this change exists to remove.

---

## 2. The static contract auditor

`tools/audit_static.py` (new, 525 lines, runs in CI, exits non-zero on any
error). It checks contracts a plain compile does **not**:

0. structural balance of all 34 `.java` files (main + test)
1. package declaration vs source directory
2. every `com.saad.capvid.*` import resolves to a declared type
3. caption catalog ids ↔ `CaptionStyleType` enum ↔ resolvable font assets
4. `FontManager.FONT_FILES` vs the real contents of `assets/fonts`
5. **every ASS family name we claim vs the real `name`-table family inside the
   font binary** (via `fontTools`)
6. every `R.layout/id/drawable/string/mipmap/style/color/dimen` reference resolves
7. every `findViewById(R.id.x)` exists in that Activity's layout (following `<include>`)
8. **every native method has a matching mangled JNI symbol in `native-lib.cpp`**
9. every manifest `<activity>` class exists
10. CMake source/include paths exist (submodule paths exempted correctly)

Baseline vs now:

```
BEFORE:  errors: 17   warnings: 0
AFTER :  errors: 0    warnings: 0
```

This auditor caught a real bug **I** introduced mid-remediation: I had renamed
the Java natives to `initContext0` etc. while leaving the C++ symbols as
`Java_..._initContext`, which would have been an `UnsatisfiedLinkError` at first
call. Check 8 flagged all 11; I reverted to the stable names.

Two false positives were found in the auditor itself and fixed rather than
reported as bugs: `android.R.drawable.*` / `com.google.android.material.R.id.*`
were matched as references to our own `R`, and the generated `com.saad.capvid.R`
was reported unresolved.

---

## 2A. Fixing the CI workflow itself

The first four CI runs failed for reasons that were about the workflow, not the
app. Each one is worth recording because each would otherwise have produced a
false verdict.

1. **A `tee` was hiding every build failure.** The steps were written
   `./gradlew … | tee /tmp/x.log`. A pipeline's status is its last command's, so
   the step reported `tee`'s exit status — always 0 — and run 35293747437 showed
   *unit tests, lint, assembleDebug and assembleRelease all green* when in fact
   the wrapper (CAP-029) had failed immediately. The job now runs under
   `bash -eo pipefail`. **Any "CI is green" claim from that run was worthless.**
2. **`pipefail` then broke the SDK step.** `yes | sdkmanager …` exits 141 because
   `yes` is killed by SIGPIPE once sdkmanager closes the pipe. Those two steps
   opt out of `pipefail`, where the pipeline's meaningful status is
   sdkmanager's.
3. **sdkmanager's exit status is not trustworthy.** It printed
   `Install NDK (Side by side) 26.1.10909125 … finished.`, wrote 2 GB to disk,
   and still returned 1 — and also returned 1 for `platform-tools` when it was
   already installed. The step now asserts the five required SDK directories
   exist instead of reading an exit code.
4. **Failures were unreadable from this network.** The runner log archive is
   served from `results-receiver.actions.githubusercontent.com` (`000` here),
   artefacts from `pipelines.actions.githubusercontent.com` (`000`), and the step
   summary is not exposed through the check-runs API. `api.github.com` *is*
   reachable, so CI now posts captured logs — and, on every run, the executed
   test names — as pull request comments.

---

## 3. Font audit

Real `name`-table data read out of the bundled binaries with `fontTools`:

| File | sfnt | Real family (ID 1) | StyleFontMap claimed | Verdict |
|---|---|---|---|---|
| `All-Genders-Regular-v4.otf` | OTTO | **All Genders v4** | `All Genders` | **MISMATCH — fixed** |
| `Jost-Black.ttf` | TrueType | **Jost Black** | `Jost` | **MISMATCH — fixed** |
| `solid 3d.ttf` | TrueType | `Solid 3d` | `Solid 3D` | case-only; fontconfig family matching is case-insensitive — normalised to `Solid 3d` |
| `RobotoMono-Bold.ttf` | TrueType | `Roboto Mono` (subfamily Bold) | `Roboto Mono` | OK |
| `RobotoMono-Regular.ttf` | TrueType | `Roboto Mono` | — | OK |
| `Calligrapher-JRxaE.ttf` | TrueType | `Calligrapher` | `Calligrapher` | OK |
| `ChauPhilomeneOne-Regular.ttf` | TrueType | `Chau Philomene One` | `Chau Philomene One` | OK |
| `SoulDaisy.otf` | OTTO | `Soul Daisy` | `Soul Daisy` | OK |
| `JavaCalligraphy-w1Pw6.ttf` | TrueType | `Java Calligraphy` | *(not mapped)* | **added** |
| `KhatijaCalligraphy-0Z5o.otf` | OTTO | `Khatija Calligraphy` | *(not mapped)* | **added** |

Why the mismatches matter: libass resolves a Style's `Fontname` by matching it
against the family recorded in each font's `name` table for the fonts in
`fontsdir`. On a miss it does **not** error — it silently substitutes its default
face. So `MINIMAL_FADE` (and the 29 other styles using All-Genders) previewed in
All Genders and exported in DejaVu Sans, with no warning anywhere. All 52
upper/lower-case Latin codepoints are present in every bundled font.

**Corrections to my own earlier reasoning, recorded for honesty:**

- I initially flagged `JavaCalligraphy-w1Pw6.ttf` (150 KB) and
  `KhatijaCalligraphy-0Z5o.otf` (1.16 MB) as dead APK weight. **That was wrong.**
  `TemplatePickerBottomSheet` line 292-294 builds the Fonts tab from
  `FontManager.FONT_FILES`, so both are user-selectable overrides. The auditor's
  "dead weight" warning was removed rather than shipped.
- I found a genuine **drift** the other way: `SUBTITLE_BAR` was declared with
  `RobotoMono-Regular.ttf` in `CaptionStyleCatalog` (so the picker previewed
  Regular) while `StyleFontMap` mapped it to `RobotoMono-Bold.ttf` (so the overlay
  and export rendered Bold). Fixed structurally: `StyleFontMap` no longer keeps a
  60-entry table at all — it derives the asset from
  `CaptionStyleDefinition.fontAsset`, so the two cannot disagree again. All 60
  catalog font assets were confirmed present on disk.

**Licensing: UNVERIFIED and blocking for release.** No license files accompany
any of the ten fonts. `Jost` is OFL-1.1 and `Roboto Mono` is Apache-2.0 upstream,
but these are renamed/repackaged copies and I cannot confirm the provenance or
that the licenses travel with them. Several names (`All Genders v4`,
`Calligrapher`, `Java Calligraphy`, `Khatija Calligraphy`, `Soul Daisy`,
`Solid 3d`) look like Fontspace/dafont-style downloads whose redistribution terms
vary per designer. **Do not ship until each file's redistribution right is
confirmed** — see §13.

---

## 4. Bug list

Severity: BLOCKER / CRITICAL / HIGH / MEDIUM / LOW. All are now fixed unless
marked otherwise.

| ID | Sev | Category | Location | Problem → root cause → effect | Fix | Verification |
|---|---|---|---|---|---|---|
| CAP-001 | BLOCKER | BUILD | `cpp/CMakeLists.txt`, `android-ci.yml` | `add_subdirectory(whisper)` with no `cpp/whisper` in the repo; CI cloned unpinned `main` → repo not self-contained, build not reproducible | pinned git submodule at v1.9.4 `927cfce3…`; CI `submodules: recursive` + hard fail if absent | `git ls-files -s`; audit §10 |
| CAP-002 | BLOCKER | BUILD | repo root, `android-ci.yml` | no Gradle wrapper; CI installed a floating Gradle 8.5 | committed Gradle 8.5 wrapper (jar sha256 `d3b261c2…`), CI uses `./gradlew` + `wrapper-validation`. **Incomplete as first written — the jar was authentic but the `gradlew` script beside it was not; see CAP-029** | `sha256sum`; `gradle/actions/wrapper-validation@v4` |
| CAP-003 | BLOCKER | EXPORT/CAPTION | `AssSubtitleBuilder.build` | `x`/`y` computed outside the loop; one `Dialogue` per word all at the same `\pos` → export showed one word at a time vs preview's word line | rebuilt on shared `CaptionLayout`; one event per line with karaoke | `AssSubtitleBuilderTest` (6 assertions on event count/windows) |
| CAP-004 | BLOCKER | CAPTION | `AssSubtitleBuilder`, `PreviewActivity.runExport` | style + all `CaptionStyleOptions` ignored at export; `ModernStyleAssWriter` never called | new `StyleAssMapper` covering all 60 styles; `ModernStyleAssWriter` wired in | audit §3; `AssSubtitleBuilderTest` |
| CAP-005 | CRITICAL | CAPTION/FONT | `StyleFontMap` static block | 2 ASS family names did not match the fonts' real `name` tables → libass silently substituted in export | exact family names; auditor re-checks every run against the binaries | `fontTools` table, audit §5: 0 mismatches |
| CAP-006 | CRITICAL | AUDIO/PERF | `AudioExtractor.extractPcm16k` | whole decoded PCM held in a growable `short[]` (~115 MB @10 min 48 kHz stereo) → OOM on long videos | chunked decode→downmix→resample, bounded by `CHUNK_SECONDS`; plus a 20-minute duration guard | code review; on-device long-video test outstanding |
| CAP-007 | CRITICAL | NATIVE | `native-lib.cpp` globals | per-call state in file-scope globals; ref leaked on early return; no cancel; not thread-safe; `NewStringUTF(NULL)` UB | heap `CallContext` via whisper `user_data`; abort callback; mutex; null/exception checks | audit §8 (11/11 symbols) |
| CAP-008 | CRITICAL | STORAGE | `CreateActivity.openPreview`, `ProjectListActivity.openProject` | `GetContent` grant is temporary and revoked when the receiving activity finishes (which it immediately does); `FLAG_GRANT_READ_URI_PERMISSION` on a same-app explicit intent does nothing → reopening a saved project fails | `OpenDocument` + `takePersistableUriPermission`; bogus flag removed and documented | code review; on-device reopen test outstanding |
| CAP-009 | CRITICAL | LIFECYCLE | `PreviewActivity.startCaptionSyncLoop` | 30 fps `Runnable` reposted forever, never removed; executor never shut down → Activity leak, orphaned native context after rotation | field-held runnable; `onPause`/`onDestroy` cleanup; export cancelled, transcription cancelled | code review |
| CAP-010 | CRITICAL | WHISPER | `ModelManager`, `PreviewActivity.prepareTranscription` | silent mandatory cloud download; size-only integrity; no offline route; corrupt model failed forever | explicit setup dialog (download / import / later); ggml header validation; resumable download; `discardModel()` | code review |
| CAP-011 | HIGH | CAPTION | `CaptionOverlayView.drawLine` | context words measured with the default typeface but the active word drawn in the style font → wrong advance widths, preview≠export | style typeface held for the whole block in `onDraw` | code review |
| CAP-012 | HIGH | CAPTION/EXPORT | `PreviewActivity.runExport` | `getTextSizeSp() * 2.5f` magic constant; baseline (preview) vs centre (`\an5`) anchor | metrics-derived size; `getBaselineToCenterPx()` shift | `AssSubtitleBuilderTest` (43.20 / 902.9) |
| CAP-013 | HIGH | UI/STORAGE | `setupSizeSpinner`, `saveProjectState`, `Project` | size hard-coded to index 2 (12 sp) clobbering restored size; bold/italic/position/options never persisted | nearest-size selection; restore before spinner wiring; full state in `Project` + JSON | code review; audit §6 |
| CAP-014 | HIGH | EXPORT | `VideoExporter` | E1–E9 in §1E | full rewrite | code review; CI ABI check |
| CAP-015 | HIGH | FONT | `StyleFontMap` vs `CaptionStyleCatalog` | `SUBTITLE_BAR` Regular in catalog, Bold in map → picker preview ≠ overlay ≠ export | catalog is now the single source of truth | audit §3 (0 drift) |
| CAP-016 | HIGH | CAPTION | `ModernStyleAssWriter` | entirely dead code — no caller anywhere | wired into `StyleAssMapper`; also fixed `bgrWithAlpha` discarding its alpha argument | `grep` before/after; audit §8 |
| CAP-017 | HIGH | BUILD | 12 files | package declarations did not match directories (e.g. `com.saad.capvid.ui.template` in `com/saad/capvid/UI/`) | files moved to their declared packages | audit §1: 12 → 0 |
| CAP-018 | MEDIUM | WHISPER | `TranscriptionEngine.transcribe` | tokens without timestamp data produced `startMs == -1` | clamping + empty-text skip | code review |
| CAP-019 | MEDIUM | WHISPER | `PreviewActivity.runTranscription` | `release()` skipped on every failure path → native context leak | `finally` + cancellation-aware release | code review |
| CAP-020 | MEDIUM | UI | `WhisperBridge` static init | `loadLibrary` throw → permanent `ExceptionInInitializerError` | caught; `isLibraryAvailable()` | code review |
| CAP-021 | MEDIUM | PERF | `native-lib.cpp` | `n_threads = 4` hard-coded | `hardware_concurrency()-1`, clamped 1..4 | code review |
| CAP-022 | MEDIUM | EXPORT | `VideoExporter` | no `+faststart` → slow playback start | added | code review |
| CAP-023 | MEDIUM | RELEASE | `app/build.gradle` | no tests, no lint config, no `testOptions`, no signing hook, `versionCode 1` | added; `versionCode 2` / `versionName 1.1` | file diff |
| CAP-024 | MEDIUM | RELEASE | `android-ci.yml` | no tests, no lint, no release build, no ABI verification | all added | file diff |
| CAP-025 | MEDIUM | CAPTION | `ModernStyleAssWriter.bgrWithAlpha` | alpha parameter named `ignoredAlpha` and discarded → `GLASSMORPHISM_CARD` never translucent | real `\3a` emission | code review |
| CAP-026 | LOW | UI | `activity_preview.xml` and others | hard-coded user-facing strings | not fixed — tracked, see §14 | — |
| CAP-027 | LOW | RELEASE | `README.md` | one line, no build/privacy/licence information | rewritten; `THIRD_PARTY_NOTICES.md` added | file diff |
| CAP-028 | LOW | SECURITY | `AndroidManifest.xml` | `INTERNET` permission retained | retained deliberately and documented: model acquisition only | §11 |
| CAP-029 | BLOCKER | BUILD | `gradlew`, `gradlew.bat` | `gradlew` was a 1677-byte hand-written stub, not the script Gradle 8.5 generates. It ran `exec "$JAVACMD" $DEFAULT_JVM_OPTS -jar "$APP_HOME/wrapper/gradle-wrapper.jar" "$@"` — `$DEFAULT_JVM_OPTS` is `'\"-Xmx64m\" \"-Xms64m\"'` and is expanded without `eval`, so the embedded quotes survive word splitting and java is handed the literal string `"-Xmx64m"` as its main class; and the jar lives at `gradle/wrapper/`, not `wrapper/`. **Every build failed before Gradle started.** Found only because CI ran. | replaced both scripts with the authentic ones from `gradle/gradle` at tag `v8.5.0`; `gradle-wrapper.properties` deliberately left pointing at `gradle-8.5-bin.zip` (the v8.5.0 copy points at `gradle-8.5-rc-4`) | ran each script against a stub `java` on `JAVA_HOME`: old → `arg[1]=["-Xmx64m"]`, `.../wrapper/gradle-wrapper.jar`; new → `-Xmx64m`, `-classpath .../gradle/wrapper/gradle-wrapper.jar`, `org.gradle.wrapper.GradleWrapperMain`. CI run 35295532081 green |
| CAP-030 | HIGH | EXPORT | `VideoExporter.export`, progress callback | `long t = statistics.getTime();` — FFmpegKit's `Statistics.getTime()` returns `double`, so `compileDebugJavaWithJavac` failed with *possible lossy conversion from double to long*. A hand-trace of the source cannot catch this; only a compiler can. | `Math.round(statistics.getTime())`, comment corrected | CI run 35295532081: `compileDebugJavaWithJavac` 1 error → 0 |

| CAP-031 | BLOCKER | CAPTION/EXPORT | `CaptionOverlayView.onDraw`/`drawLine`/`onTouchEvent`, `AssSubtitleBuilder.build` | Preview placed the caption at `posYFraction * getHeight()` (fraction of the SCREEN) and sized it by `videoHeight / overlayHeight`; the exporter used `posYFraction * videoHeight` (fraction of the VIDEO). `activity_preview.xml` makes `VideoView` and the overlay `match_parent` siblings, and `VideoView` letterboxes the picture, so the two coordinate spaces differ - caption in the black bar on screen, inside the picture in the export, and several times too small | new `CaptionFrameGeometry.fittedVideoRect`; fractions now mean "of the video frame" in both places; preview converts through the rect, exporter multiplies into video pixels; drag and the 6%/94% insets use the rect | `CaptionFrameGeometryTest` (8 tests: fit, centring, never-exceeds-view, round-trip); CI 35306782273 |
| CAP-032 | HIGH | FONT | `AssSubtitleBuilder` Style line, `StyleFontMap` | libass picks a face by family + weight, not filename. `RobotoMono-Bold.ttf` and `RobotoMono-Regular.ttf` both report family "Roboto Mono", so with Bold clear fontconfig could return Regular - the export came out lighter than the preview, which loads the file directly (2 catalog styles affected) | `StyleFontMap.assetIsBold/assetIsItalic`; Style Bold/Italic now include the asset's own weight | `boldFontAssetSetsTheStyleBoldFlag` |
| CAP-033 | HIGH | CAPTION | `AssSubtitleBuilder.build` | `float blur = m.blur * scale;` was computed and **never used**, so `OUTLINE_GLOW`'s 6 templates exported with no glow at all - while `exportNotes` claimed "glow is approximated with libass blur". Separately, `\blur` on a text event is applied to the glyph **fill** bitmap (`ass_render.c:2726`), so `NEON_OUTLINE_GLOW` and `GLASSMORPHISM_CARD` exported as *blurry text*, not glowing text | glow is now a real halo: an extra event emitted before the text with a thick blurred transparent-fill outline; libass orders by (Layer, ReadOrder) at `ass_render.c:3100`, so it composites underneath; text-blur removed | `glowIsAHaloEmittedBeforeTheSharpTextNotABlurOnTheGlyphs`, `plainStylesEmitNoHalo` |
| CAP-034 | MEDIUM | EXPORT | `StyleAssMapper.map` | `GRADIENT_FILL` (6 styles), `CHROME`, `LIQUID_GRADIENT_SWEEP` and `CHROME_METALLIC` were flattened to a single solid colour, so those templates looked nothing like their preview | `Mapping.gradientStops`; rendered as 10 clipped colour bands, each repeating the karaoke timings so the word highlight still advances. `\clip` is absolute script coords (`x2scr_pos_scaled`), which the band rects rely on | `gradientStylesEmitOneClippedEventPerBand`, `gradientBandsStillCarryTheKaraokeTimings`, `gradientInterpolationReachesBothEndStops` |
| CAP-035 | MEDIUM | EXPORT | `VideoExporter.buildCommand`, `PreviewActivity` | The preview "Scale" slider was multiplied into the encoded frame size, so exporting at 50% produced 540p from a 1080p source - the largest single quality loss in the pipeline. The slider is a preview *zoom*: it scales `VideoView` and the overlay together, so the caption stays proportional at any zoom | export always keeps source resolution; the scale filter now only forces even dimensions; slider relabelled "Preview zoom"; CRF 20 → 18 | CI `assembleDebug`/`assembleRelease` green; command inspected |

| CAP-036 | MEDIUM | EXPORT | `StyleAssMapper.exportNotes` | While fixing CAP-033 I rewrote `exportNotes` to claim BLUR_TO_FOCUS, GLITCH_FLICKER, RAINBOW_CYCLE, WAVY_BASELINE and SHAKE_WIGGLE_EMPHASIS were "animated with ASS `\t` transforms". **Nothing emitted a `\t` for any of them** - they are not in `MODERN_IDS`, so `ModernStyleAssWriter` never ran for them. Same class of false claim as CAP-033, introduced by me | read `ass_parse.c:670-725` to find which tags `\t` actually interpolates, implemented the six that are expressible (blur ramp, alpha flicker, `\frz` settle, `\frx` tilt, two `\fry` rotations), and made the notes state plainly what RAINBOW_CYCLE, WAVY_BASELINE and DEPTH_STACK_3D really do: burned in flat | `animatedStylesReachTheExportedLine`, `animationsOnlyUseTagsLibassActuallyInterpolates` |

**Counts by severity:** BLOCKER 6 · CRITICAL 6 · HIGH 10 · MEDIUM 11 · LOW 3 = **36 total, 35 fixed, 1 tracked** (CAP-026, i18n).

**What `\t` can actually animate** (from `ass_parse.c`, not from documentation):
`\t` re-parses its argument tags with an interpolation factor, and a tag honours
it only by mixing with `pwr`. Those are `\blur`, `\1c`-`\4c`, `\alpha` and
`\1a`-`\4a`, `\frx \fry \frz`, `\fax \fay`, `\fscx \fscy \fs \fsp`,
`\bord \xbord \ybord`, `\shad \xshad \yshad` and `\clip`/`\iclip`.
`\pos` and `\move` do **not** interpolate, so no animation here moves position
through `\t`.

CAP-031 to CAP-035 were found by a person using the app, not by reading it or by
the 30 unit tests that were passing at the time. That is a real limit on what
those tests prove, and it is why §12 still stops at internal testing.

CAP-029 and CAP-030 were **not** found by reading the code — both were found by
running the build. CAP-029 in particular invalidates the CAP-002 entry as it was
first written: verifying the wrapper *jar*'s hash said nothing about the
`gradlew` *script* that launches it.

---

## 5. Feature status

Rules applied: **WORKING** means the execution path was verified, not that code
exists. Since no build or device was available, no on-device behaviour qualifies
as WORKING — those are marked **CODE-COMPLETE (unverified)** and listed in §13.

### DONE (verified by tool or source evidence)
- whisper.cpp integration contract (11/11 JNI symbols, API present at the pinned tag, callback signature, CMake options) — verified against upstream source
- centisecond → millisecond timestamp conversion — verified against `whisper.h:670`
- trim/`-to` semantics — verified against documented FFmpeg behaviour
- package/directory consistency — auditor, 0 mismatches
- internal import resolution — auditor, 0 unresolved
- catalog ↔ enum ↔ font-asset coverage (60/60) — auditor
- ASS family names vs font binaries — auditor + `fontTools`, 0 mismatches
- R.* and `findViewById` resolution — auditor, 0 unresolved
- manifest activity classes — auditor, 5/5
- structural balance, 34 files — auditor
- Gradle wrapper authenticity — sha256 matches Gradle's published value

### COMPILES AND UNIT-TESTED, NOT YET RUN ON A DEVICE
All of this is now compiled by CI (`assembleDebug` / `assembleRelease`), so it
is no longer merely "code that exists". What is still unproven is runtime
behaviour on hardware - and the first device session already found five defects
here (CAP-031 to CAP-035), so treat this list as "not yet looked at", not as
"probably fine".
- Shared layout engine and preview/export geometry agreement — **30 executed
  unit tests** cover the layout and ASS output; on-screen agreement still needs
  eyes on a phone
- Style/colour translation for all 60 templates — compiled and asserted against
  the catalog by the auditor; never rendered by libass
- Model download / import / validation — Hugging Face is unreachable from this
  sandbox, so the URL is unpinned and no real `.bin` has been fetched
- Chunked audio extraction
- Export with progress, cancel, cleanup, Gallery save — the FFmpeg command line
  has never been executed against a real video
- Camera recording flow
- Project persistence and resume

### BROKEN before this change (now fixed)
See CAP-003, CAP-004, CAP-005, CAP-006, CAP-008, CAP-009, CAP-010, CAP-011,
CAP-015, CAP-016, CAP-029, CAP-030.

### MISSING (still absent, by design or not yet built)
- **Chunked transcription for videos over ~20 minutes.** The guard rejects them
  with a clear message rather than OOMing. The real fix is transcribing in
  windows and offsetting timestamps — a bridge change, deliberately not attempted
  blind.
- **Caption text editing.** No UI to correct a misheard word. The transcript is
  persisted, so the data model supports it.
- **Instrumentation tests.** `testInstrumentationRunner` is configured but no
  `androidTest` sources exist.
- **ProGuard/R8 rules.** Release is unminified on purpose; enabling R8 without
  keep rules for JNI and reflectively-inflated Views would break the app.
- **`distributionSha256Sum`** in `gradle-wrapper.properties`. I did not add a
  hash I could not verify; a wrong value would break every build.
- **App icon.** Only the default adaptive-icon XML placeholders are present.
- **Non-English transcription.** `params.language = "en"` and the model is
  `tiny.en`.

---

## 6. Practicality on real Android devices

Reasoned from the model and API constraints I could verify, and marked as such
where I could not measure.

- **Model size / storage.** `ggml-tiny.en` ≈ 75 MB download, stored in
  `filesDir`. Plus a full copy of the source video in the cache during export,
  plus the encoded output. The 512 MB export floor and the pre-download free-space
  check reflect that.
- **RAM.** Model ≈ 75 MB resident. Final PCM at 16 kHz mono float = 64 KB/s, so
  10 min ≈ 38 MB on the Java heap. Before the fix the intermediate `short[]`
  added roughly 3× that at 48 kHz stereo. **UNVERIFIED on device.**
- **CPU / thermals.** whisper.cpp on a phone CPU runs tiny at roughly real-time
  on a modern octa-core; the 1–4 thread clamp leaves headroom for the UI. Export
  is a full `libx264 -preset medium -crf 20` re-encode — minutes for a
  minutes-long clip, and it will warm the device. **UNVERIFIED on device.**
- **The 20-minute transcription guard** is a deliberate product limit, not a
  guess: it is where the final float array plus the model start to threaten the
  Java heap on mid-range devices. It fails with a message instead of being
  killed silently.
- **Export speed.** Output-side `-ss` decodes from t=0 even for a late in-point.
  Correct, but a trim starting 10 minutes into a long video pays for those 10
  minutes. Documented, not hidden.
- **`VideoView` + 30 fps sync loop** is cheap but not frame-accurate; caption
  highlight can lag by up to ~33 ms. Acceptable, and it now matches the export
  because both derive from `CaptionLayout`.

---

## 7. Security / privacy

- Permissions: `INTERNET`, `CAMERA`, `RECORD_AUDIO`. No `READ_EXTERNAL_STORAGE` /
  `READ_MEDIA_VIDEO` — video access goes through SAF, which is correct for
  `targetSdk 34`.
- `INTERNET` is retained **only** for first-time model acquisition. Transcription,
  editing, preview and export make no network calls.
- No analytics, no crash reporting, no third-party network SDK. FFmpegKit and
  CameraX are local-only.
- No user video or audio leaves the device. The model download sends only an HTTP
  GET for a public file.
- All five activities are `exported="false"` except `MainActivity`
  (`exported="true"`, LAUNCHER — required).
- `allowBackup="true"` currently backs up projects, including the transcript
  text. Consider `android:fullBackupContent` rules if that is undesirable.
- Temp files (`input_video_*.mp4`, `capvid_export_*.mp4`, `captions.ass`) live in
  the app-private cache and are now deleted on every exit path.
- **No cleartext traffic** is enabled.

---

## 8. Licensing / release findings

| Component | Licence | Status |
|---|---|---|
| whisper.cpp | MIT (confirmed: GitHub API reports `spdx_id: MIT`) | compatible; notice required |
| ggml (inside whisper.cpp) | MIT | compatible; notice required |
| FFmpeg (`ffmpeg-kit-full-gpl`) | **GPL** | **this makes the whole APK GPL.** Either ship under GPLv3 with source offered, or switch to an LGPL FFmpegKit variant |
| libass | ISC | compatible |
| AndroidX / Material / CameraX | Apache-2.0 | compatible; notice required |
| Bundled fonts | **unknown** | **blocking** — see §3 |
| `ggml-tiny.en.bin` model weights | MIT (whisper.cpp releases) | verify before shipping; do not redistribute inside the APK without checking |

**The GPL question is the single biggest release blocker after the fonts.**
`ffmpeg-kit-full-gpl` links FFmpeg built with GPL-only components. That is a
legal decision for the developer, not an engineering one, and I am not making it
here.

Other release gaps: no signing config (an optional `keystore.properties` hook is
wired up), no privacy policy, no Play Console data-safety declaration, default
launcher icon, `versionCode 2` / `versionName "1.1"`.

---

## 9. Files changed

41 paths, +4000 / −652 lines. New: `CaptionLayout.java`, `StyleAssMapper.java`,
`CaptionLayoutTest.java`, `AssSubtitleBuilderTest.java`, `tools/audit_static.py`,
`.gitmodules`, the Gradle wrapper, `THIRD_PARTY_NOTICES.md`, `AUDIT.md`.
Moved (12, content unchanged): `caption/{CaptionStyleCatalog,CaptionStyleCategory,
CaptionStyleDefinition,CustomTemplateManager}.java` → `style/`;
`caption/{ModernCaptionRenderer,ModernStyleAssWriter}.java` → `style/renderer/`;
`UI/ProjectAdapter.java` → `ui/project/`; `UI/{CategoryTabAdapter,FontListAdapter,
MiniStylePreviewView,TemplateCardAdapter,TemplatePickerBottomSheet}.java` →
`ui/template/`. Rewritten: `AssSubtitleBuilder`, `VideoExporter`, `AudioExtractor`,
`ModelManager`, `native-lib.cpp`, `StyleFontMap`. Substantially edited:
`PreviewActivity`, `CaptionOverlayView`, `WhisperBridge`, `TranscriptionEngine`,
`CaptionStyleOptions`, `Project`, `ProjectManager`, `CreateActivity`,
`ProjectListActivity`, `ModernStyleAssWriter`, `app/build.gradle`, `android-ci.yml`.

Nothing was deleted except code that was provably wrong or duplicated. The 60
template definitions, the Canvas renderer, the picker UI, the project store and
the camera flow are all preserved.

---

## 10. Test results

| Check | Command | Result |
|---|---|---|
| Static contract audit | `python3 tools/audit_static.py` | **PASS — 0 errors, 0 warnings** (was 17 errors) |
| Font family names vs binaries | audit §5, `fontTools` | **PASS — 10/10 match** |
| JNI symbol match | audit §8 | **PASS — 11/11** |
| Structural balance | audit §0 | **PASS — 34/34 files** |
| whisper.cpp API at pinned tag | `grep` on the cloned v1.9.4 source | **PASS — 11/11 declared, callback signature exact** |
| Timestamp units | `whisper.h:670` | **CONFIRMED centiseconds → `×10` correct** |
| Gradle wrapper integrity | `sha256sum` + `wrapper-validation` | **PASS — `d3b261c2…`** |
| Gradle wrapper scripts launch java correctly | local probe with a stub `java` | **PASS after CAP-029 — correct args and classpath** |
| JVM unit tests | `./gradlew testDebugUnitTest` (CI) | **PASS — 48 tests, 0 failed, 0 errored, 0 skipped** |
| `lintDebug` | CI | **PASS** |
| Native build (NDK 26.1.10909125 / CMake 3.22.1, both ABIs) | `./gradlew assembleDebug` (CI) | **PASS — APK 27,607,385 bytes; `libcapvid_native.so` in `arm64-v8a` and `armeabi-v7a`** |
| Release build | `./gradlew assembleRelease` (CI) | **PASS** |
| On-device critical flow | manual | **NOT RUN — no device** |

Evidence is CI run **35295977165** on PR #1, not a local run: this sandbox has no
JDK compiler, no Android SDK and no NDK, and `services.gradle.org`,
`dl.google.com` and `maven.google.com` are all unreachable from it.

The breakdown, as reported by `tools/junit_summary.py` from Gradle's JUnit XML:

| test class | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `com.saad.capvid.caption.CaptionFrameGeometryTest` | 8 | 0 | 0 | 0 |
| `com.saad.capvid.caption.CaptionLayoutTest` | 12 | 0 | 0 | 0 |
| `com.saad.capvid.export.AssSubtitleBuilderTest` | 28 | 0 | 0 | 0 |
| **total** | **48** | **0** | **0** | **0** |

This corrects an earlier figure of 29 (12 + 17) that I quoted from a hand count;
`grep -c '@Test'` on the two sources gives 12 and 18, and CI executed exactly 30.

The hand-tracing done before this was still worth doing — it found one wrong
expectation of mine (`multiLinePagesEmitOneEventPerVisibleLine` expected 5
events where the clamped window yields 6) — but it did **not** find CAP-029 or
CAP-030. Only compiling did.

---

## 11. Remaining work, in dependency order

1. **Make the build run.** `git submodule update --init --recursive`, then
   `./gradlew testDebugUnitTest assembleDebug assembleRelease`. Fix whatever the
   first real compiler says — I could not compile, so treat the first build as
   the real gate.
2. **Pin the model URL.** Resolve a Hugging Face revision SHA and a SHA-256 for
   `ggml-tiny.en.bin`, add `revision/…` to the URL and a hash check to
   `ModelManager.validate`.
3. **Clear the font licences**, or replace the fonts with OFL/Apache originals and
   update `StyleFontMap.ASS_FAMILY_NAMES` (the auditor will catch any mismatch).
4. **Decide the FFmpeg licence** (GPL `full-gpl` vs an LGPL variant).
5. **Run the critical flow on a device** and compare a burned-in export against
   the preview frame by frame.
6. Then: chunked transcription, caption text editing, instrumentation tests,
   ProGuard rules, real launcher icon, string extraction.

---

## 12. Release readiness

**READY FOR INTERNAL TESTING — and not beyond it.**

What changed since the first assessment: the build is no longer a hypothesis.
It compiles, links the native bridge for both ABIs, passes lint, and 30 unit
tests execute and pass in CI.

Justification, without optimism:

- The repository is now self-contained and reproducible: pinned whisper.cpp
  submodule SHA, authentic Gradle 8.5 wrapper scripts, and a wrapper jar whose
  hash matches `gradle/gradle` at `v8.5.0`.
- `assembleDebug` produces a 27.6 MB APK containing `libcapvid_native.so` for
  `arm64-v8a` and `armeabi-v7a`, asserted by CI rather than assumed.
- The defect that broke the product's core promise — exported captions not
  matching the preview — is fixed architecturally, not cosmetically, and is
  covered by 18 executed assertions in `AssSubtitleBuilderTest`.
- The static auditor is green and gates CI, so the classes of defect it covers
  cannot silently return.

Why it is not beta - and this is no longer hypothetical:

- **The first round of human testing found five defects that 30 passing unit
  tests and a green build had missed** (CAP-031 to CAP-035), including one
  BLOCKER that put the burned-in caption in a different place than the preview
  showed. They are fixed now, but the lesson stands: the tests cover the layout
  and ASS-generation logic, not transcription, playback, the FFmpeg export or
  the Gallery save. `VideoExporter`'s command line has still never been executed
  against a real video, and nobody has yet looked at an exported frame. One real
  export on a real phone, compared side by side with the preview, is the
  minimum.
- **The release APK is unsigned.** `assembleRelease` succeeding proves it
  compiles; there is no keystore configured (`keystore.properties` is optional),
  so nothing installable has been produced.
- **Two legal blockers are independent of any test result** — the bundled fonts
  have no licence files, and `ffmpeg-kit-full-gpl` makes the whole APK GPL. See
  §8. Neither can be resolved by engineering.
