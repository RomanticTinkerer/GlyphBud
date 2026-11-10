package com.glyphbud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Cues {
    // Calibration knobs. Normal driving is 1 to 3 m/s², so dots move about 12 to 36dp.
    const val DP_PER_MS2 = 12f
    const val MAX_DY_DP = 48f
    /** Smaller than the edge inset so side dots never slide off screen. */
    const val MAX_DX_DP = 20f
    const val SMOOTHING = 0.15f
    const val DOTS_PER_EDGE = 6

    /**
     * Where the dots should sit, in screen dp, for an acceleration in device axes.
     * They act like loose objects in the car: speeding up pushes them toward you,
     * a left turn slides them right.
     */
    fun cueOffset(ax: Float, ay: Float, rotation: Int): Pair<Float, Float> {
        // Device axes to screen axes (y up), per the display's rotation.
        val (sx, sy) = when (rotation) {
            Surface.ROTATION_90 -> -ay to ax
            Surface.ROTATION_180 -> -ax to -ay
            Surface.ROTATION_270 -> ay to -ax
            else -> ax to ay
        }
        // Screen y grows downward, hence the sign flip on dy only.
        return (-DP_PER_MS2 * sx).coerceIn(-MAX_DX_DP, MAX_DX_DP) to
            (DP_PER_MS2 * sy).coerceIn(-MAX_DY_DP, MAX_DY_DP)
    }
}

/** Draws [Cues.DOTS_PER_EDGE] dots down each side edge, shifted by [dx] and [dy] (dp). */
private class DotsView(context: Context) : View(context) {
    var dx = 0f
    var dy = 0f
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    override fun onDraw(canvas: Canvas) {
        val radius = 6 * density
        val inset = 24 * density
        for (i in 1..Cues.DOTS_PER_EDGE) {
            val y = height * i / (Cues.DOTS_PER_EDGE + 1f) + dy * density
            for (x in floatArrayOf(inset, width - inset)) {
                canvas.drawCircle(x + dx * density, y, radius, fill)
                canvas.drawCircle(x + dx * density, y, radius, ring)
            }
        }
    }
}

class MotionCuesTile : TileService() {
    override fun onStartListening() = show(MotionCuesService.running)

    override fun onClick() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        val on = !MotionCuesService.running
        if (on) MotionCuesService.start(this) else MotionCuesService.stop(this)
        show(on)
    }

    private fun show(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}

class MotionCuesService : Service(), SensorEventListener {
    companion object {
        var running by mutableStateOf(false)
            private set
        private const val STOP = "stop"

        fun start(context: Context) =
            context.startForegroundService(Intent(context, MotionCuesService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, MotionCuesService::class.java))
    }

    private lateinit var window: Context
    private lateinit var dots: DotsView
    private var gravityX = 0f
    private var gravityY = 0f

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(2, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (intent?.action == STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            window = createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            dots = DotsView(window)
            window.getSystemService(WindowManager::class.java).addView(dots, overlayParams())
            val sensors = getSystemService(SensorManager::class.java)
            val sensor = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
                ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            running = true
            refreshTile()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (!running) return
        getSystemService(SensorManager::class.java).unregisterListener(this)
        window.getSystemService(WindowManager::class.java).removeView(dots)
        running = false
        refreshTile()
    }

    override fun onSensorChanged(e: SensorEvent) {
        var ax = e.values[0]
        var ay = e.values[1]
        if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // No linear acceleration sensor: strip gravity with a slow low-pass.
            gravityX += 0.02f * (ax - gravityX)
            gravityY += 0.02f * (ay - gravityY)
            ax -= gravityX
            ay -= gravityY
        }
        val (tx, ty) = Cues.cueOffset(ax, ay, window.display.rotation)
        dots.dx += Cues.SMOOTHING * (tx - dots.dx)
        dots.dy += Cues.SMOOTHING * (ty - dots.dy)
        dots.invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Android blocks touches through another app's overlay above this opacity (0.8 by default).
        alpha = getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
    }

    private fun refreshTile() =
        TileService.requestListeningState(this, ComponentName(this, MotionCuesTile::class.java))

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("motion", "Motion cues", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, MotionCuesService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, "motion")
            .setSmallIcon(R.drawable.ic_motion_cues)
            .setContentTitle("Motion cues are on")
            .setContentText("The dots move with the car to ease motion sickness")
            .setContentIntent(stop)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }
}
