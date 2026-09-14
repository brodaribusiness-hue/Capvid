package com.saad.capvid.export;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class VideoExporter {

    public interface ExportCallback {
        void onSuccess(Uri outputUri);
        void onFailure(String error);
    }

    public static void export(Context context, String inputVideoPath, String assContent, File fontsDir,
                               long trimStartMs, long trimEndMs, float scaleFactor, ExportCallback callback) {
        try {
            File assFile = new File(context.getCacheDir(), "captions.ass");
            try (FileOutputStream fos = new FileOutputStream(assFile)) {
                fos.write(assContent.getBytes());
            }

            File outputFile = new File(context.getCacheDir(), "capvid_export_" + System.currentTimeMillis() + ".mp4");

            // Filter chain: scale (if changed from 100%) then burn captions. Order
            // matters — scaling first means the ass filter draws on the already
            // resized frame so caption position stays correct.
            StringBuilder vf = new StringBuilder();
            boolean hasScale = Math.abs(scaleFactor - 1f) > 0.01f;
            if (hasScale) {
                vf.append(String.format(java.util.Locale.US, "scale=iw*%.3f:ih*%.3f,", scaleFactor, scaleFactor));
            }
            vf.append(String.format("ass='%s':fontsdir='%s'", assFile.getAbsolutePath(), fontsDir.getAbsolutePath()));

            // Trim: -ss/-to placed AFTER -i (output-seek) so original PTS are kept —
            // the .ass file's word timings were generated against the untrimmed
            // timeline, so this is what keeps captions in sync after a trim.
            StringBuilder trimArgs = new StringBuilder();
            if (trimEndMs > trimStartMs && trimStartMs >= 0) {
                trimArgs.append(String.format(java.util.Locale.US, "-ss %.3f -to %.3f ",
                        trimStartMs / 1000f, trimEndMs / 1000f));
            }

            String command = String.format(
                    "-y -i \"%s\" %s-vf \"%s\" -c:v libx264 -preset fast -c:a %s \"%s\"",
                    inputVideoPath, trimArgs.toString(), vf.toString(),
                    trimArgs.length() > 0 ? "aac" : "copy", // trimming with -to needs audio re-encode, not stream copy
                    outputFile.getAbsolutePath());

            FFmpegSession session = FFmpegKit.execute(command);

            if (ReturnCode.isSuccess(session.getReturnCode())) {
                callback.onSuccess(saveToGallery(context, outputFile));
            } else {
                callback.onFailure("FFmpeg failed: " + session.getFailStackTrace());
            }

        } catch (Throwable t) {
            callback.onFailure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static Uri saveToGallery(Context context, File videoFile) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Capvid");

        Uri itemUri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

        try (OutputStream out = context.getContentResolver().openOutputStream(itemUri);
             FileInputStream in = new FileInputStream(videoFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        }

        return itemUri;
    }
}
