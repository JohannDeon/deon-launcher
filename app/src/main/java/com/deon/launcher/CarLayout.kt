package com.deon.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File

/**
 * A sticker on the car: its picture file id, centre (shares of the car picture), width (share of the picture's width),
 * height/width ratio of its image, and rotation in degrees.
 */
data class Sticker(val id: String, val center: Offset, val width: Float, val ratio: Float, val rotation: Float) {
    fun encode() = "$id:${center.x}:${center.y}:$width:$ratio:$rotation"

    companion object {
        fun decode(s: String) = runCatching {
            val p = s.split(':')
            Sticker(p[0], Offset(p[1].toFloat(), p[2].toFloat()), p[3].toFloat(), p[4].toFloat(), p[5].toFloat())
        }.getOrNull()
    }
}

/**
 * Where the tail lights and the plate sit on the car picture, as shares (0..1) of the picture's width and height,
 * the plate's colours and the stickers. Kept separately for the built-in Clio and for an imported car.
 */
data class CarLayout(
    val left: Offset,
    val right: Offset,
    val plate: Rect,
    val plateColor: Color,
    val textColor: Color,
    val stickers: List<Sticker> = emptyList(),
) {
    fun encode() = listOf(
        left.x, left.y, right.x, right.y, plate.left, plate.top, plate.right, plate.bottom
    ).joinToString(",") + ",${plateColor.toArgb()},${textColor.toArgb()}" +
        ";" + stickers.joinToString("/") { it.encode() }

    companion object {
        /** Measured on clio.png (369 px square): lenses at 20.5 % / 79.5 %, plate's white area 151..234 x 242..257 px. */
        val Clio = CarLayout(
            Offset(.205f, .515f), Offset(.795f, .515f),
            Rect(151f / 369, 242f / 369, 234f / 369, 257f / 369),
            Color(0xFFF4F5F6), Color(0xFF111111)
        )

        /** First guess for an imported car (cropped to its outline): lights high on the sides, plate low in the middle. */
        val Imported = CarLayout(
            Offset(.15f, .5f), Offset(.85f, .5f),
            Rect(.37f, .66f, .63f, .73f),
            Color(0xFFF4F5F6), Color(0xFF111111)
        )

        fun decode(s: String?, default: CarLayout): CarLayout = runCatching {
            val parts = s!!.split(';')
            val p = parts[0].split(',')
            val f = p.take(8).map { it.toFloat() }
            CarLayout(
                Offset(f[0], f[1]), Offset(f[2], f[3]), Rect(f[4], f[5], f[6], f[7]),
                Color(p[8].toInt()), Color(p[9].toInt()),
                parts.getOrNull(1).orEmpty().split('/').filter { it.isNotEmpty() }.mapNotNull(Sticker::decode)
            )
        }.getOrDefault(default)
    }
}

val PlateColors = listOf(Color(0xFFF4F5F6), Color(0xFFF2C230), Color(0xFFC9CDD3), Color(0xFF1A1A1A))
val PlateTextColors = listOf(Color(0xFF111111), Color(0xFFFFFFFF), Color(0xFF1F4BB5), Color(0xFFD93636))

private val platePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
}

/** Paints the plate and writes [text] on it; at [night] the plate dims like the rest of the car. */
fun DrawScope.drawPlate(layout: CarLayout, text: String, night: Boolean) {
    val r = Rect(
        layout.plate.left * size.width, layout.plate.top * size.height,
        layout.plate.right * size.width, layout.plate.bottom * size.height
    )
    val dim = { c: Color -> if (night) lerp(c, Color.Black, .22f) else c }
    drawRect(dim(layout.plateColor), r.topLeft, r.size)
    if (text.isBlank()) return
    platePaint.color = dim(layout.textColor).toArgb()
    platePaint.textSize = r.height * .8f
    // Shrink long plates so they stay inside the frame.
    val width = platePaint.measureText(text)
    if (width > r.width * .92f) platePaint.textSize *= r.width * .92f / width
    val fm = platePaint.fontMetrics
    drawContext.canvas.nativeCanvas.drawText(text, r.center.x, r.center.y - (fm.ascent + fm.descent) / 2, platePaint)
}

/** Stickers drawn over the car picture, each turned around its centre; [dim] darkens them at night. */
fun DrawScope.drawStickers(stickers: List<Sticker>, images: Map<String, ImageBitmap>, dim: Boolean) {
    for (s in stickers) {
        val img = images[s.id] ?: continue
        val w = s.width * size.width
        val h = w * s.ratio
        val c = Offset(s.center.x * size.width, s.center.y * size.height)
        rotate(s.rotation, c) {
            drawImage(
                img, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - h / 2).toInt()), dstSize = IntSize(w.toInt(), h.toInt()),
                colorFilter = if (dim) ColorFilter.tint(Color(0xFFA8AEB9), BlendMode.Modulate) else null
            )
        }
    }
}

/** Loads the sticker pictures saved in the app files (kept between updates). */
@Composable
fun rememberStickerImages(stickers: List<Sticker>): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val ids = stickers.map { it.id }.toSet()
    return remember(ids) {
        ids.mapNotNull { id ->
            runCatching { BitmapFactory.decodeFile(stickerFile(context, id).path)?.asImageBitmap() }.getOrNull()?.let { id to it }
        }.toMap()
    }
}

fun stickerFile(context: Context, id: String) = File(File(context.filesDir, "stickers").apply { mkdirs() }, "$id.png")

/** Red glow on both tail lights, used at night. */
fun DrawScope.drawTailGlow(layout: CarLayout) {
    val radius = size.width * .12f
    for (p in listOf(layout.left, layout.right)) {
        val c = Offset(p.x * size.width, p.y * size.height)
        drawCircle(Brush.radialGradient(listOf(Color(0x88FF2020), Color.Transparent), c, radius), radius, c)
    }
}
