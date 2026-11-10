package com.glyphbud

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat

class GlyphListener : NotificationListenerService() {
    private val seen = HashSet<String>()

    private val power = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val dndOn = getSystemService(NotificationManager::class.java).currentInterruptionFilter >
                NotificationManager.INTERRUPTION_FILTER_ALL
            if (!isOn(Feature.CHARGING) || (dndOn && !prefs.getBoolean(PASS_DND, false))) return
            val percent = getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            glyph(Source.CHARGING, Patterns.charging(percent))
        }
    }

    override fun onListenerConnected() {
        ContextCompat.registerReceiver(
            this, power, IntentFilter(Intent.ACTION_POWER_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onListenerDisconnected() {
        runCatching { unregisterReceiver(power) }
        // Samsung and friends unbind listeners to save battery. Ask to come back.
        requestRebind(ComponentName(this, GlyphListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankings: RankingMap) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        val ranking = Ranking().also { rankings.getRanking(sbn.key, it) }
        val allowed = ranking.matchesInterruptionFilter() || prefs.getBoolean(PASS_DND, false)

        // Calls are checked on every update, so answering the call stops the flash.
        if (n.category == Notification.CATEGORY_CALL) {
            if (allowed && isOn(Feature.CALLS) && ringing(n)) glyph(Source.CALL, Patterns.call())
            else Glyph.stop(Source.CALL)
            return
        }
        // Everything else flashes once per notification, not on every progress update.
        if (!seen.add(sbn.key) || !allowed) return
        when {
            n.category == Notification.CATEGORY_ALARM && n.fullScreenIntent != null ->
                if (isOn(Feature.ALARMS)) glyph(Source.ALARM, Patterns.alarm())
            n.category == Notification.CATEGORY_REMINDER || n.category == Notification.CATEGORY_EVENT ->
                if (isOn(Feature.REMINDERS)) glyph(Source.REMINDER, Patterns.reminder())
            isOn(Feature.NOTIFICATIONS) && !sbn.isOngoing &&
                n.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT ->
                glyph(Source.NOTIFICATION, Patterns.notification())
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
        val n = sbn.notification
        when {
            n.category == Notification.CATEGORY_CALL -> Glyph.stop(Source.CALL)
            n.category == Notification.CATEGORY_ALARM && n.fullScreenIntent != null -> Glyph.stop(Source.ALARM)
        }
    }

    private fun ringing(n: Notification) =
        getSystemService(AudioManager::class.java).mode == AudioManager.MODE_RINGTONE ||
            n.extras.getInt(Notification.EXTRA_CALL_TYPE) == Notification.CallStyle.CALL_TYPE_INCOMING

    private fun glyph(source: Source, steps: List<Step>) {
        if (!prefs.getBoolean(FACE_DOWN_ONLY, false)) return Glyph.show(this, source, steps)
        // ponytail: one accelerometer read when the alert arrives. Flipping the phone later won't start it.
        val sensors = getSystemService(SensorManager::class.java)
        val accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return Glyph.show(this, source, steps)
        sensors.registerListener(object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                sensors.unregisterListener(this)
                if (e.values[2] < -7f) Glyph.show(this@GlyphListener, source, steps)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }
}
