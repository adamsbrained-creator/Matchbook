package com.matchbook.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------- football geometry
private class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm(): V3 { val l = sqrt(dot(this)); return V3(x / l, y / l, z / l) }
}

private class Pent(val c: V3, val pts: List<V3>)

/** The 12 black pentagons of a truncated icosahedron (a real football), as points on the unit sphere. */
private val PENTS: List<Pent> by lazy {
    val p = (1f + sqrt(5f)) / 2f
    val raw = listOf(
        V3(0f, 1f, p), V3(0f, -1f, p), V3(0f, 1f, -p), V3(0f, -1f, -p), V3(1f, p, 0f), V3(-1f, p, 0f),
        V3(1f, -p, 0f), V3(-1f, -p, 0f), V3(p, 0f, 1f), V3(-p, 0f, 1f), V3(p, 0f, -1f), V3(-p, 0f, -1f),
    ).map { it.norm() }
    raw.map { v ->
        val nb = raw.filter { it !== v && v.dot(it) > 0.4f }            // the 5 neighbours (dot = 1/sqrt(5))
        val ref = if (abs(v.x) < 0.9f) V3(1f, 0f, 0f) else V3(0f, 1f, 0f)
        val e1 = v.cross(ref).norm()
        val e2 = v.cross(e1)
        val ring = nb.sortedBy { atan2(it.dot(e2), it.dot(e1)) }.map { w -> (v * (2f / 3f) + w * (1f / 3f)).norm() }
        // smooth the five edges by adding points between the corners
        val smooth = ArrayList<V3>()
        for (i in ring.indices) {
            val a = ring[i]; val b = ring[(i + 1) % ring.size]
            for (t in 0 until 4) smooth.add((a * (1f - t / 4f) + b * (t / 4f)).norm())
        }
        Pent(v, smooth)
    }
}

private fun rot(v: V3, yaw: Float, pitch: Float): V3 {
    val cy = cos(yaw); val sy = sin(yaw)
    val x1 = v.x * cy + v.z * sy
    val z1 = -v.x * sy + v.z * cy
    val cp = cos(pitch); val sp = sin(pitch)
    return V3(x1, v.y * cp - z1 * sp, v.y * sp + z1 * cp)
}

/** Draws a shaded football filling the canvas. [spinDeg] turns it around its vertical axis. */
private fun DrawScope.drawBall(spinDeg: Float, tiltDeg: Float = 24f) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val hi = Offset(c.x - r * .32f, c.y - r * .38f)
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Color(0xFFE9EFEC), Color(0xFFAAB6B1)), center = hi, radius = r * 1.55f),
        r, c,
    )
    val circle = Path().apply { addOval(Rect(c, r)) }
    clipPath(circle) {
        val yaw = Math.toRadians(spinDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(tiltDeg.toDouble()).toFloat()
        val ink = Color(0xFF131A17)
        for (pent in PENTS) {
            val rc = rot(pent.c, yaw, pitch)
            if (rc.z < -0.32f) continue
            val path = Path()
            pent.pts.forEachIndexed { i, v ->
                val q = rot(v, yaw, pitch)
                var px = q.x; var py = q.y
                if (q.z < 0f) { val l = sqrt(px * px + py * py).coerceAtLeast(0.0001f); px /= l; py /= l }   // hug the rim when behind it
                val o = Offset(c.x + px * r, c.y - py * r)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            drawPath(path, ink)
        }
        // form shadow towards the lower right, then a soft highlight
        drawCircle(
            Brush.radialGradient(0f to Color.Transparent, .5f to Color.Transparent, 1f to Color.Black.copy(alpha = .42f), center = hi, radius = r * 1.7f),
            r, c,
        )
        val sp = Offset(c.x - r * .34f, c.y - r * .40f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .85f), Color.Transparent), center = sp, radius = r * .3f), r * .3f, sp)
    }
}

/** The Matchbook ball as a small logo. */
@Composable
fun BallMark(modifier: Modifier = Modifier, spin: Float = 28f) {
    Canvas(modifier) { drawBall(spin) }
}

// ---------------------------------------------------------------- launch screen
@Composable
fun LaunchSplash(onDone: () -> Unit) {
    val spin = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { spin.animateTo(560f, tween(1800, easing = LinearOutSlowInEasing)) }
            launch { drop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { delay(450); word.animateTo(1f, tween(600)) }
        }
        delay(250)
        fade.animateTo(0f, tween(320))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().alpha(fade.value).background(Color(0xFF0B0C0F))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        // green glow behind the ball
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF00D964).copy(alpha = .38f), Color(0xFF00953F).copy(alpha = .12f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * .42f), radius = size.width * .95f,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp, 220.dp), contentAlignment = Alignment.TopCenter) {
                val h = abs(1f - drop.value).coerceAtMost(1f)      // height above the ground: the spring overshoot becomes a bounce
                // contact shadow grows as the ball lands
                Canvas(Modifier.align(Alignment.BottomCenter).size(130.dp, 26.dp)) {
                    val k = (1f - h).coerceIn(0f, 1f)
                    drawOval(
                        Brush.radialGradient(listOf(Color.Black.copy(alpha = .55f * k), Color.Transparent), center = Offset(size.width / 2f, size.height / 2f), radius = size.width / 2f),
                        topLeft = Offset(size.width * (.5f - .5f * k), size.height * (.5f - .5f * k)),
                        size = Size(size.width * k, size.height * k),
                    )
                }
                Canvas(Modifier.size(150.dp).offset(y = (-(h * 320f) + 8f).dp)) { drawBall(spin.value) }
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.alpha(word.value), horizontalAlignment = Alignment.CenterHorizontally) {
                Hd("Matchbook", 38f, Color.White, align = TextAlign.Center)
                Tx("Your football, organised", 14f, 600, Color.White.copy(alpha = .65f), Modifier.padding(top = 6.dp), align = TextAlign.Center)
            }
        }
    }
}
