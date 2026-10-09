package com.deon.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Handle { LEFT, RIGHT, PLATE, CORNER }

/**
 * Lets the user drag the two tail lights and the plate onto the car picture, resize the plate from its bottom-right
 * corner and pick the plate and text colours. Changes are saved as they happen.
 */
@Composable
fun CarEditor(customCar: ImageBitmap?, onClose: () -> Unit) {
    val s = LocalSettings.current
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        BoxWithConstraints(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            // The Clio picture is square with margins; an imported one is cropped to the car.
            val ratio = customCar?.let { it.height.toFloat() / it.width } ?: 1f
            val w = minOf(maxWidth, maxHeight / ratio)
            Box(Modifier.size(w, w * ratio)) {
                if (customCar != null) Image(customCar, "Voiture", Modifier.fillMaxSize())
                else Image(painterResource(R.drawable.clio), "Clio", Modifier.fillMaxSize())
                Handles(Modifier.fillMaxSize(), s.carLayout, s.plate, c.primary) { s.carLayout = it }
            }
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Feux et plaque", color = c.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(
                    "Fais glisser les deux ronds sur les feux arrière : ils s'allumeront en mode sombre. " +
                        "Déplace la plaque, et agrandis-la avec le carré de son coin.",
                    color = c.onSurfaceVariant, fontSize = 15.sp, lineHeight = 21.sp
                )
                Text("Couleur de la plaque", color = c.onSurface, fontSize = 16.sp)
                Swatches(PlateColors, s.carLayout.plateColor) { s.carLayout = s.carLayout.copy(plateColor = it) }
                Text("Couleur du texte", color = c.onSurface, fontSize = 16.sp)
                Swatches(PlateTextColors, s.carLayout.textColor) { s.carLayout = s.carLayout.copy(textColor = it) }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("Réinitialiser", Modifier.weight(1f), filled = false) { s.resetCarLayout() }
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

@Composable
private fun Handles(modifier: Modifier, layout: CarLayout, plate: String, accent: Color, onChange: (CarLayout) -> Unit) {
    val current by rememberUpdatedState(layout)
    var active by remember { mutableStateOf<Handle?>(null) }
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { at ->
                    val l = current
                    fun px(o: Offset) = Offset(o.x * size.width, o.y * size.height)
                    val corner = Offset(l.plate.right * size.width, l.plate.bottom * size.height)
                    val reach = 36.dp.toPx()
                    active = when {
                        (at - corner).getDistance() < reach -> Handle.CORNER
                        (at - px(l.left)).getDistance() < reach -> Handle.LEFT
                        (at - px(l.right)).getDistance() < reach -> Handle.RIGHT
                        Rect(px(l.plate.topLeft), corner).inflate(reach / 2).contains(at) -> Handle.PLATE
                        else -> null
                    }
                },
                onDragEnd = { active = null },
                onDragCancel = { active = null },
                onDrag = { change, drag ->
                    change.consume()
                    val d = Offset(drag.x / size.width, drag.y / size.height)
                    val l = current
                    fun Offset.clamp() = Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                    onChange(
                        when (active) {
                            Handle.LEFT -> l.copy(left = (l.left + d).clamp())
                            Handle.RIGHT -> l.copy(right = (l.right + d).clamp())
                            Handle.PLATE -> {
                                // Move the whole plate, kept inside the picture.
                                val dx = d.x.coerceIn(-l.plate.left, 1f - l.plate.right)
                                val dy = d.y.coerceIn(-l.plate.top, 1f - l.plate.bottom)
                                l.copy(plate = l.plate.translate(dx, dy))
                            }
                            Handle.CORNER -> l.copy(
                                plate = Rect(
                                    l.plate.left, l.plate.top,
                                    (l.plate.right + d.x).coerceIn(l.plate.left + .04f, 1f),
                                    (l.plate.bottom + d.y).coerceIn(l.plate.top + .015f, 1f)
                                )
                            )
                            null -> l
                        }
                    )
                }
            )
        }
    ) {
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
    }
}
