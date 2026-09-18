package com.saad.capvid.export

import com.saad.capvid.caption.StyleCatalog
import com.saad.capvid.model.CaptionEffect
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.Project
import org.junit.Assert.assertTrue
import org.junit.Test

class AssSubtitleBuilderTest {
    @Test
    fun writesAssHeaderWordTimingAndKaraokeEffect() {
        val project = Project.create("/tmp/video.mp4", durationMs = 2_000L).copy(
            style = StyleCatalog.find("karaoke"),
            words = listOf(
                CaptionWord(id = "a", text = "Hello", startMs = 100, endMs = 650),
                CaptionWord(id = "b", text = "world!", startMs = 700, endMs = 1_400)
            )
        )
        val ass = AssSubtitleBuilder.build(project, 1280, 720)
        assertTrue(ass.contains("PlayResX: 1280"))
        assertTrue(ass.contains("PlayResY: 720"))
        assertTrue(ass.contains("Fontname"))
        assertTrue(ass.contains("0:00:00.10"))
        assertTrue(ass.contains("\\kf"))
        assertTrue(ass.contains("Hello"))
        assertTrue(ass.contains("world!"))
    }

    @Test
    fun everyPresetEmitsAConcreteEffectOrStyleTag() {
        StyleCatalog.all.forEach { style ->
            val project = Project.create("video.mp4").copy(
                style = style,
                words = listOf(CaptionWord(text = "Effect", startMs = 0, endMs = 500))
            )
            val ass = AssSubtitleBuilder.build(project)
            assertTrue("${style.id} has no dialogue", ass.contains("Dialogue:"))
            assertTrue("${style.id} has no effect body", ass.substringAfter("[Events]").contains("\\"))
        }
    }

    @Test
    fun colorsAreConvertedToAssBgr() {
        val project = Project.create("video.mp4").copy(
            style = StyleCatalog.defaultStyle().copy(textColor = "#123456"),
            words = listOf(CaptionWord(text = "Color", startMs = 0, endMs = 500))
        )
        assertTrue(AssSubtitleBuilder.build(project).contains("&H00563412&"))
    }
}
