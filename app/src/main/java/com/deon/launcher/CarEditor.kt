package com.deon.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private sealed interface Handle {
    data object Left : Handle
    data object Right : Handle
    data object Plate : Handle
    data object Corner : Handle
    data class Move(val index: Int) : Handle
    data class Turn(val index: Int) : Handle
}

/**
 * Lets the user drag the two tail lights and the plate onto the car picture, resize the plate from its bottom-right
 * corner, pick the plate and text colours, and add stickers that move, resize and turn with one finger.
 * Changes are saved as they happen.
 */
@Composable
fun CarEditor(customCar: ImageBitmap?, onAddSticker: () -> Unit, onClose: () -> Unit) {
    val s = LocalSettings.current
    val c = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf<Int?>(null) }
    // A sticker that was just added is selected, ready to be placed.
    val count = s.carLayout.stickers.size
    var lastCount by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > lastCount) selected = count - 1
        if (selected != null && selected!! >= count) selected = null
        lastCount = count
    }
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        BoxWithConstraints(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            // The Clio picture is square with margins; an imported one is cropped to the car.
            val ratio = customCar?.let { it.height.toFloat() / it.width } ?: 1f
            val w = minOf(maxWidth, maxHeight / ratio)
            Box(Modifier.size(w, w * ratio)) {
                if (customCar != null) Image(customCar, "Voiture", Modifier.fillMaxSize())
                else Image(painterResource(R.drawable.clio), "Clio", Modifier.fillMaxSize())
                Handles(Modifier.fillMaxSize(), s.carLayout, s.plate, c.primary, selected, { selected = it }) { s.carLayout = it }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Feux, plaque et stickers", color = c.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(
                    "Fais glisser les deux ronds sur les feux arrière : ils s'allumeront en mode sombre. " +
                        "Déplace la plaque, et agrandis-la avec le carré de son coin.",
                    color = c.onSurfaceVariant, fontSize = 15.sp, lineHeight = 21.sp
                )
                Text("Couleur de la plaque", color = c.onSurface, fontSize = 16.sp)
                Swatches(PlateColors, s.carLayout.plateColor) { s.carLayout = s.carLayout.copy(plateColor = it) }
                Text("Couleur du texte", color = c.onSurface, fontSize = 16.sp)
                Swatches(PlateTextColors, s.carLayout.textColor) { s.carLayout = s.carLayout.copy(textColor = it) }
                Text("Stickers", color = c.onSurface, fontSize = 16.sp)
                Text(
                    "Importe un PNG à fond transparent. Touche un sticker pour le choisir, fais-le glisser pour le déplacer, " +
                        "et tire son rond pour l'agrandir et le tourner.",
                    color = c.onSurfaceVariant, fontSize = 15.sp, lineHeight = 21.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("Ajouter un sticker", Modifier.weight(1.3f), filled = false, icon = { UIcon(Ui.Plus, it, 16.sp) }, onClick = onAddSticker)
                    val sel = selected
                    if (sel != null) Pill("Supprimer", Modifier.weight(1f), filled = false) {
                        s.carLayout = s.carLayout.copy(stickers = s.carLayout.stickers.filterIndexed { i, _ -> i != sel })
                        selected = null
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("Réinitialiser", Modifier.weight(1f), filled = false) { s.resetCarLayout(); selected = null }
                    Pill("Terminé", Modifier.weight(1f), filled = true, onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun Swatches(colors: List<Color>, selected: Color, onPick: (Color) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { color ->
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(color)
                    .border(if (color == selected) 3.dp else 1.dp, if (color == selected) c.primary else c.outlineVariant, CircleShape)
                    .clickable { onPick(color) }
            )
        }
    }
}

/** Corner of a sticker that carries its resize-and-turn handle (bottom right, turned with the sticker). */
private fun turnHandle(st: Sticker, w: Float, h: Float): Offset {
    val c = Offset(st.center.x * w, st.center.y * h)
    val hx = st.width * w / 2
    val hy = hx * st.ratio
    val a = Math.toRadians(st.rotation.toDouble())
    return c + Offset((hx * cos(a) - hy * sin(a)).toFloat(), (hx * sin(a) + hy * cos(a)).toFloat())
}

/** True when [p] falls inside the turned rectangle of the sticker. */
private fun hits(st: Sticker, p: Offset, w: Float, h: Float): Boolean {
    val d = p - Offset(st.center.x * w, st.center.y * h)
    val a = Math.toRadians(-st.rotation.toDouble())
    val x = d.x * cos(a) - d.y * sin(a)
    val y = d.x * sin(a) + d.y * cos(a)
    val hx = st.width * w / 2
    return kotlin.math.abs(x) <= hx && kotlin.math.abs(y) <= hx * st.ratio
}

@Composable
private fun Handles(
    modifier: Modifier, layout: CarLayout, plate: String, accent: Color,
    selected: Int?, onSelect: (Int?) -> Unit, onChange: (CarLayout) -> Unit,
) {
    val current by rememberUpdatedState(layout)
    val currentSelected by rememberUpdatedState(selected)
    var active by remember { mutableStateOf<Handle?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    val images = rememberStickerImages(layout.stickers)
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { at ->
                    val l = current
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    onSelect(l.stickers.indices.lastOrNull { hits(l.stickers[it], at, w, h) })
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { at ->
                        val l = current
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        fun px(o: Offset) = Offset(o.x * w, o.y * h)
                        val corner = Offset(l.plate.right * w, l.plate.bottom * h)
                        val reach = 36.dp.toPx()
                        val sel = currentSelected?.takeIf { it < l.stickers.size }
                        finger = at
                        active = when {
                            sel != null && (at - turnHandle(l.stickers[sel], w, h)).getDistance() < reach -> Handle.Turn(sel)
                            (at - corner).getDistance() < reach -> Handle.Corner
                            (at - px(l.left)).getDistance() < reach -> Handle.Left
                            (at - px(l.right)).getDistance() < reach -> Handle.Right
                            Rect(px(l.plate.topLeft), corner).inflate(reach / 2).contains(at) -> Handle.Plate
                            else -> l.stickers.indices.lastOrNull { hits(l.stickers[it], at, w, h) }?.let { onSelect(it); Handle.Move(it) }
                        }
                    },
                    onDragEnd = { active = null },
                    onDragCancel = { active = null },
                    onDrag = { change, drag ->
                        change.consume()
                        finger += drag
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        val d = Offset(drag.x / w, drag.y / h)
                        val l = current
                        fun Offset.clamp() = Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                        fun withSticker(i: Int, f: (Sticker) -> Sticker) =
                            l.copy(stickers = l.stickers.mapIndexed { j, st -> if (j == i) f(st) else st })
                        onChange(
                            when (val a = active) {
                                Handle.Left -> l.copy(left = (l.left + d).clamp())
                                Handle.Right -> l.copy(right = (l.right + d).clamp())
                                Handle.Plate -> {
                                    // Move the whole plate, kept inside the picture.
                                    val dx = d.x.coerceIn(-l.plate.left, 1f - l.plate.right)
                                    val dy = d.y.coerceIn(-l.plate.top, 1f - l.plate.bottom)
                                    l.copy(plate = l.plate.translate(dx, dy))
                                }
                                Handle.Corner -> l.copy(
                                    plate = Rect(
                                        l.plate.left, l.plate.top,
                                        (l.plate.right + d.x).coerceIn(l.plate.left + .04f, 1f),
                                        (l.plate.bottom + d.y).coerceIn(l.plate.top + .015f, 1f)
                                    )
                                )
                                is Handle.Move -> withSticker(a.index) { it.copy(center = (it.center + d).clamp()) }
                                is Handle.Turn -> withSticker(a.index) { st ->
                                    // The finger sets the corner: its distance gives the size, its angle the rotation.
                                    val v = finger - Offset(st.center.x * w, st.center.y * h)
                                    val diag = atan(st.ratio.toDouble())
                                    val halfW = v.getDistance() * cos(diag)
                                    st.copy(
                                        width = (2 * halfW / w).toFloat().coerceIn(.04f, 1.5f),
                                        rotation = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble()) - diag).toFloat()
                                    )
                                }
                                null -> l
                            }
                        )
                    }
                )
            }
    ) {
        drawStickers(layout.stickers, images, dim = false)
        drawPlate(layout, plate, night = false)
        drawTailGlow(layout)
        val st = Stroke(2.dp.toPx())
        val dash = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        for (p in listOf(layout.left, layout.right)) {
            val o = Offset(p.x * size.width, p.y * size.height)
            drawCircle(Color.White, 18.dp.toPx(), o, style = Stroke(4.dp.toPx()))
            drawCircle(accent, 18.dp.toPx(), o, style = st)
        }
        val r = Rect(
            layout.plate.left * size.width, layout.plate.top * size.height,
            layout.plate.right * size.width, layout.plate.bottom * size.height
        )
        drawRect(accent, r.topLeft, r.size, style = dash)
        val k = 14.dp.toPx()
        drawRect(Color.White, Offset(r.right - k / 2, r.bottom - k / 2), androidx.compose.ui.geometry.Size(k, k))
        drawRect(accent, Offset(r.right - k / 2, r.bottom - k / 2), androidx.compose.ui.geometry.Size(k, k), style = st)
        // Frame and turn handle of the selected sticker.
        val sel = selected?.takeIf { it < layout.stickers.size } ?: return@Canvas
        val s = layout.stickers[sel]
        val c = Offset(s.center.x * size.width, s.center.y * size.height)
        val sw = s.width * size.width
        val sh = sw * s.ratio
        rotate(s.rotation, c) {
            drawRect(accent, Offset(c.x - sw / 2, c.y - sh / 2), androidx.compose.ui.geometry.Size(sw, sh), style = dash)
        }
        val t = turnHandle(s, size.width, size.height)
        drawCircle(Color.White, 15.dp.toPx(), t)
        drawCircle(accent, 15.dp.toPx(), t, style = Stroke(3.dp.toPx()))
        drawCircle(accent, 4.dp.toPx(), t)
    }
}
