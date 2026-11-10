package com.glyphbud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternsTest {
    @Test
    fun chargingStepsCountTheBattery() {
        fun top(percent: Int) = Patterns.charging(percent).maxOf { it.level }
        assertEquals(1, top(0))
        assertEquals(1, top(20))
        assertEquals(3, top(41))
        assertEquals(5, top(100))
    }

    @Test
    fun patternsStayInRangeAndUnderThreeFlashesASecond() {
        val all = listOf(Patterns.call(), Patterns.alarm(), Patterns.reminder(), Patterns.notification())
        assertTrue(all.flatten().all { it.level in 0..5 })
        // A flash is two changes, so the shortest flash takes 2 * MIN_STEP_MS.
        assertTrue(1000.0 / (2 * Patterns.MIN_STEP_MS) < 3)
    }

    @Test
    fun callStopsOnItsOwnAfterAboutThirtySeconds() {
        assertEquals(30_000.0, Patterns.call().sumOf { it.ms }.toDouble(), 500.0)
    }

    @Test
    fun callsAndAlarmsSweepAllFiveLevelsWithoutGoingDark() {
        assertEquals((1..5).toSet(), Patterns.call().map { it.level }.toSet())
        assertEquals((1..5).toSet(), Patterns.alarm().map { it.level }.toSet())
    }

    @Test
    fun bandsReadOnlyTheirOwnBins() {
        val rate = 44_100_000
        val bass = ByteArray(1024).also { it[2] = 100 } // bin 1, about 43 Hz
        val treble = ByteArray(1024).also { it[400] = 100 } // bin 200, about 8.6 kHz
        assertEquals(0f, Patterns.energy(ByteArray(1024), rate, Band.BASS))
        assertTrue(Patterns.energy(bass, rate, Band.BASS) > 0f)
        assertEquals(0f, Patterns.energy(treble, rate, Band.BASS))
        assertTrue(Patterns.energy(treble, rate, Band.TREBLE) > 0f)
        assertTrue(Patterns.energy(treble, rate, Band.ALL) > 0f)
    }
}
