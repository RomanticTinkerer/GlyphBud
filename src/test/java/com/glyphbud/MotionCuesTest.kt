package com.glyphbud

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionCuesTest {
    private fun assertCue(dx: Float, dy: Float, actual: Pair<Float, Float>) {
        assertEquals(dx, actual.first, 0.001f)
        assertEquals(dy, actual.second, 0.001f)
    }

    @Test
    fun speedingUpPushesDotsTowardYou() {
        assertCue(0f, 24f, Cues.cueOffset(0f, 2f, Surface.ROTATION_0))
    }

    @Test
    fun leftTurnSlidesDotsRight() {
        assertCue(12f, 0f, Cues.cueOffset(-1f, 0f, Surface.ROTATION_0))
    }

    @Test
    fun hardBumpsAreClamped() {
        assertCue(-Cues.MAX_DX_DP, Cues.MAX_DY_DP, Cues.cueOffset(100f, 100f, Surface.ROTATION_0))
    }

    @Test
    fun landscapeSwapsAxes() {
        // Device x points up the screen at 90° and down it at 270°.
        assertCue(0f, 24f, Cues.cueOffset(2f, 0f, Surface.ROTATION_90))
        assertCue(0f, -24f, Cues.cueOffset(2f, 0f, Surface.ROTATION_270))
        assertCue(0f, -24f, Cues.cueOffset(0f, 2f, Surface.ROTATION_180))
    }
}
