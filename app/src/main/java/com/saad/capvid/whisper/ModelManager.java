package com.saad.capvid.whisper;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ModelManager {

    private static final String MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin";
    private static final String MODEL_FILENAME = "ggml-tiny.en.bin";

    public interface ProgressListener {
        void onProgress(int percent);
        void onComplete(String modelPath);
        void onError(String error);
    }

    public static File getModelFile(Context context) {
        return new File(context.getFilesDir(), MODEL_FILENAME);
    }

    public static boolean isModelDownloaded(Context context) {
        File f = getModelFile(context);
        if (f.exists() && f.length() < MIN_VALID_MODEL_BYTES) {
            // Leftover corrupt/partial file from before this check existed —
            // delete it so downloadModel() runs again instead of loadModel()
            // failing forever on a bad file.
            f.delete();
            return false;
        }
        return f.exists() && f.length() > 0;
    }

    // ggml-tiny.en.bin is ~75MB; anything drastically smaller than this means
    // we saved a redirect/error page or a truncated download, not the model.
    private static final long MIN_VALID_MODEL_BYTES = 30L * 1024 * 1024;

    public static void downloadModel(Context context, ProgressListener listener) {
        new Thread(() -> {
            File outputFile = getModelFile(context);
            File tempFile = new File(context.getFilesDir(), MODEL_FILENAME + ".part");
            try {
                URL url = new URL(MODEL_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    listener.onError("Server returned HTTP " + responseCode);
                    return;
                }

                int fileLength = connection.getContentLength();

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(tempFile)) {

                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int len;
                    while ((len = input.read(buffer)) != -1) {
                        total += len;
                        output.write(buffer, 0, len);
                        if (fileLength > 0) {
                            listener.onProgress((int) (total * 100 / fileLength));
                        }
                    }
                }

                if (tempFile.length() < MIN_VALID_MODEL_BYTES) {
                    tempFile.delete();
                    listener.onError("Downloaded file looks incomplete/corrupt (" + tempFile.length() + " bytes)");
                    return;
                }

                // Only move into place once we know the download is complete and
                // large enough to be the real model — isModelDownloaded() only
                // ever sees the final good file, never a partial one.
                if (outputFile.exists()) outputFile.delete();
                if (!tempFile.renameTo(outputFile)) {
                    listener.onError("Could not finalize downloaded model file");
                    return;
                }

                listener.onComplete(outputFile.getAbsolutePath());

            } catch (Exception e) {
                tempFile.delete();
                listener.onError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }).start();
    }
}
