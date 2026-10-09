package com.deon.launcher

import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Size of the widget grid, in cells. */
const val GRID_COLS = 4
const val GRID_ROWS = 6

/** What a grid tile shows: the launcher's weather or music player, or another app's widget. */
enum class TileKind { WEATHER, MUSIC, APP }

/** One widget on the grid: cell position and span, corner radius in dp, and the app widget id for [TileKind.APP]. */
data class Tile(
    val key: Long, val kind: TileKind, val appId: Int,
    val x: Int, val y: Int, val w: Int, val h: Int, val radius: Int,
) {
    fun overlaps(o: Tile) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h
    fun inside() = x >= 0 && y >= 0 && w >= 1 && h >= 1 && x + w <= GRID_COLS && y + h <= GRID_ROWS
    fun encode() = "$key;${kind.name};$appId;$x;$y;$w;$h;$radius"

    companion object {
        fun decode(s: String) = runCatching {
            val p = s.split(';')
            Tile(p[0].toLong(), TileKind.valueOf(p[1]), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toInt(), p[7].toInt())
        }.getOrNull()
    }
}

/** The tiles on the grid, saved in the launcher preferences. */
class WidgetGridState(private val prefs: SharedPreferences, legacyAppWidgets: List<Int>) {
    val tiles = mutableStateListOf<Tile>()

    init {
        val saved = prefs.getString("grid", null)
        if (saved != null) tiles += saved.split('|').mapNotNull(Tile::decode)
        else {
            // First run with the grid: weather on top, then the widgets of the older column layout.
            tiles += Tile(1, TileKind.WEATHER, -1, 0, 0, GRID_COLS, 2, 28)
            legacyAppWidgets.forEach { add(TileKind.APP, it) }
            save()
        }
    }

    private fun save() = prefs.edit().putString("grid", tiles.joinToString("|") { it.encode() }).apply()

    fun fits(t: Tile) = t.inside() && tiles.none { it.key != t.key && it.overlaps(t) }

    /** Places a new tile full width at the first free rows (or a smaller one if space is short); false if the grid is full. */
    fun add(kind: TileKind, appId: Int = -1): Boolean {
        val key = (tiles.maxOfOrNull { it.key } ?: 0) + 1
        for ((w, h) in listOf(GRID_COLS to 2, GRID_COLS to 1, 2 to 2, 2 to 1, 1 to 1))
            for (y in 0..GRID_ROWS - h) for (x in 0..GRID_COLS - w) {
                val t = Tile(key, kind, appId, x, y, w, h, 28)
                if (fits(t)) { tiles += t; save(); return true }
            }
        return false
    }

    fun update(t: Tile) {
        val i = tiles.indexOfFirst { it.key == t.key }
        if (i >= 0 && fits(t)) { tiles[i] = t; save() }
    }

    fun setRadius(key: Long, radius: Int) {
        val i = tiles.indexOfFirst { it.key == key }
        if (i >= 0) { tiles[i] = tiles[i].copy(radius = radius); save() }
    }

    fun remove(key: Long) { tiles.removeAll { it.key == key }; save() }
}

/**
 * The widget grid. In [edit] mode a tile moves by dragging it, resizes from its bottom-right corner, and a tap selects
 * it so its corners and removal can be set in [selected].
 */
@Composable
fun WidgetGrid(
    state: WidgetGridState,
    edit: Boolean,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Tile) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    BoxWithConstraints(modifier) {
        val cellW = maxWidth / GRID_COLS
        val cellH = maxHeight / GRID_ROWS
        val d = LocalDensity.current
        val cellWpx = with(d) { cellW.toPx() }
        val cellHpx = with(d) { cellH.toPx() }
        if (edit) Canvas(Modifier.fillMaxSize()) {
            // Faint cells so the user sees where tiles can snap.
            for (x in 0 until GRID_COLS) for (y in 0 until GRID_ROWS) drawRoundRect(
                c.onBackground.copy(alpha = .08f),
                androidx.compose.ui.geometry.Offset(x * cellWpx + 6.dp.toPx(), y * cellHpx + 6.dp.toPx()),
                androidx.compose.ui.geometry.Size(cellWpx - 12.dp.toPx(), cellHpx - 12.dp.toPx()),
                CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            )
        }
        state.tiles.forEach { tile ->
            key(tile.key) {
                var drag by remember { mutableStateOf(IntOffset.Zero) }
                var grow by remember { mutableStateOf(IntOffset.Zero) }
                val current by rememberUpdatedState(tile)
                Box(
                    Modifier
                        .offset { IntOffset((tile.x * cellWpx).roundToInt() + drag.x, (tile.y * cellHpx).roundToInt() + drag.y) }
                        .size(
                            with(d) { (tile.w * cellWpx + grow.x).coerceAtLeast(cellWpx).toDp() },
                            with(d) { (tile.h * cellHpx + grow.y).coerceAtLeast(cellHpx).toDp() }
                        )
                        .padding(6.dp)
                ) {
                    content(tile)
                    if (edit) {
                        val on = selected == tile.key
                        Box(
                            Modifier.fillMaxSize()
                                .border(if (on) 3.dp else 1.5.dp, if (on) c.primary else c.onBackground.copy(alpha = .4f), RoundedCornerShape(tile.radius.dp))
                                .clickable { onSelect(if (on) null else tile.key) }
                                .pointerInput(tile.key) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            val t = current
                                            state.update(t.copy(x = t.x + (drag.x / cellWpx).roundToInt(), y = t.y + (drag.y / cellHpx).roundToInt()))
                                            drag = IntOffset.Zero
                                        },
                                        onDragCancel = { drag = IntOffset.Zero }
                                    ) { change, amount ->
                                        change.consume()
                                        drag += IntOffset(amount.x.roundToInt(), amount.y.roundToInt())
                                    }
                                }
                        )
                        // Resize handle.
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(c.primary)
                                .pointerInput(tile.key) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            val t = current
                                            state.update(
                                                t.copy(
                                                    w = (t.w + (grow.x / cellWpx).roundToInt()).coerceAtLeast(1),
                                                    h = (t.h + (grow.y / cellHpx).roundToInt()).coerceAtLeast(1)
                                                )
                                            )
                                            grow = IntOffset.Zero
                                        },
                                        onDragCancel = { grow = IntOffset.Zero }
                                    ) { change, amount ->
                                        change.consume()
                                        grow += IntOffset(amount.x.roundToInt(), amount.y.roundToInt())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { UIcon(Ui.Expand, Color.White, 14.sp) }
                    }
                }
            }
        }
    }
}

/** Corner and removal controls for the selected tile, shown in the edit bar. */
@Composable
fun TileControls(radius: Int, onRadius: (Int) -> Unit, onRemove: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Coins", color = c.onBackground, fontSize = 15.sp)
        Slider(
            radius.toFloat(), { onRadius(it.roundToInt()) }, Modifier.weight(1f), valueRange = 0f..60f,
            colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary)
        )
        Pill("Retirer", Modifier.width(110.dp), filled = false, onClick = onRemove)
        Pill("OK", Modifier.width(80.dp), filled = true, onClick = onDone)
    }
}
