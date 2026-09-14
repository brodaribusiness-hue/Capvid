package com.saad.capvid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * "Create" screen reached from the home screen's Create button: choose
 * between captioning an existing video (Caption) or recording a new one
 * (Record Video, via RecordVideoActivity/CameraX).
 */
public class CreateActivity extends AppCompatActivity {

    private ActivityResultLauncher<String> videoPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> { if (uri != null) openPreview(uri); }
        );

        MaterialButton btnCaption = findViewById(R.id.btnCaption);
        MaterialButton btnRecordVideo = findViewById(R.id.btnRecordVideo);

        btnCaption.setOnClickListener(v -> videoPickerLauncher.launch("video/*"));
        btnRecordVideo.setOnClickListener(v -> startActivity(new Intent(this, RecordVideoActivity.class)));

        findViewById(R.id.btnExitCreate).setOnClickListener(v -> finish());
    }

    private void openPreview(Uri videoUri) {
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra("videoUri", videoUri);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
        finish();
    }
}
