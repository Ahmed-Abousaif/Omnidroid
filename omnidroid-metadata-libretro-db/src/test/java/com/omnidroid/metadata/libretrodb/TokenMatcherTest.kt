package com.omnidroid.metadata.libretrodb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenMatcherTest {
    @Test
    fun selectSignificantTokenPrefersNonStopWords() {
        assertEquals("mario", TokenMatcher.selectSignificantToken("super mario world"))
        assertEquals("legend", TokenMatcher.selectSignificantToken("the legend of zelda"))
        assertEquals("fighter", TokenMatcher.selectSignificantToken("street fighter"))
    }

    @Test
    fun exactMatchScores100() {
        assertEquals(100, TokenMatcher.bestScore("super mario world", "super mario world"))
    }

    @Test
    fun subsetMatchScoresHigh() {
        val score = TokenMatcher.bestScore("super mario world", "super mario world usa")
        assertTrue("Expected score >= 85, was $score", score >= 85)
    }

    @Test
    fun minorTypoMatchScoresHigh() {
        val score = TokenMatcher.bestScore("pokemon emerald", "pokemon - emerald version")
        assertTrue("Expected score >= 85, was $score", score >= 85)
    }

    @Test
    fun completelyDifferentGameScoresLow() {
        val score = TokenMatcher.bestScore("super mario world", "sonic the hedgehog")
        assertTrue("Expected score < 50, was $score", score < 50)
    }

    @Test
    fun tokenSetRatioScores100OnPermutedWords() {
        val score = TokenMatcher.bestScore("world super mario", "super mario world")
        assertEquals(100, score)
    }
}
