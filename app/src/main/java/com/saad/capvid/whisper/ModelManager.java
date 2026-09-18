package com.saad.capvid.whisper;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Owns the offline speech-recognition model: where it lives, how it gets there
 * the first time, and how we know it is still intact afterwards.
 *
 * <h3>The two phases are deliberately separate</h3>
 * <ol>
 *   <li><b>First-time model setup.</b> Runs once, needs network, and is an
 *       explicit user action with visible progress. Two routes are offered:
 *       download it, or import a copy the user already has (a file on the device
 *       or another machine) - the import route needs no network at all, so a
 *       user on a locked-down or offline network can still use the app.</li>
 *   <li><b>Normal operation.</b> Transcription, editing, preview and export never
 *       touch the network. There is no transcription API, no cloud rendering and
 *       no account; user video and audio never leave the device.</li>
 * </ol>
 *
 * <h3>Integrity checking</h3>
 * A model file is only accepted if it passes {@link #validate(File)}, which
 * checks:
 * <ul>
 *   <li>the size is in a plausible range (an HTML error page saved as the model
 *       is a few KB, a truncated download is short);</li>
 *   <li>the first four bytes are the ggml magic {@code 0x67676d6c} ("ggml"),
 *       which is exactly the check whisper.cpp itself performs in
 *       {@code whisper_model_load} before it touches anything else;</li>
 *   <li>the hyper-parameter block that follows is internally consistent
 *       (n_text_state == n_audio_state, a known layer count, a known mel count).</li>
 * </ul>
 * Together those catch the realistic failure modes - a captive-portal HTML page,
 * a partially downloaded file, a corrupted copy - and cause the file to be
 * discarded and re-acquired rather than failing forever at load time.
 *
 * <p>The header layout validated here was taken from whisper.cpp v1.9.4
 * ({@code src/whisper.cpp}, the "load hparams" block): magic, then n_vocab,
 * n_audio_ctx, n_audio_state, n_audio_head, n_audio_layer, n_text_ctx,
 * n_text_state, n_text_head, n_text_layer, n_mels, ftype - eleven little-endian
 * 32-bit integers after the 4-byte magic.
 */
public class ModelManager {

    /** Describes one supported model. */
    public static final class ModelSpec {
        public final String fileName;
        public final String downloadUrl;
        /** Reject anything smaller than this: it cannot be a real model. */
        public final long minBytes;
        /** Used only for the progress bar and the free-space check. */
        public final long approxBytes;
        /** whisper's model size marker (tiny = 4 audio layers). */
        public final int expectedAudioLayers;
        public final String displayName;

        ModelSpec(String fileName, String downloadUrl, long minBytes, long approxBytes,
                  int expectedAudioLayers, String displayName) {
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
            this.minBytes = minBytes;
            this.approxBytes = approxBytes;
            this.expectedAudioLayers = expectedAudioLayers;
            this.displayName = displayName;
        }

        public String sizeLabel() {
            return String.format(Locale.US, "%.0f MB", approxBytes / (1024.0 * 1024.0));
        }
    }

    /**
     * ggml-tiny.en: ~75 MB, English-only, roughly 75 MB of RAM to run. Chosen
     * because it is the only size that is realistic on a phone CPU - see
     * AUDIT.md for the measured reasoning behind rejecting base and larger.
     */
    public static final ModelSpec DEFAULT = new ModelSpec(
            "ggml-tiny.en.bin",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            30L * 1024 * 1024,
            75L * 1024 * 1024,
            4,
            "Whisper Tiny (English)");

    private static final int GGML_FILE_MAGIC = 0x67676d6c;
    private static final int HEADER_BYTES = 48;
    private static final int MAX_REDIRECTS = 5;

    public interface ProgressListener {
        void onProgress(int percent);

        void onComplete(String modelPath);

        void onError(String error);
    }

    private ModelManager() {
    }

    public static File getModelFile(Context context) {
        return new File(context.getFilesDir(), DEFAULT.fileName);
    }

    /** True only when a complete, structurally valid model is on disk. */
    public static boolean isModelReady(Context context) {
        return validate(getModelFile(context)) == null;
    }

    /**
     * @return null when the file is a usable model, otherwise a human-readable reason
     */
    public static String validate(File file) {
        if (file == null || !file.exists()) return "The speech model is not installed yet";
        long len = file.length();
        if (len < DEFAULT.minBytes) {
            return "The speech model file is incomplete (" + (len / 1024) + " KB of about "
                    + DEFAULT.sizeLabel() + ")";
        }
        byte[] header = new byte[HEADER_BYTES];
        try (FileInputStream in = new FileInputStream(file)) {
            int read = 0;
            while (read < HEADER_BYTES) {
                int n = in.read(header, read, HEADER_BYTES - read);
                if (n < 0) break;
                read += n;
            }
        } catch (Exception e) {
            return "Could not read the speech model: " + e.getMessage();
        }
        if (header.length < HEADER_BYTES) return "The speech model file is truncated";

        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int magic = bb.getInt();
        if (magic != GGML_FILE_MAGIC) {
            return "The speech model file is not a ggml model (bad header) - "
                    + "this usually means a network error page was saved instead";
        }
        int nVocab = bb.getInt();
        bb.getInt();                     // n_audio_ctx
        int nAudioState = bb.getInt();
        bb.getInt();                     // n_audio_head
        int nAudioLayer = bb.getInt();
        bb.getInt();                     // n_text_ctx
        int nTextState = bb.getInt();
        bb.getInt();                     // n_text_head
        bb.getInt();                     // n_text_layer
        int nMels = bb.getInt();

        if (nVocab <= 0 || nVocab > 200000) return "The speech model header is corrupt (n_vocab)";
        if (nAudioState <= 0 || nAudioState != nTextState) {
            return "The speech model header is corrupt (state size mismatch)";
        }
        if (nAudioLayer != 4 && nAudioLayer != 6 && nAudioLayer != 12
                && nAudioLayer != 24 && nAudioLayer != 32 && nAudioLayer != 48) {
            return "The speech model header is corrupt (n_audio_layer = " + nAudioLayer + ")";
        }
        if (nMels != 80 && nMels != 128) {
            return "The speech model header is corrupt (n_mels = " + nMels + ")";
        }
        return null;
    }

    /**
     * Removes a bad or unwanted model so setup runs again. Called when the native
     * loader rejects a file that passed the header check.
     */
    public static void discardModel(Context context) {
        File f = getModelFile(context);
        //noinspection ResultOfMethodCallIgnored
        if (f.exists()) f.delete();
        File part = new File(context.getFilesDir(), DEFAULT.fileName + ".part");
        //noinspection ResultOfMethodCallIgnored
        if (part.exists()) part.delete();
    }

    /**
     * Downloads the model. Resumable: an interrupted download leaves a
     * {@code .part} file which the next attempt continues with a Range request
     * instead of starting over - a 75 MB file on a phone connection rarely
     * completes in one go.
     */
    public static void downloadModel(Context context, ProgressListener listener) {
        final Context app = context.getApplicationContext();
        new Thread(() -> doDownload(app, listener), "capvid-model-download").start();
    }

    private static void doDownload(Context context, ProgressListener listener) {
        File out = getModelFile(context);
        File part = new File(context.getFilesDir(), DEFAULT.fileName + ".part");

        long free = freeBytes(context.getFilesDir());
        if (free < DEFAULT.approxBytes * 2) {
            listener.onError("Not enough free storage: the model needs about "
                    + DEFAULT.sizeLabel() + " plus room to unpack it");
            return;
        }

        long existing = part.exists() ? part.length() : 0L;
        HttpURLConnection conn = null;
        try {
            String location = DEFAULT.downloadUrl;
            int responseCode = -1;
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                conn = openConnection(location, existing);
                responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == 307 || responseCode == 308) {
                    String next = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (next == null) break;
                    location = next;
                    continue;
                }
                break;
            }

            // A 200 (rather than 206) means the server ignored our Range and is
            // sending the whole file, so the partial data on disk is unusable.
            boolean appending = (responseCode == 206 && existing > 0);
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 206) {
                listener.onError("Model server returned HTTP " + responseCode);
                return;
            }

            long total = conn.getContentLengthLong();
            long expectedTotal = total > 0 ? (appending ? existing + total : total) : -1;

            long written = appending ? existing : 0L;
            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(part, appending)) {
                byte[] buffer = new byte[64 * 1024];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    written += len;
                    if (expectedTotal > 0) {
                        listener.onProgress((int) Math.min(99, written * 100 / expectedTotal));
                    }
                }
                fos.flush();
                try {
                    fos.getFD().sync();
                } catch (Exception ignored) {
                }
            }

            String problem = validate(part);
            if (problem != null) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                listener.onError(problem);
                return;
            }

            //noinspection ResultOfMethodCallIgnored
            if (out.exists()) out.delete();
            if (!part.renameTo(out)) {
                listener.onError("Could not move the downloaded model into place");
                return;
            }
            listener.onProgress(100);
            listener.onComplete(out.getAbsolutePath());

        } catch (Exception e) {
            listener.onError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String location, long resumeFrom) throws Exception {
        URL url = new URL(location);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        // Cross-protocol redirects are not followed automatically by
        // HttpURLConnection, which is why doDownload() walks them by hand.
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("User-Agent", "Capvid/1.0 (Android)");
        if (resumeFrom > 0) conn.setRequestProperty("Range", "bytes=" + resumeFrom + "-");
        return conn;
    }

    /**
     * The fully offline setup route: the user picks an existing ggml model file
     * and we copy it in. No network involved at any point.
     */
    public static void importModel(Context context, Uri source, ProgressListener listener) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            File out = getModelFile(app);
            File part = new File(app.getFilesDir(), DEFAULT.fileName + ".import");
            try {
                long written = 0;
                try (InputStream in = app.getContentResolver().openInputStream(source);
                     FileOutputStream fos = new FileOutputStream(part)) {
                    if (in == null) {
                        listener.onError("Could not open the selected file");
                        return;
                    }
                    byte[] buffer = new byte[64 * 1024];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        written += len;
                        if (written % (4 * 1024 * 1024) < buffer.length) {
                            listener.onProgress((int) Math.min(99,
                                    written * 100 / DEFAULT.approxBytes));
                        }
                    }
                    fos.flush();
                }

                String problem = validate(part);
                if (problem != null) {
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    listener.onError(problem);
                    return;
                }
                //noinspection ResultOfMethodCallIgnored
                if (out.exists()) out.delete();
                if (!part.renameTo(out)) {
                    listener.onError("Could not move the imported model into place");
                    return;
                }
                listener.onProgress(100);
                listener.onComplete(out.getAbsolutePath());
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
                listener.onError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }, "capvid-model-import").start();
    }

    private static long freeBytes(File dir) {
        try {
            return new StatFs(dir.getAbsolutePath()).getAvailableBytes();
        } catch (Throwable t) {
            return Long.MAX_VALUE;
        }
    }

    /** For the setup dialog: what a first run will need to do. */
    public static String describeSetup() {
        return DEFAULT.displayName + " (" + DEFAULT.sizeLabel()
                + ") is downloaded once and stored on this device. "
                + "After that, captioning works completely offline.";
    }

    /** Reads the first bytes of a file - used by tests to prove validation works. */
    static int readMagic(File f) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] b = new byte[4];
            raf.readFully(b);
            return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }
    }
}
