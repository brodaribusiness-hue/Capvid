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

    private ActivityResultLauncher<String[]> videoPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

        // ACTION_OPEN_DOCUMENT (not ACTION_GET_CONTENT) is deliberate: it is the
        // only picker whose result carries a PERSISTABLE URI permission. A
        // GetContent grant is temporary and is revoked once the receiving
        // activity finishes - and this activity calls finish() immediately - so
        // the stored content:// URI in a saved project became unreadable the
        // moment the user left, and reopening that project failed with a
        // SecurityException on the very first frame read.
        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    takePersistablePermission(uri);
                    openPreview(uri);
                }
        );

        MaterialButton btnCaption = findViewById(R.id.btnCaption);
        MaterialButton btnRecordVideo = findViewById(R.id.btnRecordVideo);

        btnCaption.setOnClickListener(v -> videoPickerLauncher.launch(new String[]{"video/*"}));
        btnRecordVideo.setOnClickListener(v -> startActivity(new Intent(this, RecordVideoActivity.class)));

        findViewById(R.id.btnExitCreate).setOnClickListener(v -> finish());
    }

    /**
     * Upgrades the picker's temporary grant to a persistable one so the project
     * can still read its source video after a process restart.
     */
    private void takePersistablePermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Not every provider offers persistable grants. The video still opens
            // now; it just may not survive a restart.
        }
    }

    private void openPreview(Uri videoUri) {
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra("videoUri", videoUri);
        startActivity(intent);
        finish();
    }
}
