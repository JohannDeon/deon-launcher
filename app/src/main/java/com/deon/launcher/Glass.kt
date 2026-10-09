package com.deon.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** Size of the whole screen, so every glass panel can redraw the backdrop exactly behind itself. */
val LocalRootSize = compositionLocalOf { IntSize.Zero }

/** The launcher background: its plain colour with a few large, soft colour glows for the glass to frost. */
@Composable
fun Backdrop(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val dark = LocalDark.current
    val k = if (dark) 1f else 0.7f
    // Soft UI is flat: no colour glows behind it.
    val glows = LocalSettings.current.style == Style.GLASS
    Canvas(modifier.background(c.background)) {
        if (!glows) return@Canvas
        val w = size.width
        val h = size.height
        fun glow(color: Color, alpha: Float, x: Float, y: Float, r: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha * k), Color.Transparent), Offset(w * x, h * y), w * r),
            w * r, Offset(w * x, h * y)
        )
        glow(c.primary, .38f, .80f, .30f, .32f)                 // blue, behind the clock and weather
        glow(Color(0xFF8A5CFF), .26f, .95f, .85f, .28f)         // violet, bottom right
        glow(Color(0xFF2EC5CE), .20f, .08f, .98f, .26f)         // teal, behind the app row
        glow(c.primary, .12f, .30f, .40f, .30f)                 // faint blue behind the car
    }
}

/** Frosted glass panel: the backdrop behind it, blurred, under a translucent tint and a light edge. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    blur: Dp = 28.dp,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val dark = LocalDark.current
    val root = LocalRootSize.current
    var pos by remember { mutableStateOf(IntOffset.Zero) }
    val tint = if (dark) Color.White.copy(alpha = .06f) else Color.White.copy(alpha = .55f)
    val edge = if (dark) listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .04f))
    else listOf(Color.White.copy(alpha = .95f), Color.White.copy(alpha = .35f))
    Box(
        modifier
            .onGloballyPositioned { val p = it.positionInRoot(); pos = IntOffset(p.x.toInt(), p.y.toInt()) }
            .clip(shape)
    ) {
        if (root != IntSize.Zero) Box(Modifier.matchParentSize()) {
            val d = LocalDensity.current
            Backdrop(
                Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset { IntOffset(-pos.x, -pos.y) }
                    .requiredSize(with(d) { root.width.toDp() }, with(d) { root.height.toDp() })
                    .blur(blur)
            )
        }
        Box(Modifier.matchParentSize().background(tint).border(1.dp, Brush.linearGradient(edge), shape))
        content()
    }
}
