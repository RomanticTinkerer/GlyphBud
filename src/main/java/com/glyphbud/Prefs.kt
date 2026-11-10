package com.glyphbud

import android.content.Context

val Context.prefs get() = getSharedPreferences("glyphbud", Context.MODE_PRIVATE)!!

const val PASS_DND = "pass_dnd"
const val FACE_DOWN_ONLY = "face_down_only"
const val MAX_LEVEL = "max_level"
const val MUSIC_WARNED = "music_warned"
const val MUSIC_BAND = "music_band"

/** The part of the sound music mode follows. */
enum class Band(val label: String, val lowHz: Int, val highHz: Int) {
    BASS("Bass", 20, 150),
    VOCALS("Vocals", 300, 3000),
    TREBLE("Treble", 4000, 16000),
    ALL("All", 20, 16000),
}

val Context.band get() = Band.valueOf(prefs.getString(MUSIC_BAND, null) ?: Band.BASS.name)

enum class Feature(val title: String, val subtitle: String, val default: Boolean = true) {
    CALLS("Calls", "Sweeps through all five levels while the phone rings"),
    ALARMS("Alarms and timers", "Sweeps slowly through all five levels until you dismiss them"),
    REMINDERS("Reminders", "Calendar events and reminders breathe three times"),
    NOTIFICATIONS("Other notifications", "One soft breath for everything else", default = false),
    CHARGING("Charging", "Steps up once per 20% of battery when plugged in"),
}

fun Context.isOn(feature: Feature) = prefs.getBoolean(feature.name, feature.default)
