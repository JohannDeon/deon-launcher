package com.deon.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * An ordinary app cannot receive the phone's Bluetooth audio itself: on a head unit that is done by the unit's own
 * Bluetooth music app (A2DP sink). The DEON player's Bluetooth button opens that app; once it plays, its media
 * session shows up in the player like any other.
 */

/** A launchable app: package and label. */
data class LaunchableApp(val pkg: String, val label: String)

fun launchableApps(context: Context): List<LaunchableApp> = context.packageManager
    .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
    .filter { it.pkg != context.packageName }
    .distinctBy { it.pkg }
    .sortedBy { it.label.lowercase() }

// Head unit makers name it differently: "BT Music", "Musique Bluetooth", "A2DP", "蓝牙音乐"…
private val btWords = listOf("btmusic", "bt music", "bt_music", "bluetooth music", "musique bluetooth", "musique bt", "a2dp", "蓝牙音乐")
private val btLoose = listOf("bluetooth", "蓝牙", ".bt.", ".bt", "bt.")

/** The head unit's Bluetooth music app: the user's choice in the settings, else the best guess by name. */
fun bluetoothMusicApp(context: Context, chosen: String): LaunchableApp? {
    val apps = launchableApps(context)
    apps.firstOrNull { it.pkg == chosen }?.let { return it }
    fun key(a: LaunchableApp) = "${a.pkg.lowercase()} ${a.label.lowercase()}"
    return apps.firstOrNull { a -> btWords.any { it in key(a) } }
        ?: apps.firstOrNull { a -> btLoose.any { it in key(a) } && ("music" in key(a) || "musique" in key(a) || "audio" in key(a)) }
}

/** Opens the Bluetooth music app, or the Bluetooth settings when none is found so the phone can at least be paired. */
fun openBluetoothMusic(context: Context, chosen: String) {
    val app = bluetoothMusicApp(context, chosen)
    val intent = app?.let { context.packageManager.getLaunchIntentForPackage(it.pkg) }
    if (intent != null) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return
    }
    Toast.makeText(
        context, "Appli musique Bluetooth introuvable : choisis-la dans Réglages DEON. En attendant, voici le Bluetooth.",
        Toast.LENGTH_LONG
    ).show()
    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The Bluetooth rune, drawn as a line icon (coolicons has none). */
@Composable
fun BluetoothIcon(color: Color, size: Dp = 20.dp) = Canvas(Modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    drawPath(
        Path().apply {
            moveTo(w * .28f, h * .30f)
            lineTo(w * .72f, h * .68f)
            lineTo(w * .50f, h * .88f)
            lineTo(w * .50f, h * .12f)
            lineTo(w * .72f, h * .32f)
            lineTo(w * .28f, h * .70f)
        },
        color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
