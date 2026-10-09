package com.deon.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ponytail: how fast the light streaks slide along the horizon; one full sweep per second at this speed
private const val SWEEP_KMH = 60f

// clio.png is square; its tyres touch the ground at this height of the image.
private const val GROUND_Y = 0.87f

// Shares of the scene height: the horizon line runs just above the car roof, the car stands on the ground lower down.
private const val HORIZON = 0.34f
private const val GROUND = 0.90f

/**
 * Photo of a white Renault Clio 2 phase 2 (rear view) in front of a faded horizon line, drawn on a transparent
 * background so it blends with the interface. Light streaks along the horizon, bounce and shadow follow [speedKmh].
 */
@Composable
fun ClioScene(
    speedKmh: Float, night: Boolean, line: Color, accent: Color, plate: String, layout: CarLayout, motion: Boolean,
    customCar: ImageBitmap? = null, modifier: Modifier = Modifier
) {
    val speed by rememberUpdatedState(speedKmh)
    var phase by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) {
                val dt = (t - last) / 1e9f
                phase += dt * speed / SWEEP_KMH
                time += dt
            }
            last = t
        }
    }
    // 0 when parked, 1 from 130 km/h: drives how much the car shakes and how bright the streaks are.
    val shake = min(speed / 130f, 1f)
    val moving = speed > 2f
    val shaking = moving && motion
    val stickerImages = rememberStickerImages(layout.stickers)

    BoxWithConstraints(modifier.clipToBounds()) {
        val w = maxWidth
        val h = maxHeight
        val img = minOf(w * 0.5f, h * 0.72f)
        val bounce = if (shaking) (sin(time * 31f) * 0.6f + sin(time * 13f) * 0.4f) * (0.5f + 2f * shake) else 0f
        val sway = if (shaking) sin(time * 2.3f) * (1f + 2.5f * shake) else 0f
        val pulse = if (shaking) 0.5f + 0.5f * sin(time * 9f) else 0f
        val carY = h * GROUND - img * GROUND_Y + bounce.dp

        Canvas(Modifier.fillMaxSize()) {
            drawHorizon(size.height * HORIZON, line, accent, if (moving) phase else 0f, if (moving) 0.25f + 0.75f * shake else 0f)
            val y = size.height * GROUND
            // Soft contact shadow, a little darker when the car dips.
            val cw = img.toPx() * 0.82f
            val a = (if (night) 0.55f else 0.30f) + 0.08f * pulse * (0.4f + shake)
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = a), Color.Transparent), Offset(size.width / 2, y), cw * 0.6f),
                Offset(size.width / 2 - cw * 0.6f, y - cw * .05f), Size(cw * 1.2f, cw * .10f)
            )
        }
        val nightTint = if (night) ColorFilter.tint(Color(0xFFA8AEB9), BlendMode.Modulate) else null
        if (customCar != null) {
            // Imported picture, already cropped to the car: as wide as the Clio, standing on the ground line.
            val ratio = customCar.height.toFloat() / customCar.width
            val carW = minOf(img * 0.78f, h * 0.62f / ratio)
            val carH = carW * ratio
            val carModifier = Modifier.size(carW, carH).align(Alignment.TopCenter).offset(x = sway.dp, y = h * GROUND - carH + bounce.dp)
            Image(customCar, "Voiture", carModifier, colorFilter = nightTint)
            Canvas(carModifier) {
                drawStickers(layout.stickers, stickerImages, night)
                drawPlate(layout, plate, night)
                if (night) drawTailGlow(layout)
            }
            return@BoxWithConstraints
        }
        Image(
            painterResource(R.drawable.clio),
            "Clio",
            Modifier.size(img).align(Alignment.TopCenter).offset(x = sway.dp, y = carY),
            colorFilter = nightTint
        )
        Canvas(Modifier.size(img).align(Alignment.TopCenter).offset(x = sway.dp, y = carY)) {
            drawStickers(layout.stickers, stickerImages, night)
            drawPlate(layout, plate, night)
            if (night) drawTailGlow(layout)
        }
    }
}

/** A thin line that fades out at both ends, with a soft glow below it only, and light streaks. */
private fun DrawScope.drawHorizon(y: Float, line: Color, accent: Color, phase: Float, intensity: Float) {
    val w = size.width
    val half = w * 0.36f // the line covers the middle 72 % of the scene
    // Glow hangs under the line: a squashed radial gradient, clipped to the lower half.
    clipRect(top = y) {
        scale(1f, 0.15f, Offset(w / 2, y)) {
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = .30f), accent.copy(alpha = .08f), Color.Transparent), Offset(w / 2, y), half),
                half, Offset(w / 2, y)
            )
        }
    }
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, line.copy(alpha = .6f), Color.Transparent), w / 2 - half, w / 2 + half),
        Offset(w / 2 - half, y - 0.75.dp.toPx()), Size(half * 2, 1.5.dp.toPx())
    )

    if (intensity <= 0f) return
    // Streaks start behind the car and slide outward, speeding up like scenery going by.
    val n = 5
    for (i in 0 until n) {
        val u = (phase + i.toFloat() / n).let { it - floor(it) }
        val x = (0.15f + 0.8f * u.pow(1.6f)) * half
        val len = (10f + 60f * u) * density
        val a = sin(PI.toFloat() * u) * intensity
        for (side in floatArrayOf(-1f, 1f)) {
            val cx = w / 2 + side * x
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, line.copy(alpha = .9f * a), Color.Transparent), cx - len / 2, cx + len / 2
                ),
                Offset(cx - len / 2, y - 1.dp.toPx()), Size(len, 2.dp.toPx())
            )
        }
    }
}
