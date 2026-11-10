package com.glyphbud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.media.audiofx.Visualizer
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Pulses the flash with the chosen [Band]. Prefers the session a music player announces for
 * equalizer apps: attaching there also stops the player offloading audio to the DSP,
 * where the global mix (session 0) often reads as silence.
 */
class MusicService : Service() {
    companion object {
        var running by mutableStateOf(false)
            private set
        private const val STOP = "stop"

        fun start(context: Context) =
            context.startForegroundService(Intent(context, MusicService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, MusicService::class.java))
    }

    private var visualizer: Visualizer? = null
    private var session = 0
    private var peak = 0f
    private var lastPush = 0L
    private var lastBand = Band.BASS

    private val sessions = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            when {
                intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION && id != 0 -> attach(id)
                intent.action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION && id == session -> attach(0)
            }
        }
    }

    private val capture = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) {}

        override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) {
            val now = SystemClock.uptimeMillis()
            if (now - lastPush < Patterns.MIN_STEP_MS) return
            lastPush = now
            val band = this@MusicService.band
            if (band != lastBand) {
                lastBand = band
                peak = 0f
            }
            val energy = Patterns.energy(fft, samplingRate, band)
            peak = maxOf(energy, peak * 0.98f)
            // Calibration knob: below this peak it's silence or hiss, so stay dark.
            // Otherwise stay lit and move across all five levels.
            val level = if (peak < 1f) 0 else 1 + (energy / peak * 4).roundToInt()
            Glyph.show(this@MusicService, Source.MUSIC, listOf(Step(level, 600)))
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (!running) {
            running = true
            ContextCompat.registerReceiver(
                this, sessions,
                IntentFilter().apply {
                    addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                    addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
            attach(0)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(sessions) }
        visualizer?.release()
        Glyph.stop(Source.MUSIC)
    }

    private fun attach(id: Int) {
        visualizer?.release()
        val v = open(id)
        session = if (v != null) id else 0
        visualizer = v ?: open(0)
    }

    private fun open(id: Int) = runCatching {
        Visualizer(id).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            setDataCaptureListener(capture, Visualizer.getMaxCaptureRate(), false, true)
            enabled = true
        }
    }.getOrNull()

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("music", "Music mode", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, MusicService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, "music")
            .setSmallIcon(R.drawable.ic_glyph)
            .setContentTitle("Music mode is on")
            .setContentText("The flash follows the ${band.label.lowercase()}")
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }
}
