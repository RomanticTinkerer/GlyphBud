package com.glyphbud

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.glyphbud.ui.ActionCard
import com.glyphbud.ui.ConnectedChoice
import com.glyphbud.ui.GroupRow
import com.glyphbud.ui.Header
import com.glyphbud.ui.IconBadge
import com.glyphbud.ui.Section
import com.glyphbud.ui.SettingsGroup
import com.glyphbud.ui.ToggleCard
import com.glyphbud.ui.GlyphTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    // Bumped on every resume so the setup rows re-check permissions granted in Settings.
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GlyphTheme { Screen(resumes) } }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }
}

private val Feature.icon
    get() = when (this) {
        Feature.CALLS -> Icons.Filled.Call
        Feature.ALARMS -> Icons.Filled.Alarm
        Feature.REMINDERS -> Icons.Filled.Event
        Feature.NOTIFICATIONS -> Icons.Filled.Notifications
        Feature.CHARGING -> Icons.Filled.BatteryChargingFull
    }

private fun Context.preview(feature: Feature) {
    val steps = when (feature) {
        Feature.CALLS -> Patterns.call()
        Feature.ALARMS -> Patterns.alarm()
        Feature.REMINDERS -> Patterns.reminder()
        Feature.NOTIFICATIONS -> Patterns.notification()
        Feature.CHARGING -> Patterns.charging(
            getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
        )
    }
    Glyph.show(this, Source.PREVIEW, steps.take(12))
}

@Composable
private fun Screen(resumes: Int) {
    val context = LocalContext.current
    val torch = remember { Glyph.torch(context) }
    val listening = remember(resumes) {
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
    val unrestricted = remember(resumes) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(
            "GlyphBud",
            when {
                !torch.available -> "No flash found on this phone"
                torch.maxLevel > 1 -> "Your flash has ${torch.maxLevel} brightness levels"
                else -> "Your flash is on/off only, so glyphs blink instead of dimming"
            },
        )
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { HeroCard() }
            if (!listening || !unrestricted) item {
                Section("Setup") {
                    SettingsGroup(
                        listOfNotNull<GroupRow>(
                            if (listening) null else { shape ->
                                ActionCard(
                                    Icons.Filled.NotificationsActive,
                                    "Allow notification access",
                                    "Needed for calls, alarms, reminders and charging",
                                    shape,
                                ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                            },
                            if (unrestricted) null else { shape ->
                                ActionCard(
                                    Icons.Filled.BatteryAlert,
                                    "Let GlyphBud run in the background",
                                    "Find GlyphBud and pick \"Don't optimise\" so Samsung doesn't put it to sleep",
                                    shape,
                                ) { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                            },
                        ),
                    )
                }
            }
            item {
                Section("Glyphs") {
                    SettingsGroup(
                        Feature.entries.map<Feature, GroupRow> { feature ->
                            { shape ->
                                PrefToggle(feature.name, feature.default) { checked, onChange ->
                                    ToggleCard(
                                        feature.icon, feature.title, feature.subtitle, checked, onChange, shape,
                                        onLongClick = { context.preview(feature) },
                                    )
                                }
                            }
                        } + listOf<GroupRow> { shape -> MusicToggle(shape) },
                    )
                }
            }
            item {
                Section("Comfort") {
                    SettingsGroup(listOf<GroupRow> { shape -> MotionCuesToggle(shape) })
                }
            }
            item {
                Section("Behaviour") {
                    SettingsGroup(
                        listOf<GroupRow>(
                            { shape ->
                                PrefToggle(PASS_DND, false) { checked, onChange ->
                                    ToggleCard(
                                        Icons.Filled.DoNotDisturbOn,
                                        "Pass through Do Not Disturb",
                                        "Flash even when Do Not Disturb is on. When off, only people and apps allowed through DND can flash",
                                        checked, onChange, shape,
                                    )
                                }
                            },
                            { shape ->
                                PrefToggle(FACE_DOWN_ONLY, false) { checked, onChange ->
                                    ToggleCard(
                                        Icons.Filled.ScreenRotation,
                                        "Only when face down",
                                        "The flash is on the back, so skip glyphs while the screen faces up. Music mode ignores this",
                                        checked, onChange, shape,
                                    )
                                }
                            },
                            { shape -> BrightnessCap(shape) },
                        ),
                    )
                }
            }
            item {
                Text(
                    "Long-press any glyph to preview it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Live mirror of the flash, plus buttons to try each level. */
@Composable
private fun HeroCard() {
    val context = LocalContext.current
    val glow by animateFloatAsState(Glyph.level / 5f, label = "glow")
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = glow), CircleShape),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (Glyph.level == 0) "Flash idle" else "Flash at L${Glyph.level}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Tap a level to light it for two seconds",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            ConnectedChoice(listOf("Off", "L1", "L2", "L3", "L4", "L5"), Glyph.level) { level ->
                if (level == 0) Glyph.stop(Source.PREVIEW)
                else Glyph.show(context, Source.PREVIEW, Patterns.level(level))
            }
        }
    }
}

/** Runs [content] with a switch state that reads and writes a boolean preference. */
@Composable
private fun PrefToggle(key: String, default: Boolean, content: @Composable (Boolean, (Boolean) -> Unit) -> Unit) {
    val prefs = LocalContext.current.prefs
    var checked by remember { mutableStateOf(prefs.getBoolean(key, default)) }
    content(checked) {
        checked = it
        prefs.edit { putBoolean(key, it) }
    }
}

@Composable
private fun MusicToggle(shape: Shape) {
    val context = LocalContext.current
    var warning by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.RECORD_AUDIO] == true) MusicService.start(context)
    }
    val start = {
        permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
    }
    var band by remember { mutableStateOf(context.band) }
    ToggleCard(
        Icons.Filled.GraphicEq,
        "Music visualiser",
        "Follows the ${band.label.lowercase()} of whatever is playing. Tap to pick what it follows",
        MusicService.running,
        { on ->
            when {
                !on -> MusicService.stop(context)
                context.prefs.getBoolean(MUSIC_WARNED, false) -> start()
                else -> warning = true
            }
        },
        shape,
        options = {
            ConnectedChoice(Band.entries.map { it.label }, band.ordinal) {
                band = Band.entries[it]
                context.prefs.edit { putString(MUSIC_BAND, band.name) }
            }
        },
    )
    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false },
            title = { Text("Flashing light") },
            text = {
                Text(
                    "Music mode pulses the flash with the beat. It's capped below 3 flashes a second, " +
                        "but skip it if flashing light bothers you.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    warning = false
                    context.prefs.edit { putBoolean(MUSIC_WARNED, true) }
                    start()
                }) { Text("Turn on") }
            },
            dismissButton = { TextButton(onClick = { warning = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MotionCuesToggle(shape: Shape) {
    val context = LocalContext.current
    ToggleCard(
        Icons.Filled.DirectionsCar,
        "Motion cues",
        "Dots on the screen edges move with the car to ease motion sickness. Also in Quick Settings",
        MotionCuesService.running,
        { on ->
            when {
                !on -> MotionCuesService.stop(context)
                Settings.canDrawOverlays(context) -> MotionCuesService.start(context)
                else -> context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                )
            }
        },
        shape,
    )
}

@Composable
private fun BrightnessCap(shape: Shape) {
    val prefs = LocalContext.current.prefs
    var cap by remember { mutableIntStateOf(prefs.getInt(MAX_LEVEL, 5)) }
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.BrightnessMedium)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Max brightness", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "Nothing goes brighter than L$cap. Lower it if the phone gets warm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Slider(
                value = cap.toFloat(),
                onValueChange = { cap = it.roundToInt() },
                onValueChangeFinished = { prefs.edit { putInt(MAX_LEVEL, cap) } },
                valueRange = 1f..5f,
                steps = 3,
            )
        }
    }
}
