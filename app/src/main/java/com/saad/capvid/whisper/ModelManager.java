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
        return f.exists() && f.length() > 0;
    }

    public static void downloadModel(Context context, ProgressListener listener) {
        new Thread(() -> {
            try {
                File outputFile = getModelFile(context);
                URL url = new URL(MODEL_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                int fileLength = connection.getContentLength();

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(outputFile)) {

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

                listener.onComplete(outputFile.getAbsolutePath());

            } catch (Exception e) {
                listener.onError(e.getMessage());
            }
        }).start();
    }
}
