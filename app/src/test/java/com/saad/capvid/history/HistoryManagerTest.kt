package com.saad.capvid.history

import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryManagerTest {
    @Test
    fun undoRedoRestoresImmutableProjectSnapshots() {
        val initial = Project.create("video.mp4")
        val manager = HistoryManager(initial)
        val withWord = initial.copy(words = listOf(CaptionWord(text = "Hello", startMs = 0, endMs = 500)))
        val withTwoWords = withWord.copy(words = withWord.words + CaptionWord(text = "again", startMs = 500, endMs = 900))

        manager.applySnapshot(withWord, "Add caption")
        manager.applySnapshot(withTwoWords, "Add second caption")
        assertEquals(2, manager.current.words.size)
        assertTrue(manager.canUndo)

        manager.undo()
        assertEquals(1, manager.current.words.size)
        manager.undo()
        assertEquals(0, manager.current.words.size)
        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)

        manager.redo()
        assertEquals("Hello", manager.current.words.single().text)
        val changed = manager.current.copy(name = "Changed")
        manager.applySnapshot(changed, "Rename")
        assertFalse(manager.canRedo)
    }
}
