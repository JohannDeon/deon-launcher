package com.deon.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * Where the tail lights and the plate sit on the car picture, as shares (0..1) of the picture's width and height,
 * and the plate's colours. Kept separately for the built-in Clio and for an imported car.
 */
data class CarLayout(
    val left: Offset,
    val right: Offset,
    val plate: Rect,
    val plateColor: Color,
    val textColor: Color,
) {
    fun encode() = listOf(
        left.x, left.y, right.x, right.y, plate.left, plate.top, plate.right, plate.bottom
    ).joinToString(",") + ",${plateColor.toArgb()},${textColor.toArgb()}"

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
            val p = s!!.split(',')
            val f = p.take(8).map { it.toFloat() }
            CarLayout(
                Offset(f[0], f[1]), Offset(f[2], f[3]), Rect(f[4], f[5], f[6], f[7]),
                Color(p[8].toInt()), Color(p[9].toInt())
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

/** Red glow on both tail lights, used at night. */
fun DrawScope.drawTailGlow(layout: CarLayout) {
    val radius = size.width * .12f
    for (p in listOf(layout.left, layout.right)) {
        val c = Offset(p.x * size.width, p.y * size.height)
        drawCircle(Brush.radialGradient(listOf(Color(0x88FF2020), Color.Transparent), c, radius), radius, c)
    }
}
