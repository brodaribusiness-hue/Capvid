package com.saad.capvid.export

import com.saad.capvid.caption.StyleCatalog
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.Project
import com.saad.capvid.model.VideoTransform
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoExporterTest {
    @Test
    fun commandContainsSubtitleBurnInTrimAndOriginalAudioCopy() {
        val project = Project.create("/input/source.mp4", durationMs = 10_000L).copy(
            style = StyleCatalog.find("glow"),
            words = listOf(CaptionWord(text = "offline", startMs = 0, endMs = 900)),
            transform = VideoTransform(trimStartMs = 1_000L, trimEndMs = 6_000L)
        )
        val command = VideoExporter.buildCommand(
            File(project.videoPath), File("/output/final.mp4"), File("/tmp/captions.ass"), File("/tmp/fonts"), project,
            VideoExporter.Metadata(1920, 1080, 10_000L, 30f)
        )
        assertTrue(command.contains("-ss 1.000"))
        assertTrue(command.contains("-t 5.000"))
        assertTrue(command.contains("subtitles="))
        assertTrue(command.contains("-c:a copy"))
        assertTrue(command.contains("libx264"))
    }
}
