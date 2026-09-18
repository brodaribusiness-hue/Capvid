package com.saad.capvid.caption

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordGroupingTest {
    @Test
    fun groupsBpeTokensByLeadingWhitespaceAndKeepsPunctuation() {
        val words = WordGrouping.groupTokens(
            listOf(
                WordGrouping.Token(" Hel", 0, 20),
                WordGrouping.Token("lo", 20, 40),
                WordGrouping.Token(",", 40, 50),
                WordGrouping.Token(" world", 80, 120),
                WordGrouping.Token("!", 120, 140)
            )
        )
        assertEquals(2, words.size)
        assertEquals("Hello,", words[0].text)
        assertEquals(0L, words[0].startMs)
        assertEquals(50L, words[0].endMs)
        assertEquals("world!", words[1].text)
        assertEquals(80L, words[1].startMs)
        assertTrue(words[1].endMs >= 140L)
    }

    @Test
    fun repairsZeroLengthAndNonMonotonicTokenTimes() {
        val words = WordGrouping.groupTokens(
            listOf(
                WordGrouping.Token(" one", 100, 100),
                WordGrouping.Token(" two", 40, 41)
            )
        )
        assertEquals(2, words.size)
        assertTrue(words[0].endMs > words[0].startMs)
        assertTrue(words[1].startMs >= words[0].endMs)
        assertTrue(words[1].endMs > words[1].startMs)
    }
}
