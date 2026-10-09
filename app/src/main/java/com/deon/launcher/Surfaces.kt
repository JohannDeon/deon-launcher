package com.deon.launcher

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Soft-UI relief: the shape is filled with the background colour and lit from the top left, a light shadow on that
 * side and a dark one on the other. [inset] gives the pressed-in look used for selected options.
 */
fun Modifier.neu(radius: Dp, dark: Boolean, elevation: Dp = 7.dp, inset: Boolean = false): Modifier = drawBehind {
    val bg = if (dark) NeuPalette.darkBg else NeuPalette.lightBg
    val hi = if (dark) NeuPalette.darkHi else NeuPalette.lightHi
    val lo = if (dark) NeuPalette.darkLo else NeuPalette.lightLo
    val r = radius.toPx()
    val e = elevation.toPx()
    if (inset) {
        // Pressed in: a darker rim on the top left fading to a lighter one on the bottom right.
        drawRoundRect(
            Brush.linearGradient(listOf(lo.copy(alpha = .55f), hi)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
        )
        drawRoundRect(
            bg, topLeft = androidx.compose.ui.geometry.Offset(e * .35f, e * .35f),
            size = androidx.compose.ui.geometry.Size(size.width - e * .7f, size.height - e * .7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r - e * .35f)
        )
        return@drawBehind
    }
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg.toArgb() }
        for ((dx, c) in listOf(-e / 2 to hi, e / 2 to lo)) {
            paint.setShadowLayer(e, dx, dx, c.copy(alpha = if (c == hi) .9f else .7f).toArgb())
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }
}

/** Large panel: frosted glass or raised soft-UI, depending on the chosen style. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (LocalSettings.current.style == Style.GLASS) Glass(modifier, content = content)
    else Box(modifier.neu(28.dp, LocalDark.current, 10.dp).clip(RoundedCornerShape(28.dp)), content = content)
}

/** Small element background (icon tiles, round buttons) in the current style. */
@Composable
fun Modifier.tile(radius: Dp, selected: Boolean = false): Modifier {
    val dark = LocalDark.current
    val shape = RoundedCornerShape(radius)
    return if (LocalSettings.current.style == Style.NEU) this.neu(radius, dark, if (selected) 4.dp else 5.dp, inset = selected).clip(shape)
    else this.clip(shape)
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else glassTint(dark))
        .border(1.dp, glassEdge(dark), shape)
}

fun glassTint(dark: Boolean) = if (dark) Color.White.copy(alpha = .07f) else Color.White.copy(alpha = .6f)

fun glassEdge(dark: Boolean) = Brush.linearGradient(
    if (dark) listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .05f))
    else listOf(Color.White, Color.White.copy(alpha = .4f))
)

@Composable
fun Pill(
    text: String, modifier: Modifier, filled: Boolean,
    icon: (@Composable (Color) -> Unit)? = null, onClick: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    val neu = LocalSettings.current.style == Style.NEU
    val fg = if (filled && !neu) c.onPrimary else if (filled) c.primary else c.onSurface
    val shape = RoundedCornerShape(28.dp)
    val base = modifier.height(56.dp)
    Row(
        (if (filled && !neu) base.clip(shape).background(c.primary).border(1.dp, SolidColor(Color.Transparent), shape)
        else base.tile(28.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let { it(fg); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
