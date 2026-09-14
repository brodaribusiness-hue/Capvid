package com.saad.capvid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * Reached from CreateActivity's "Record Video" button. Standard CameraX
 * Recorder/VideoCapture flow: request camera+mic permission, bind preview +
 * video capture to the lifecycle, toggle recording with one button, then
 * hand the resulting file straight to PreviewActivity — same entry point
 * the "Caption" (pick existing video) flow uses.
 */
public class RecordVideoActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageButton btnRecordToggle;
    private TextView recordTimer;

    private VideoCapture<Recorder> videoCapture;
    private Recording activeRecording;
    private boolean isRecording = false;
    private long recordStartMs = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording) {
                long elapsed = System.currentTimeMillis() - recordStartMs;
                long sec = elapsed / 1000;
                recordTimer.setText(String.format(java.util.Locale.US, "%02d:%02d", sec / 60, sec % 60));
                mainHandler.postDelayed(this, 500);
            }
        }
    };

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_video);

        previewView = findViewById(R.id.cameraPreview);
        btnRecordToggle = findViewById(R.id.btnRecordToggle);
        recordTimer = findViewById(R.id.recordTimer);

        findViewById(R.id.btnExitRecord).setOnClickListener(v -> finish());
        btnRecordToggle.setOnClickListener(v -> toggleRecording());

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean cameraOk = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));
                    boolean micOk = Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO));
                    if (cameraOk && micOk) {
                        startCamera();
                    } else {
                        Toast.makeText(this, "Camera and microphone permission are required to record", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });

        if (hasPermissions()) {
            startCamera();
        } else {
            permissionLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        }
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, videoCapture);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Camera init failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleRecording() {
        if (videoCapture == null) return;
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        File outDir = new File(getExternalFilesDir(null), "recordings");
        if (!outDir.exists()) outDir.mkdirs();
        File outFile = new File(outDir, "capvid_rec_" + System.currentTimeMillis() + ".mp4");

        FileOutputOptions outputOptions = new FileOutputOptions.Builder(outFile).build();

        PendingRecording pending = videoCapture.getOutput()
                .prepareRecording(this, outputOptions)
                .withAudioEnabled();

        activeRecording = pending.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;
                if (!finalize.hasError()) {
                    openPreview(Uri.fromFile(outFile));
                } else {
                    Toast.makeText(this, "Recording failed: " + finalize.getCause(), Toast.LENGTH_LONG).show();
                }
            }
        });

        isRecording = true;
        recordStartMs = System.currentTimeMillis();
        btnRecordToggle.setImageResource(android.R.drawable.ic_media_pause);
        recordTimer.setVisibility(android.view.View.VISIBLE);
        mainHandler.post(timerRunnable);
    }

    private void stopRecording() {
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
        isRecording = false;
        btnRecordToggle.setImageResource(android.R.drawable.presence_video_online);
        recordTimer.setVisibility(android.view.View.GONE);
    }

    private void openPreview(Uri videoUri) {
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra("videoUri", videoUri);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(timerRunnable);
        if (activeRecording != null) activeRecording.stop();
    }
}
