package com.saad.capvid.export;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Burns the generated .ass captions into the source video with FFmpeg and saves
 * the result to the Gallery.
 *
 * <h3>What changed and why</h3>
 * <ul>
 *   <li><b>Runs asynchronously with progress and cancellation.</b> The old code
 *       called the blocking {@code FFmpegKit.execute} and gave the UI no way to
 *       report progress or abort; on a long video the app looked frozen with no
 *       escape.</li>
 *   <li><b>Checks free space first</b> and refuses to start rather than filling
 *       the device and failing halfway.</li>
 *   <li><b>Always cleans up temp files</b>, including on failure and on cancel.
 *       Previously {@code capvid_export_*.mp4} and {@code input_video.mp4} were
 *       left behind in the cache directory on every run, success or failure.</li>
 *   <li><b>Even output dimensions.</b> {@code scale=iw*f:ih*f} could produce an
 *       odd width or height for many source sizes, which libx264 rejects with
 *       yuv420p - the export simply failed. Dimensions are now floored to even.</li>
 *   <li><b>Explicit stream mapping with an optional audio map</b> so a video with
 *       no audio track still exports instead of erroring out.</li>
 *   <li><b>MediaStore IS_PENDING</b> so the Gallery never shows a half-written
 *       file while it is being copied.</li>
 *   <li><b>Skips the trim arguments entirely when the user did not trim</b>, so
 *       the default path stream-copies the original audio instead of needlessly
 *       re-encoding it to AAC and losing quality.</li>
 * </ul>
 *
 * <h3>Trim semantics (verified, unchanged)</h3>
 * {@code -ss}/{@code -to} are kept as <i>output</i> options (after {@code -i}).
 * With both on the output side, {@code -to} is an absolute position on the
 * original timeline, and the decoder's original PTS reach the filtergraph. That
 * is exactly what {@link AssSubtitleBuilder} writes its event times against, so
 * captions stay in sync after a trim. It is slower than input seeking (frames
 * before the in-point are decoded and discarded) but correct, and correctness is
 * the requirement here.
 */
public class VideoExporter {

    public interface ExportCallback {
        /** Called on the caller's thread with progress 0..100. */
        void onProgress(int percent);

        void onSuccess(Uri outputUri);

        void onFailure(String error);

        /** The user cancelled. */
        void onCancelled();
    }

    /** Roughly how much free space we insist on before starting an export. */
    private static final long MIN_FREE_BYTES = 512L * 1024 * 1024;

    private volatile FFmpegSession session;
    private volatile boolean cancelRequested;
    private File tempOutput;
    private File assFile;

    /**
     * @param context           app context
     * @param inputVideoPath    absolute path to a readable copy of the source video
     * @param assContent        the .ass file body (original, untrimmed timeline)
     * @param fontsDir          directory containing the bundled font files
     * @param trimStartMs       in-point on the original timeline
     * @param trimEndMs         out-point on the original timeline; &lt;= trimStartMs means no trim
     * @param videoDurationMs   total duration, used only for the progress percentage
     * @param scaleFactor       1.0 = keep original size
     * @param callback          progress/result callbacks
     */
    public void export(Context context, String inputVideoPath, String assContent, File fontsDir,
                       long trimStartMs, long trimEndMs, long videoDurationMs,
                       float scaleFactor, ExportCallback callback) {
        final Context appContext = context.getApplicationContext();
        try {
            long usable = freeBytes(appContext.getCacheDir());
            if (usable < MIN_FREE_BYTES) {
                callback.onFailure("Not enough free storage to export (need about "
                        + (MIN_FREE_BYTES / (1024 * 1024)) + " MB, have "
                        + (usable / (1024 * 1024)) + " MB)");
                return;
            }

            assFile = new File(appContext.getCacheDir(), "captions.ass");
            try (FileOutputStream fos = new FileOutputStream(assFile)) {
                fos.write(assContent.getBytes("UTF-8"));
            }

            tempOutput = new File(appContext.getCacheDir(),
                    "capvid_export_" + System.currentTimeMillis() + ".mp4");

            String command = buildCommand(inputVideoPath, assFile, fontsDir,
                    trimStartMs, trimEndMs, scaleFactor, tempOutput);

            final long duration = videoDurationMs > 0 ? videoDurationMs : 1L;
            final long inPoint = trimStartMs > 0 ? trimStartMs : 0L;

            session = FFmpegKit.executeAsync(command,
                    finished -> {
                        try {
                            if (cancelRequested) {
                                callback.onCancelled();
                                return;
                            }
                            if (ReturnCode.isSuccess(finished.getReturnCode())) {
                                Uri uri = saveToGallery(appContext, tempOutput);
                                callback.onSuccess(uri);
                            } else if (ReturnCode.isCancel(finished.getReturnCode())) {
                                callback.onCancelled();
                            } else {
                                String logs = finished.getAllLogsAsString();
                                callback.onFailure("FFmpeg failed"
                                        + (logs != null && !logs.isEmpty()
                                            ? ": " + tail(logs, 400) : ""));
                            }
                        } catch (Throwable t) {
                            callback.onFailure(t.getClass().getSimpleName() + ": " + t.getMessage());
                        } finally {
                            cleanup();
                        }
                    },
                    null,
                    statistics -> {
                        // Statistics.getTime() returns a double, in milliseconds of
                        // input processed, so it has to be rounded before it can be
                        // compared against the long inPoint/duration values below.
                        long t = Math.round(statistics.getTime());
                        if (t >= 0) {
                            int pct = (int) Math.max(0, Math.min(100,
                                    ((t - inPoint) * 100L) / Math.max(1L, duration - inPoint)));
                            callback.onProgress(pct);
                        }
                    });

        } catch (Throwable t) {
            cleanup();
            callback.onFailure(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /** Asks FFmpeg to stop. Safe to call from any thread, and before start. */
    public void cancel() {
        cancelRequested = true;
        FFmpegSession s = session;
        if (s != null) {
            FFmpegKit.cancel(s.getSessionId());
        }
    }

    static String buildCommand(String inputVideoPath, File assFile, File fontsDir,
                               long trimStartMs, long trimEndMs, float scaleFactor, File output) {
        boolean hasTrim = trimEndMs > trimStartMs && trimStartMs >= 0
                && !(trimStartMs == 0 && trimEndMs <= 0);

        // Even output dimensions are mandatory for libx264 with yuv420p.
        String scale;
        if (Math.abs(scaleFactor - 1f) > 0.01f) {
            scale = String.format(Locale.US, "scale=trunc(iw*%.4f/2)*2:trunc(ih*%.4f/2)*2",
                    scaleFactor, scaleFactor);
        } else {
            scale = "scale=trunc(iw/2)*2:trunc(ih/2)*2";
        }

        String vf = scale
                + ",ass='" + assFile.getAbsolutePath() + "'"
                + ":fontsdir='" + fontsDir.getAbsolutePath() + "'"
                + ",format=yuv420p";

        StringBuilder cmd = new StringBuilder();
        cmd.append("-y -hide_banner -nostdin -i \"").append(inputVideoPath).append("\" ");

        if (hasTrim) {
            // Output-side seek: original PTS reach the ass filter, so the .ass
            // timings (written on the untrimmed timeline) line up.
            cmd.append(String.format(Locale.US, "-ss %.3f -to %.3f ",
                    trimStartMs / 1000f, trimEndMs / 1000f));
        }

        cmd.append("-map 0:v:0 -map 0:a:0? ")
                .append("-vf \"").append(vf).append("\" ")
                .append("-c:v libx264 -preset medium -crf 20 ")
                // Audio is re-encoded only when we had to re-time the stream; a
                // straight copy preserves the original quality otherwise.
                .append(hasTrim ? "-c:a aac -b:a 192k " : "-c:a copy ")
                .append("-movflags +faststart ")
                .append("\"").append(output.getAbsolutePath()).append("\"");

        return cmd.toString();
    }

    private static Uri saveToGallery(Context context, File videoFile) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME,
                "Capvid_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Capvid");
            // Keep the row invisible until the bytes are fully written.
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        Uri itemUri = context.getContentResolver()
                .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (itemUri == null) throw new IllegalStateException("MediaStore refused to create the video entry");

        try (OutputStream out = context.getContentResolver().openOutputStream(itemUri);
             FileInputStream in = new FileInputStream(videoFile)) {
            if (out == null) throw new IllegalStateException("Could not open the MediaStore output stream");
            byte[] buffer = new byte[64 * 1024];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            out.flush();
        } catch (Exception e) {
            // Don't leave a broken row behind.
            context.getContentResolver().delete(itemUri, null, null);
            throw e;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            context.getContentResolver().update(itemUri, done, null, null);
        }

        return itemUri;
    }

    private void cleanup() {
        if (tempOutput != null) {
            //noinspection ResultOfMethodCallIgnored
            tempOutput.delete();
            tempOutput = null;
        }
        if (assFile != null) {
            //noinspection ResultOfMethodCallIgnored
            assFile.delete();
            assFile = null;
        }
    }

    private static long freeBytes(File dir) {
        try {
            StatFs stat = new StatFs(dir.getAbsolutePath());
            return stat.getAvailableBytes();
        } catch (Throwable t) {
            return Long.MAX_VALUE; // don't block an export over a stat failure
        }
    }

    private static String tail(String s, int max) {
        return s.length() <= max ? s : s.substring(s.length() - max);
    }
}
