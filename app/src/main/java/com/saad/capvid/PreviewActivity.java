package com.saad.capvid;

import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
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

    private Uri videoUri;
    private List<CaptionWord> captionWords = new ArrayList<>();

    private long trimStartMs = 0;
    private long trimEndMs = -1; // -1 until video duration is known (set in onPrepared)
    private float scaleFactor = 1f;

    private ProjectManager projectManager;
    private Project currentProject;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int[] sizeOptions = {8, 10, 12, 14, 16, 18, 20, 24, 28, 32};

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
        Spinner styleSpinner = findViewById(R.id.styleSpinner);
        Spinner sizeSpinner = findViewById(R.id.sizeSpinner);
        CheckBox checkBold = findViewById(R.id.checkBold);
        CheckBox checkItalic = findViewById(R.id.checkItalic);
        Button exportButton = findViewById(R.id.btnExport);
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
            scaleLabel.setText("Scale: " + (int) value + "%");
            applyScalePreview();
            if (fromUser) saveProjectState();
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

        setupStyleSpinner(styleSpinner);
        setupSizeSpinner(sizeSpinner);

        checkBold.setOnCheckedChangeListener((buttonView, isChecked) -> captionOverlay.setBold(isChecked));
        checkItalic.setOnCheckedChangeListener((buttonView, isChecked) -> captionOverlay.setItalic(isChecked));

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
            seekBar.setMax(mp.getDuration());
            if (trimEndMs <= 0) trimEndMs = mp.getDuration();
            trimRangeSlider.setValueFrom(0f);
            trimRangeSlider.setValueTo(Math.max(1f, (float) mp.getDuration()));
            trimRangeSlider.setValues((float) trimStartMs, (float) trimEndMs);
            trimRangeLabel.setText("Trim: " + formatMs(trimStartMs) + " - " + formatMs(trimEndMs));
            scaleLabel.setText("Scale: " + (int) (scaleFactor * 100) + "%");
            scaleSlider.setValue(scaleFactor * 100f);
            applyScalePreview();
            if (trimStartMs > 0) videoView.seekTo((int) trimStartMs);
            videoView.start();
            startCaptionSyncLoop();
        });

        exportButton.setOnClickListener(v -> runExport());

        chooseTemplateButton.setOnClickListener(v -> openTemplatePicker(styleSpinner));

        try {
            captionOverlay.setStyleType(CaptionOverlayView.CaptionStyleType.valueOf(currentProject.styleId));
        } catch (IllegalArgumentException ignored) {
        }

        if (isResumed && !currentProject.words.isEmpty()) {
            captionWords = currentProject.words;
            captionOverlay.setWords(captionWords);
            statusText.setText("Ready — " + captionWords.size() + " words (resumed)");
            progressBar.setProgress(100);
        } else {
            prepareTranscription();
        }
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
        currentProject.words = captionWords;
        currentProject.lastEditedMs = System.currentTimeMillis();
        projectManager.save(currentProject);
    }

    private void openTemplatePicker(Spinner styleSpinner) {
        String currentId = captionOverlay.getStyleType().name();
        TemplatePickerBottomSheet picker = TemplatePickerBottomSheet.newInstance(currentId);
        picker.setInitialOptions(captionOverlay.getStyleOptions());
        picker.setOnApply(this::applyTemplate);
        picker.show(getSupportFragmentManager(), "template_picker");
    }

    private void applyTemplate(CaptionStyleDefinition def, CaptionStyleOptions options) {
        captionOverlay.setStyleOptions(options);
        try {
            CaptionOverlayView.CaptionStyleType type = CaptionOverlayView.CaptionStyleType.valueOf(def.id);
            captionOverlay.setStyleType(type);
            saveProjectState();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Style \"" + def.displayName + "\" isn't wired into the enum yet", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupStyleSpinner(Spinner spinner) {
        CaptionOverlayView.CaptionStyleType[] styles = CaptionOverlayView.CaptionStyleType.values();
        String[] names = new String[styles.length];
        for (int i = 0; i < styles.length; i++) names[i] = styles[i].name();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                captionOverlay.setStyleType(styles[position]);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupSizeSpinner(Spinner spinner) {
        String[] labels = new String[sizeOptions.length];
        for (int i = 0; i < sizeOptions.length; i++) labels[i] = String.valueOf(sizeOptions[i]);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(2);

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                captionOverlay.setTextSizeSp(sizeOptions[position]);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void startCaptionSyncLoop() {
        Runnable syncRunnable = new Runnable() {
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

    private void prepareTranscription() {
        statusText.setText("Checking model...");

        if (!ModelManager.isModelDownloaded(this)) {
            statusText.setText("Downloading model...");
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
                    mainHandler.post(() -> statusText.setText("Model download failed: " + error));
                }
            });
        } else {
            runTranscription(ModelManager.getModelFile(this).getAbsolutePath());
        }
    }

    private void runTranscription(String modelPath) {
        statusText.setText("Extracting audio...");
        progressBar.setProgress(0);

        executor.execute(() -> {
            try {
                float[] audio = AudioExtractor.extractPcm16k(this, videoUri);
                mainHandler.post(() -> statusText.setText("Transcribing... 0%"));

                WhisperBridge bridge = new WhisperBridge();
                TranscriptionEngine engine = new TranscriptionEngine(bridge);

                if (!engine.loadModel(modelPath)) {
                    mainHandler.post(() -> statusText.setText("Failed to load model"));
                    return;
                }

                engine.setProgressListener(percent -> mainHandler.post(() -> {
                    statusText.setText("Transcribing... " + percent + "%");
                    progressBar.setProgress(percent);
                }));

                List<CaptionWord> words = engine.transcribe(audio);
                engine.release();

                mainHandler.post(() -> {
                    captionWords = words;
                    captionOverlay.setWords(words);
                    statusText.setText("Ready — " + words.size() + " words");
                    progressBar.setProgress(100);
                    saveProjectState();
                });

            } catch (Throwable t) {
                mainHandler.post(() -> statusText.setText("Error: " + t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        });
    }

    private void runExport() {
        if (captionWords.isEmpty()) {
            Toast.makeText(this, "No captions yet", Toast.LENGTH_SHORT).show();
            return;
        }

        statusText.setText("Exporting...");

        executor.execute(() -> {
            try {
                String realPath = copyUriToCache(videoUri);

                int videoWidth;
                int videoHeight;
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(realPath);
                    videoWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    videoHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                } finally {
                    retriever.release();
                }

                File fontsDir = FontManager.copyFontsToInternal(this);
                StyleFontMap.FontInfo fontInfo = StyleFontMap.get(captionOverlay.getStyleType());

                String ass = AssSubtitleBuilder.build(captionWords, videoWidth, videoHeight,
                        captionOverlay.getPosXFraction(), captionOverlay.getPosYFraction(),
                        captionOverlay.getTextSizeSp() * 2.5f,
                        fontInfo.assFamilyName, captionOverlay.getBold(), captionOverlay.getItalic());

                VideoExporter.export(this, realPath, ass, fontsDir, trimStartMs, trimEndMs, scaleFactor, new VideoExporter.ExportCallback() {
                    @Override
                    public void onSuccess(Uri outputUri) {
                        mainHandler.post(() -> {
                            statusText.setText("Exported to Movies/Capvid");
                            Toast.makeText(PreviewActivity.this, "Saved to gallery", Toast.LENGTH_LONG).show();
                        });
                    }
                    @Override
                    public void onFailure(String error) {
                        mainHandler.post(() -> statusText.setText("Export failed: " + error));
                    }
                });

            } catch (Throwable t) {
                mainHandler.post(() -> statusText.setText("Export error: " + t.getClass().getSimpleName() + ": " + t.getMessage()));
            }
        });
    }

    private String copyUriToCache(Uri uri) throws Exception {
        File outFile = new File(getCacheDir(), "input_video.mp4");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        }
        return outFile.getAbsolutePath();
    }
}
