package com.saad.capvid;

import android.media.MediaMetadataRetriever;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.saad.capvid.audio.AudioExtractor;
import com.saad.capvid.caption.CaptionOverlayView;
import com.saad.capvid.export.AssSubtitleBuilder;
import com.saad.capvid.export.VideoExporter;
import com.saad.capvid.font.FontManager;
import com.saad.capvid.font.StyleFontMap;
import com.saad.capvid.model.CaptionWord;
import com.saad.capvid.project.Project;
import com.saad.capvid.project.ProjectManager;
import com.saad.capvid.style.CaptionStyleDefinition;
import com.saad.capvid.style.CaptionStyleOptions;
import com.saad.capvid.ui.template.TemplatePickerBottomSheet;
import com.saad.capvid.whisper.ModelManager;
import com.saad.capvid.whisper.TranscriptionEngine;
import com.saad.capvid.whisper.WhisperBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PreviewActivity extends AppCompatActivity {

    private VideoView videoView;
    private CaptionOverlayView captionOverlay;
    private TextView statusText;
    private ProgressBar progressBar;
    private ImageButton playPauseButton;
    private SeekBar seekBar;
    private boolean userSeeking = false;
    private Runnable syncRunnable;
    private ActivityResultLauncher<String[]> modelImportLauncher;
    private volatile TranscriptionEngine transcriptionEngine;

    private Uri videoUri;
    private List<CaptionWord> captionWords = new ArrayList<>();

    private long trimStartMs = 0;
    private long trimEndMs = -1; // -1 until video duration is known (set in onPrepared)
    /**
     * Preview zoom only. It scales the VideoView and the caption overlay
     * together, so the caption keeps the same size relative to the picture at
     * any zoom, and VideoExporter no longer applies it to the encoded frame -
     * exporting at 50% used to produce a 540p file from a 1080p source.
     */
    private float scaleFactor = 1f;

    private ProjectManager projectManager;
    private Project currentProject;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Total duration of the source video; -1 until the player is prepared. */
    private long videoDurationMs = -1;

    /** Export state. The Export button doubles as Cancel while one is running. */
    private Button exportButton;
    private boolean exportRunning = false;
    private VideoExporter activeExporter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        videoView = findViewById(R.id.videoView);
        captionOverlay = findViewById(R.id.captionOverlay);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        playPauseButton = findViewById(R.id.btnPlayPause);
        seekBar = findViewById(R.id.videoSeekBar);
        exportButton = findViewById(R.id.btnExport);
        Button chooseTemplateButton = findViewById(R.id.btnChooseTemplate);

        TextView btnTrimToggle = findViewById(R.id.btnTrimToggle);
        TextView btnScaleToggle = findViewById(R.id.btnScaleToggle);
        View trimPanel = findViewById(R.id.trimPanel);
        View scalePanel = findViewById(R.id.scalePanel);
        TextView trimRangeLabel = findViewById(R.id.trimRangeLabel);
        com.google.android.material.slider.RangeSlider trimRangeSlider = findViewById(R.id.trimRangeSlider);
        TextView scaleLabel = findViewById(R.id.scaleLabel);
        com.google.android.material.slider.Slider scaleSlider = findViewById(R.id.scaleSlider);

        btnTrimToggle.setOnClickListener(v -> {
            boolean show = trimPanel.getVisibility() != View.VISIBLE;
            trimPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            scalePanel.setVisibility(View.GONE);
            setChipSelected(btnTrimToggle, show);
            setChipSelected(btnScaleToggle, false);
        });

        btnScaleToggle.setOnClickListener(v -> {
            boolean show = scalePanel.getVisibility() != View.VISIBLE;
            scalePanel.setVisibility(show ? View.VISIBLE : View.GONE);
            trimPanel.setVisibility(View.GONE);
            setChipSelected(btnScaleToggle, show);
            setChipSelected(btnTrimToggle, false);
        });

        trimRangeSlider.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            trimStartMs = values.get(0).longValue();
            trimEndMs = values.get(1).longValue();
            trimRangeLabel.setText("Trim: " + formatMs(trimStartMs) + " - " + formatMs(trimEndMs));
            if (fromUser && videoView.getCurrentPosition() < trimStartMs) videoView.seekTo((int) trimStartMs);
            if (fromUser) saveProjectState();
        });

        scaleSlider.addOnChangeListener((slider, value, fromUser) -> {
            scaleFactor = value / 100f;
            scaleLabel.setText("Preview zoom: " + (int) value + "%");
            applyScalePreview();
            if (fromUser) saveProjectState();
        });

        modelImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) startModelImport(uri);
                    else showModelSetupDialog();
                });

        videoUri = getIntent().getParcelableExtra("videoUri");

        projectManager = new ProjectManager(this);
        String incomingProjectId = getIntent().getStringExtra("projectId");
        currentProject = incomingProjectId != null ? projectManager.findById(incomingProjectId) : null;
        if (currentProject == null) currentProject = projectManager.findByVideoUri(videoUri.toString());
        boolean isResumed = currentProject != null;
        if (currentProject == null) {
            currentProject = new Project();
            currentProject.id = java.util.UUID.randomUUID().toString();
            currentProject.videoUri = videoUri.toString();
            currentProject.name = fileNameFromUri(videoUri);
            currentProject.createdAtMs = System.currentTimeMillis();
        }
        // Restore trim/scale/style BEFORE the video-prepared listener runs,
        // so it doesn't overwrite the resumed values with fresh defaults.
        trimStartMs = currentProject.trimStartMs;
        trimEndMs = currentProject.trimEndMs;
        scaleFactor = currentProject.scaleFactor;

        restoreOverlayState();

        playPauseButton.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
                playPauseButton.setImageResource(android.R.drawable.ic_media_play);
            } else {
                videoView.start();
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) videoView.seekTo(progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { userSeeking = false; }
        });

        videoView.setVideoURI(videoUri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            videoDurationMs = mp.getDuration();
            seekBar.setMax(mp.getDuration());
            if (trimEndMs <= 0) trimEndMs = mp.getDuration();
            trimRangeSlider.setValueFrom(0f);
            trimRangeSlider.setValueTo(Math.max(1f, (float) mp.getDuration()));
            trimRangeSlider.setValues((float) trimStartMs, (float) trimEndMs);
            trimRangeLabel.setText("Trim: " + formatMs(trimStartMs) + " - " + formatMs(trimEndMs));
            scaleLabel.setText("Preview zoom: " + (int) (scaleFactor * 100) + "%");
            scaleSlider.setValue(scaleFactor * 100f);
            applyScalePreview();
            if (trimStartMs > 0) videoView.seekTo((int) trimStartMs);
            videoView.start();
            startCaptionSyncLoop();
            publishVideoDisplaySize();
        });

        exportButton.setOnClickListener(v -> {
            if (exportRunning) {
                if (activeExporter != null) activeExporter.cancel();
                statusText.setText("Cancelling export...");
            } else {
                runExport();
            }
        });

        chooseTemplateButton.setOnClickListener(v -> openTemplatePicker());

        if (isResumed && !currentProject.words.isEmpty()) {
            captionWords = currentProject.words;
            captionOverlay.setWords(captionWords);
            statusText.setText("Ready — " + captionWords.size() + " words (resumed)");
            progressBar.setProgress(100);
        } else {
            prepareTranscription();
        }
    }

    /**
     * Pushes the persisted project state into the overlay.
     *
     * <p>VideoProject.textSizeSp / .bold / .italic are the stored source of
     * truth; they are copied into the options object here so the picker opens
     * showing the real values and the two can never drift apart.
     */
    private void restoreOverlayState() {
        try {
            captionOverlay.setStyleType(CaptionOverlayView.CaptionStyleType.valueOf(currentProject.styleId));
        } catch (IllegalArgumentException ignored) {
            // The saved style id no longer exists in the enum; keep the default.
        }
        if (currentProject.options != null) {
            CaptionStyleOptions restored = currentProject.options;
            restored.textSizeSp = currentProject.textSizeSp;
            restored.bold = currentProject.bold;
            restored.italic = currentProject.italic;
            captionOverlay.setStyleOptions(restored);
        }
        captionOverlay.setTextSizeSp(currentProject.textSizeSp);
        captionOverlay.setBold(currentProject.bold);
        captionOverlay.setItalic(currentProject.italic);
        captionOverlay.setPositionFractions(currentProject.posXFraction, currentProject.posYFraction);
    }

    private String fileNameFromUri(Uri uri) {
        String path = uri.getLastPathSegment();
        return path != null ? path : "Untitled";
    }

    private void saveProjectState() {
        if (currentProject == null) return;
        currentProject.trimStartMs = trimStartMs;
        currentProject.trimEndMs = trimEndMs;
        currentProject.scaleFactor = scaleFactor;
        currentProject.styleId = captionOverlay.getStyleType().name();
        currentProject.textSizeSp = captionOverlay.getTextSizeSp();
        currentProject.bold = captionOverlay.getBold();
        currentProject.italic = captionOverlay.getItalic();
        currentProject.posXFraction = captionOverlay.getPosXFraction();
        currentProject.posYFraction = captionOverlay.getPosYFraction();
        currentProject.options = captionOverlay.getStyleOptions().copy();
        currentProject.words = captionWords;
        currentProject.lastEditedMs = System.currentTimeMillis();
        projectManager.save(currentProject);
    }

    private void openTemplatePicker() {
        // The picker owns the whole look now, typography included, so it has to
        // be told the live values or it would open showing the defaults and
        // silently reset the size and weight on Apply.
        CaptionStyleOptions snapshot = captionOverlay.getStyleOptions().copy();
        snapshot.textSizeSp = captionOverlay.getTextSizeSp();
        snapshot.bold = captionOverlay.getBold();
        snapshot.italic = captionOverlay.getItalic();

        TemplatePickerBottomSheet picker =
                TemplatePickerBottomSheet.newInstance(captionOverlay.getStyleType().name());
        picker.setInitialOptions(snapshot);
        picker.setOnApply(this::applyTemplate);
        picker.show(getSupportFragmentManager(), "template_picker");
    }

    private void applyTemplate(CaptionStyleDefinition def, CaptionStyleOptions options) {
        captionOverlay.setStyleOptions(options);
        captionOverlay.setTextSizeSp(options.textSizeSp);
        captionOverlay.setBold(options.bold);
        captionOverlay.setItalic(options.italic);
        try {
            CaptionOverlayView.CaptionStyleType type = CaptionOverlayView.CaptionStyleType.valueOf(def.id);
            captionOverlay.setStyleType(type);
            saveProjectState();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Style \"" + def.displayName + "\" isn't wired into the enum yet", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCaptionSyncLoop() {
        if (syncRunnable != null) mainHandler.removeCallbacks(syncRunnable);
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                if (videoView.isPlaying()) {
                    int pos = videoView.getCurrentPosition();
                    if (trimEndMs > trimStartMs && pos >= trimEndMs) {
                        videoView.seekTo((int) trimStartMs);
                        pos = (int) trimStartMs;
                    }
                    captionOverlay.setCurrentTimeMs(pos);
                    if (!userSeeking) seekBar.setProgress(pos);
                }
                mainHandler.postDelayed(this, 33);
            }
        };
        mainHandler.post(syncRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView.isPlaying()) {
            videoView.pause();
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
        saveProjectState();
    }

    /**
     * The 30fps caption sync loop reposts itself forever, and the single-thread
     * executor keeps a reference to this Activity. Without this the destroyed
     * Activity leaks and a transcription started just before a rotation keeps
     * running (and keeps a native whisper context alive) with nowhere to deliver
     * its result.
     */
    @Override
    protected void onDestroy() {
        if (syncRunnable != null) mainHandler.removeCallbacks(syncRunnable);
        mainHandler.removeCallbacksAndMessages(null);
        if (activeExporter != null) activeExporter.cancel();
        TranscriptionEngine eng = transcriptionEngine;
        if (eng != null) eng.cancel();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void applyScalePreview() {
        videoView.setScaleX(scaleFactor);
        videoView.setScaleY(scaleFactor);
        captionOverlay.setScaleX(scaleFactor);
        captionOverlay.setScaleY(scaleFactor);
    }

    private void setChipSelected(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(selected ? 0xFFFFFFFF : 0xFFB0B0B5);
    }

    private String formatMs(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(java.util.Locale.US, "%d:%02d", min, sec);
    }

    /**
     * Model handling. There are two distinct phases and they must not be
     * confused:
     *
     *   1. FIRST-TIME SETUP - the model is not on the device yet. The user is
     *      asked explicitly whether to download it or import a copy they already
     *      have. Nothing is fetched silently.
     *   2. NORMAL OPERATION - the model is present and validated; transcription,
     *      editing and export all run fully offline.
     */
    private void prepareTranscription() {
        if (ModelManager.isModelReady(this)) {
            runTranscription(ModelManager.getModelFile(this).getAbsolutePath());
            return;
        }
        showModelSetupDialog();
    }

    private void showModelSetupDialog() {
        statusText.setText("Speech model not installed");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Set up offline captions")
                .setMessage(ModelManager.describeSetup()
                        + "\n\nNothing is uploaded - your video and audio are processed on this device.")
                .setPositiveButton("Download", (d, w) -> startModelDownload())
                .setNeutralButton("Import file", (d, w) ->
                        modelImportLauncher.launch(new String[]{"*/*"}))
                .setNegativeButton("Later", (d, w) ->
                        statusText.setText("No captions until the speech model is installed"))
                .setCancelable(false)
                .show();
    }

    private void startModelDownload() {
        statusText.setText("Downloading model... 0%");
        progressBar.setProgress(0);
        ModelManager.downloadModel(this, new ModelManager.ProgressListener() {
            @Override
            public void onProgress(int percent) {
                mainHandler.post(() -> {
                    statusText.setText("Downloading model... " + percent + "%");
                    progressBar.setProgress(percent);
                });
            }

            @Override
            public void onComplete(String modelPath) {
                mainHandler.post(() -> runTranscription(modelPath));
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    statusText.setText("Model download failed: " + error);
                    showModelSetupDialog();   // offer the offline import route
                });
            }
        });
    }

    private void startModelImport(Uri source) {
        statusText.setText("Importing model...");
        progressBar.setProgress(0);
        ModelManager.importModel(this, source, new ModelManager.ProgressListener() {
            @Override
            public void onProgress(int percent) {
                mainHandler.post(() -> {
                    statusText.setText("Importing model... " + percent + "%");
                    progressBar.setProgress(percent);
                });
            }

            @Override
            public void onComplete(String modelPath) {
                mainHandler.post(() -> runTranscription(modelPath));
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    statusText.setText("Model import failed: " + error);
                    showModelSetupDialog();
                });
            }
        });
    }

    private void runTranscription(String modelPath) {
        statusText.setText("Extracting audio...");
        progressBar.setProgress(0);

        if (!WhisperBridge.isLibraryAvailable()) {
            statusText.setText("Speech engine unavailable: " + WhisperBridge.getLoadError());
            return;
        }

        executor.execute(() -> {
            TranscriptionEngine engine = null;
            try {
                float[] audio = AudioExtractor.extractPcm16k(this, videoUri);
                if (audio.length == 0) {
                    mainHandler.post(() -> statusText.setText("No speech audio found in this video"));
                    return;
                }
                mainHandler.post(() -> statusText.setText("Transcribing... 0%"));

                engine = new TranscriptionEngine(new WhisperBridge());
                final TranscriptionEngine eng = engine;

                if (!engine.loadModel(modelPath)) {
                    // Almost always a corrupt or truncated model file. Drop it so
                    // the next run re-acquires it instead of failing forever.
                    ModelManager.discardModel(this);
                    mainHandler.post(() -> statusText.setText(
                            "Could not load the speech model. The stored copy will be re-downloaded on the next attempt."));
                    return;
                }

                engine.setProgressListener(percent -> mainHandler.post(() -> {
                    statusText.setText("Transcribing... " + percent + "%");
                    progressBar.setProgress(percent);
                }));

                transcriptionEngine = eng;
                final List<CaptionWord> words = eng.transcribe(audio);
                engine = null;   // released in finally via transcriptionEngine
                eng.release();
                transcriptionEngine = null;

                mainHandler.post(() -> {
                    captionWords = words;
                    captionOverlay.setWords(words);
                    statusText.setText(words.isEmpty()
                            ? "No speech detected in this video"
                            : "Ready - " + words.size() + " words");
                    progressBar.setProgress(100);
                    saveProjectState();
                });

            } catch (Throwable t) {
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                mainHandler.post(() -> statusText.setText("Transcription failed: " + msg));
            } finally {
                if (engine != null) engine.release();
                transcriptionEngine = null;
            }
        });
    }

    private void runExport() {
        if (captionWords.isEmpty()) {
            Toast.makeText(this, "No captions yet", Toast.LENGTH_SHORT).show();
            return;
        }

        exportRunning = true;
        exportButton.setText("Cancel Export");

        // ---- snapshot everything the exporter needs, ON THE UI THREAD ----
        // CaptionOverlayView is a View: its paint metrics, font metrics and
        // height have to be read here. The old code read them from the worker
        // thread inside executor.execute(), which is both a threading violation
        // and the reason the export could not reproduce the preview geometry.
        final String styleId = captionOverlay.getStyleType().name();
        final CaptionStyleOptions optionsSnapshot = captionOverlay.getStyleOptions().copy();
        final float posX = captionOverlay.getPosXFraction();
        final float posY = captionOverlay.getPosYFraction();
        final float textSizePx = captionOverlay.getTextSizePx();
        // The height of the rect the VIDEO occupies, not of the overlay. The
        // overlay is match_parent while VideoView letterboxes the picture inside
        // it, and dividing by the overlay height is what made the burned-in
        // caption the wrong size.
        final float videoDisplayHeightPx = Math.max(1f, captionOverlay.getVideoRect().height);
        final float baselineToCenterPx = captionOverlay.getBaselineToCenterPx();
        final float lineHeightPx = captionOverlay.getLineHeightPx();
        final boolean boldSnapshot = captionOverlay.getBold();
        final boolean italicSnapshot = captionOverlay.getItalic();
        final String fontAsset = optionsSnapshot.fontAssetOverride != null
                ? optionsSnapshot.fontAssetOverride
                : StyleFontMap.assetForStyleId(styleId);
        final String assFamily = StyleFontMap.familyNameFor(fontAsset);
        final List<CaptionWord> wordsSnapshot = new ArrayList<>(captionWords);
        final long trimStart = trimStartMs;
        final long trimEnd = trimEndMs;
        final long duration = videoDurationMs;

        String fidelityNote = com.saad.capvid.export.StyleAssMapper.exportNotes(styleId);
        String optionNote = com.saad.capvid.export.StyleAssMapper.optionNotes(optionsSnapshot);
        if (!optionNote.isEmpty()) {
            fidelityNote = fidelityNote.isEmpty() ? optionNote : fidelityNote + " " + optionNote;
        }
        statusText.setText(fidelityNote.isEmpty() ? "Exporting... 0%" : "Exporting... 0% (" + fidelityNote + ")");

        activeExporter = new VideoExporter();
        final VideoExporter exporter = activeExporter;

        executor.execute(() -> {
            File inputCopy = null;
            try {
                inputCopy = copyUriToCache(videoUri);
                final File inputRef = inputCopy;

                int[] dims = readDisplayDimensions(inputCopy);
                int videoWidth = dims[0];
                int videoHeight = dims[1];

                File fontsDir = FontManager.copyFontsToInternal(this);

                AssSubtitleBuilder.Request req = new AssSubtitleBuilder.Request();
                req.words = wordsSnapshot;
                req.videoWidth = videoWidth;
                req.videoHeight = videoHeight;
                req.styleId = styleId;
                req.options = optionsSnapshot;
                req.assFontFamilyName = assFamily;
                req.bold = boldSnapshot;
                req.italic = italicSnapshot;
                // libass resolves a face by family + weight, not by filename, so
                // a bold file that shares its family with a regular one has to
                // ask for bold explicitly or fontconfig may hand back the
                // lighter face.
                req.fontAssetIsBold = StyleFontMap.assetIsBold(fontAsset);
                req.fontAssetIsItalic = StyleFontMap.assetIsItalic(fontAsset);
                req.posXFraction = posX;
                req.posYFraction = posY;
                req.previewTextSizePx = textSizePx;
                req.previewVideoDisplayHeightPx = videoDisplayHeightPx;
                req.previewBaselineToCenterPx = baselineToCenterPx;
                req.previewLineHeightPx = lineHeightPx;

                String ass = AssSubtitleBuilder.build(req);

                exporter.export(this, inputRef.getAbsolutePath(), ass, fontsDir,
                        trimStart, trimEnd, duration,
                        new VideoExporter.ExportCallback() {
                            @Override
                            public void onProgress(int percent) {
                                mainHandler.post(() -> {
                                    progressBar.setProgress(percent);
                                    statusText.setText("Exporting... " + percent + "%");
                                });
                            }

                            @Override
                            public void onSuccess(Uri outputUri) {
                                mainHandler.post(() -> {
                                    exportRunning = false;
                                    activeExporter = null;
                                    exportButton.setText("Export");
                                    progressBar.setProgress(100);
                                    statusText.setText("Exported to Movies/Capvid");
                                    Toast.makeText(PreviewActivity.this, "Saved to gallery", Toast.LENGTH_LONG).show();
                                    deleteQuietly(inputRef);
                                });
                            }

                            @Override
                            public void onFailure(String error) {
                                mainHandler.post(() -> {
                                    exportRunning = false;
                                    activeExporter = null;
                                    exportButton.setText("Export");
                                    statusText.setText("Export failed: " + error);
                                    deleteQuietly(inputRef);
                                });
                            }

                            @Override
                            public void onCancelled() {
                                mainHandler.post(() -> {
                                    exportRunning = false;
                                    activeExporter = null;
                                    exportButton.setText("Export");
                                    statusText.setText("Export cancelled");
                                    deleteQuietly(inputRef);
                                });
                            }
                        });

            } catch (Throwable t) {
                deleteQuietly(inputCopy);
                mainHandler.post(() -> {
                    exportRunning = false;
                    activeExporter = null;
                    exportButton.setText("Export");
                    statusText.setText("Export error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
                });
            }
        });
    }

    /**
     * Reads the video's DISPLAY dimensions, i.e. after the rotation metadata is
     * applied. FFmpeg auto-rotates decoded frames to match how the video is
     * shown, and the .ass canvas has to describe that same frame - so a portrait
     * phone video stored as rotated landscape must be reported swapped.
     */
    /**
     * Reads the display dimensions straight from the content URI, without the
     * cache copy {@link #readDisplayDimensions(File)} needs. The overlay has to
     * know these to work out where {@code VideoView} letterboxes the picture,
     * because the caption is positioned as a fraction of the video frame while
     * the overlay covers the whole screen.
     *
     * <p>Fails soft: if the metadata cannot be read the overlay keeps using the
     * full view, which is the old behaviour.
     */
    private void publishVideoDisplaySize() {
        final Uri uri = videoUri;
        executor.execute(() -> {
            int[] dims = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(PreviewActivity.this, uri);
                dims = displayDimensionsFrom(retriever);
            } catch (Throwable ignored) {
                // leave dims null; the overlay falls back to the whole view
            } finally {
                try {
                    retriever.release();
                } catch (Throwable ignored) {
                }
            }
            final int[] result = dims;
            if (result == null) return;
            mainHandler.post(() -> captionOverlay.setVideoDisplaySize(result[0], result[1]));
        });
    }

    private int[] readDisplayDimensions(File videoFile) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(videoFile.getAbsolutePath());
            int[] dims = displayDimensionsFrom(retriever);
            if (dims == null) throw new IllegalStateException("Could not read the video dimensions");
            return dims;
        } finally {
            retriever.release();
        }
    }

    /**
     * Width/height as the viewer will see them, i.e. with 90/270 rotation
     * metadata applied. Returns null when the metadata is missing.
     */
    private static int[] displayDimensionsFrom(MediaMetadataRetriever retriever) {
        String w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        if (w == null || h == null) return null;
        int videoWidth = Integer.parseInt(w);
        int videoHeight = Integer.parseInt(h);
        String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        int rotation = rotationStr != null ? Integer.parseInt(rotationStr) : 0;
        if (rotation == 90 || rotation == 270) {
            int tmp = videoWidth;
            videoWidth = videoHeight;
            videoHeight = tmp;
        }
        return new int[]{videoWidth, videoHeight};
    }

    /**
     * FFmpeg needs a real filesystem path; Android hands us a content:// URI.
     * Copies the source into the cache dir. The caller owns the returned file and
     * must delete it - every export path now does, success, failure or cancel.
     */
    private File copyUriToCache(Uri uri) throws Exception {
        File outFile = new File(getCacheDir(), "input_video_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            if (in == null) throw new IllegalStateException("Could not open the source video");
            byte[] buffer = new byte[64 * 1024];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            out.flush();
        } catch (Exception e) {
            deleteQuietly(outFile);
            throw e;
        }
        return outFile;
    }

    private static void deleteQuietly(File f) {
        if (f != null) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
