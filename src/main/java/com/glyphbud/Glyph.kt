package com.glyphbud

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.TreeMap
import kotlin.math.hypot

/** Declaration order is priority: earlier entries interrupt later ones. */
enum class Source { PREVIEW, CALL, ALARM, REMINDER, NOTIFICATION, CHARGING, MUSIC }

/** Flash at [level] (0 off, 1..5) for [ms]. */
data class Step(val level: Int, val ms: Long)

object Patterns {
    /** Each step lasts at least this long, which keeps any pattern under 3 flashes a second (the photosensitive seizure limit). */
    const val MIN_STEP_MS = 170L

    fun call() = repeat(19, *sweep(200))
    fun alarm() = repeat(30, *sweep(250))
    fun reminder() = repeat(3, *breath())
    fun notification() = breath().toList()
    fun level(level: Int) = listOf(Step(level, 2000))

    /** One step up per 20% of battery, so the number of steps tells the charge. */
    fun charging(percent: Int): List<Step> {
        val top = ((percent + 19) / 20).coerceIn(1, 5)
        return (1..top).map { Step(it, 400) } + Step(top, 600)
    }

    /** Average FFT magnitude across [band]. */
    fun energy(fft: ByteArray, samplingRateMilliHz: Int, band: Band): Float {
        val binHz = samplingRateMilliHz / 1000f / fft.size
        val last = fft.size / 2 - 1
        val from = (band.lowHz / binHz).toInt().coerceIn(1, last)
        val to = (band.highHz / binHz).toInt().coerceIn(from, last)
        var sum = 0f
        for (k in from..to) sum += hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
        return sum / (to - from + 1)
    }

    /** L1 up to L5 and back down, never fully off. */
    private fun sweep(ms: Long) = ((1..5) + (4 downTo 2)).map { Step(it, ms) }.toTypedArray()

    private fun breath() = arrayOf(Step(1, 250), Step(2, 250), Step(3, 600), Step(2, 250), Step(1, 250), Step(0, 400))
    private fun repeat(times: Int, vararg steps: Step) = List(times) { steps.toList() }.flatten()
}

/** Plays the highest-priority active pattern. Everything runs on the main thread. */
object Glyph {
    /** Current flash level, for the UI to mirror. */
    var level by mutableIntStateOf(0)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val active = TreeMap<Source, Iterator<Step>>()
    private var playing: Source? = null
    private var torch: Torch? = null
    private lateinit var app: Context

    fun torch(context: Context): Torch {
        app = context.applicationContext
        return torch ?: Torch(app).also { torch = it }
    }

    fun show(context: Context, source: Source, steps: List<Step>) {
        torch(context)
        handler.post {
            active[source] = steps.iterator()
            if (playing.let { it == null || source <= it }) tick.run()
        }
    }

    fun stop(source: Source) {
        handler.post { if (active.remove(source) != null && source == playing) tick.run() }
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            while (active.isNotEmpty()) {
                val (source, steps) = active.firstEntry()!!
                if (!steps.hasNext()) {
                    active.remove(source)
                    continue
                }
                val step = steps.next()
                playing = source
                set(step.level)
                handler.postDelayed(this, maxOf(step.ms, Patterns.MIN_STEP_MS))
                return
            }
            playing = null
            set(0)
        }
    }

    private fun set(level: Int) {
        val capped = level.coerceAtMost(app.prefs.getInt(MAX_LEVEL, 5))
        if (capped == this.level) return
        // Throws when the camera app owns the flash. Skipping the step is fine.
        runCatching { torch!!.set(capped) }
        this.level = capped
    }
}
