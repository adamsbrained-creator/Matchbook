package com.matchbook.app

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Brand green of the app icon. */
val BrandGreen = Color(0xFF17B35A)

/** Draws the Matchbook pitch icon (white pitch lines, 108 x 108 grid) into the current canvas. */
private fun DrawScope.drawPitchLines(color: Color) {
    val k = size.minDimension / 108f
    withTransform({ scale(k, k, Offset.Zero) }) {
        val st = Stroke(3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(36f, 26f, 72f, 82f, CornerRadius(4f, 4f)))
            moveTo(36f, 54f); lineTo(72f, 54f)
            moveTo(45.5f, 26f); lineTo(45.5f, 33.5f); lineTo(62.5f, 33.5f); lineTo(62.5f, 26f)
            moveTo(45.5f, 82f); lineTo(45.5f, 74.5f); lineTo(62.5f, 74.5f); lineTo(62.5f, 82f)
        }
        drawPath(p, color, style = st)
        drawCircle(color, 6.5f, Offset(54f, 54f), style = st)
    }
}

/** The Matchbook logo: green rounded square with the pitch. */
@Composable
fun PitchMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRoundRect(BrandGreen, size = Size(size.width, size.height), cornerRadius = CornerRadius(size.minDimension * .24f))
        drawPitchLines(Color.White)
    }
}

// ---------------------------------------------------------------- launch screen
@Composable
fun LaunchSplash(onDone: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch { delay(380); word.animateTo(1f, tween(520)) }
        }
        delay(520)
        fade.animateTo(0f, tween(320))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().alpha(fade.value).background(Color(0xFF0B0C0F))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(BrandGreen.copy(alpha = .30f), BrandGreen.copy(alpha = .08f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * .42f), radius = size.width * .9f,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PitchMark(Modifier.size(132.dp).scale(.55f + .45f * pop.value).alpha(pop.value.coerceIn(0f, 1f)))
            Spacer(Modifier.height(22.dp))
            Column(Modifier.alpha(word.value), horizontalAlignment = Alignment.CenterHorizontally) {
                Hd("Matchbook", 38f, Color.White, align = TextAlign.Center)
                Tx("Your football, organised", 14f, 600, Color.White.copy(alpha = .65f), Modifier.padding(top = 6.dp), align = TextAlign.Center)
            }
        }
    }
}
