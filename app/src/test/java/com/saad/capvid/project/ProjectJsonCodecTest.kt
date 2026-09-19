package com.saad.capvid.project

import com.saad.capvid.caption.StyleCatalog
import com.saad.capvid.model.CaptionLanguage
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.VideoTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectJsonCodecTest {
    @Test
    fun roundTripPreservesVideoWordsStyleAndTransform() {
        val original = com.saad.capvid.model.Project.create("/data/video.mp4", "Demo", 12_345L, CaptionLanguage.URDU).copy(
            words = listOf(CaptionWord(id = "one", text = "سلام", startMs = 100, endMs = 900, highlighted = true)),
            style = StyleCatalog.find("neon").copy(positionX = 0.31f, positionY = 0.67f),
            transform = VideoTransform(trimStartMs = 500, trimEndMs = 8_000, zoom = 1.35f, panX = -0.2f, panY = 0.4f)
        )
        val json = ProjectJsonCodec.encode(original)
        val restored = ProjectJsonCodec.decode(json)
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.videoPath, restored.videoPath)
        assertEquals(original.language, restored.language)
        assertEquals(original.words, restored.words)
        assertEquals(original.style.id, restored.style.id)
        assertEquals(original.style.positionX, restored.style.positionX, 0.001f)
        assertEquals(original.transform.trimEndMs, restored.transform.trimEndMs)
        assertEquals(original.transform.zoom, restored.transform.zoom, 0.001f)
        assertTrue(json.contains("formatVersion"))
    }

    @Test
    fun splitRangesRoundTripAndDeleteWithUndoFriendlyTransform() {
        val split = VideoTransform().splitAt(5_000L, 10_000L)
        assertEquals(listOf(0L, 5_000L), split.segments.map { it.startMs })
        assertEquals(listOf(5_000L, 10_000L), split.segments.map { it.endMs })
        val remaining = split.deleteSegment(0, 10_000L)
        assertEquals(5_000L, remaining.trimStartMs)
        assertEquals(0L, remaining.trimEndMs)
        assertTrue(remaining.segments.isEmpty())
    }

    @Test
    fun legacyJsonUsesSafeDefaults() {
        val restored = ProjectJsonCodec.decode("{\"id\":\"legacy\",\"videoPath\":\"old.mp4\"}")
        assertEquals("legacy", restored.id)
        assertEquals("old.mp4", restored.videoPath)
        assertEquals(CaptionLanguage.ENGLISH, restored.language)
        assertFalse(restored.words.isNotEmpty())
        assertEquals(StyleCatalog.defaultStyle().id, restored.style.id)
    }
}
