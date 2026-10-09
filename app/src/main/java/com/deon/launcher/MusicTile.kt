package com.deon.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

/** Exists only so the launcher may read the media sessions (music now playing); it handles no notification. */
class MediaListener : NotificationListenerService()

private const val YT_MUSIC = "com.google.android.apps.youtube.music"

private fun hasNotificationAccess(context: Context) =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

private class Track(
    val title: String, val artist: String, val art: Bitmap?, val playing: Boolean,
    val duration: Long, val like: PlaybackState.CustomAction?, val dislike: PlaybackState.CustomAction?,
)

/** The thumbs up / down that YT Music exposes as custom actions on its session. */
private fun thumbs(state: PlaybackState?): Pair<PlaybackState.CustomAction?, PlaybackState.CustomAction?> {
    val actions = state?.customActions.orEmpty()
    fun has(a: PlaybackState.CustomAction, vararg w: String) = w.any { a.action.contains(it, true) || a.name.toString().contains(it, true) }
    val dislike = actions.firstOrNull { has(it, "dislike", "thumbs_down", "thumb_down", "pouce vers le bas", "je n'aime pas") }
    val like = actions.firstOrNull { it != dislike && has(it, "like", "thumbs_up", "thumb_up", "pouce", "j'aime") }
    return like to dislike
}

/**
 * Music player tile in the YT Music widget layout: cover on the left, title and artist, the YT Music badge, and
 * thumbs up, previous, play, next, thumbs down along the bottom. It follows whichever app is playing, YT Music first.
 * [rows] is the tile height in grid cells; one row gives a compact bar.
 */
@Composable
fun MusicTile(rows: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    var allowed by remember { mutableStateOf(hasNotificationAccess(context)) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var track by remember { mutableStateOf<Track?>(null) }
    var position by remember { mutableLongStateOf(0L) }

    // Access is granted in the system settings, outside the launcher: check again now and then.
    LaunchedEffect(Unit) { while (!allowed) { delay(2000); allowed = hasNotificationAccess(context) } }

    DisposableEffect(allowed) {
        if (!allowed) return@DisposableEffect onDispose { }
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(context, MediaListener::class.java)
        fun pick(list: List<MediaController>?) {
            val all = list.orEmpty()
            controller = all.firstOrNull { it.packageName == YT_MUSIC }
                ?: all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: all.firstOrNull()
        }
        val onChange = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        runCatching {
            msm.addOnActiveSessionsChangedListener(onChange, listener)
            pick(msm.getActiveSessions(listener))
        }
        onDispose { runCatching { msm.removeOnActiveSessionsChangedListener(onChange) } }
    }

    DisposableEffect(controller) {
        val ctl = controller
        fun read() {
            val m = ctl?.metadata
            val state = ctl?.playbackState
            val (like, dislike) = thumbs(state)
            track = if (ctl == null || m == null) null else Track(
                m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
                m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART),
                state?.state == PlaybackState.STATE_PLAYING,
                m.getLong(MediaMetadata.METADATA_KEY_DURATION), like, dislike
            )
        }
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = read()
            override fun onPlaybackStateChanged(state: PlaybackState?) = read()
            override fun onSessionDestroyed() { controller = null }
        }
        ctl?.registerCallback(cb)
        read()
        onDispose { ctl?.unregisterCallback(cb) }
    }

    // Progress, extrapolated from the last position the player reported.
    LaunchedEffect(controller) {
        while (true) {
            val s = controller?.playbackState
            position = if (s == null) 0 else if (s.state == PlaybackState.STATE_PLAYING)
                s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
            else s.position
            delay(500)
        }
    }

    val t = track
    val ctl = controller
    Row(modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        // Cover, as tall as the tile.
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(c.onSurface.copy(alpha = .08f)),
            contentAlignment = Alignment.Center
        ) {
            if (t?.art != null) Image(t.art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else UIcon(Ui.Music, c.onSurfaceVariant, 26.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    when {
                        !allowed -> {
                            Text("Lecteur de musique", color = c.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Accès aux notifications nécessaire", color = c.onSurfaceVariant, fontSize = 14.sp)
                        }
                        t == null -> {
                            Text("Aucune lecture", color = c.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Lance ta musique, elle s'affichera ici", color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        else -> {
                            Text(t.title.ifEmpty { appLabel(context, ctl?.packageName) }, color = c.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (rows >= 2) YtBadge()
            }
            if (!allowed) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction("Autoriser") {
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                // Apps installed from a file may first need "restricted settings" allowed on their info page.
                SmallAction("Infos de l'appli") {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            else if (ctl != null && t != null) {
                if (rows >= 2 && t.duration > 0) Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(c.onSurface.copy(alpha = .12f))) {
                    Box(Modifier.fillMaxWidth((position.toFloat() / t.duration).coerceIn(0f, 1f)).fillMaxHeight().background(c.primary))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    if (rows >= 2) ThumbButton(ctl, t.like, Ui.Heart)
                    Control({ ctl.transportControls.skipToPrevious() }) { UIcon(Ui.Previous, c.onSurface, 20.sp) }
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(c.onSurface)
                            .clickable { if (t.playing) ctl.transportControls.pause() else ctl.transportControls.play() },
                        contentAlignment = Alignment.Center
                    ) { UIcon(if (t.playing) Ui.Pause else Ui.Play, c.surface, 20.sp) }
                    Control({ ctl.transportControls.skipToNext() }) { UIcon(Ui.Next, c.onSurface, 20.sp) }
                    if (rows >= 2) ThumbButton(ctl, t.dislike, null)
                }
            }
        }
    }
}

private fun appLabel(context: Context, pkg: String?) = pkg?.let {
    runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull()
} ?: "Musique"

/** Small red round badge with a play mark, like the one on the YT Music widget. */
@Composable
private fun YtBadge() = Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFFF0033)), contentAlignment = Alignment.Center) {
    UIcon(Ui.Play, Color.White, 12.sp)
}

@Composable
private fun Control(onClick: () -> Unit, icon: @Composable () -> Unit) =
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { icon() }

/** A thumb button using the player's own icon for its custom action; hidden when the player has none. */
@Composable
private fun ThumbButton(ctl: MediaController, action: PlaybackState.CustomAction?, fallback: Glyph?) {
    val context = LocalContext.current
    val c = MaterialTheme.colorScheme
    val icon: ImageBitmap? = remember(action, ctl.packageName) {
        action?.let {
            runCatching {
                context.packageManager.getResourcesForApplication(ctl.packageName)
                    .getDrawable(it.icon, null).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    if (action == null) { Spacer(Modifier.size(44.dp)); return }
    Control({ ctl.transportControls.sendCustomAction(action, null) }) {
        if (icon != null) Image(icon, action.name.toString(), Modifier.size(22.dp), colorFilter = ColorFilter.tint(c.onSurface))
        else if (fallback != null) UIcon(fallback, c.onSurface, 20.sp)
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.height(36.dp).tile(18.dp).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
