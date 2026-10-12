package com.matchbook.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ================================================================ colour helpers
val InkDark = Color(0xFF16161A)
val Neutral = Color(0xFF3B3B44)

fun hex(s: String): Color = runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrDefault(Color.Gray)
fun Color.toHex(): String = String.format("#%06x", toArgb() and 0xFFFFFF)
fun lum(c: Color) = (c.red * 255 * 299 + c.green * 255 * 587 + c.blue * 255 * 114) / 1000
fun inkOn(c: Color) = if (lum(c) > 150) InkDark else Color.White
fun gray(c: Color): Color { val v = c.red * .299f + c.green * .587f + c.blue * .114f; return Color(v, v, v, c.alpha) }

/** Keeps dark colours, darkens light ones so header text stays readable. */
fun pageC(s: String?): Color {
    val c = runCatching { Color(android.graphics.Color.parseColor(s ?: "")) }.getOrNull() ?: Neutral
    return if (lum(c) < 140) c else Color(c.red * .62f, c.green * .62f, c.blue * .62f)
}

fun parseHex(s: String): Color? {
    var t = s.trim().removePrefix("#")
    if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
    if (t.length != 6 || !t.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$t")) }.getOrNull()
}

// ================================================================ tokens
class U(
    val s: Settings, val dark: Boolean,
    manrope: FontFamily, anton: FontFamily, inter: FontFamily, nunito: FontFamily, outfit: FontFamily,
) {
    /** Coral look (2.5): flat white cards on a soft grey page, black primary actions, coral highlights. */
    val coral = s.ui == "coral"
    // glass: near-black with glass (dark) or cool tinted background with frosted white glass (light)
    val bg = if (coral) (if (dark) Color(0xFF0A0A0A) else Color(0xFFF3F3F3)) else (if (dark) Color(0xFF0B0C0F) else Color(0xFFE9EDF5))
    val ink = if (coral) (if (dark) Color(0xFFF4F4F4) else Color(0xFF1B1B1B)) else (if (dark) Color(0xFFF4F6F8) else Color(0xFF12151B))
    val mu = if (coral) (if (dark) Color(0xFFA3A3A3) else Color(0xFF6B6B6B)) else (if (dark) Color(0xFFB4BCC6) else Color(0xFF566072))
    val ln = if (coral) (if (dark) Color.White.copy(alpha = .07f) else Color.Black.copy(alpha = .06f))
        else (if (dark) Color.White.copy(alpha = .08f) else Color(0xFF0F172A).copy(alpha = .08f))
    val sf = if (coral) (if (dark) Color(0xFF161616) else Color.White) else (if (dark) Color(0xFF181A20) else Color.White)
    /** Track behind segmented controls (Coral). */
    val track = if (dark) Color(0xFF1F1F1F) else Color(0xFFECECEE)
    val glassFill = if (dark) Color(0xFF1A1C22).copy(alpha = .72f) else Color.White.copy(alpha = .70f)
    val glassSheen = if (dark) Color.White.copy(alpha = .05f) else Color.White.copy(alpha = .55f)
    val glassTop = if (dark) Color.White.copy(alpha = .15f) else Color.White.copy(alpha = .95f)
    val glassEdge = if (dark) Color.White.copy(alpha = .08f) else Color(0xFF0F172A).copy(alpha = .07f)
    val outline = if (dark) Color.White.copy(alpha = .22f) else Color(0xFF0F172A).copy(alpha = .22f)
    val shadowC = Color(0xFF1E293B).copy(alpha = .16f)
    val navFill = if (dark) Color(0xFF16181D).copy(alpha = .88f) else Color.White.copy(alpha = .86f)
    val fieldFill = if (coral) (if (dark) Color.White.copy(alpha = .06f) else Color(0xFFF3F3F3)) else (if (dark) Color.White.copy(alpha = .06f) else Color.White.copy(alpha = .78f))
    val red = Color(0xFFFF5A5F)
    val acc: Color = hex(s.acc).let { a ->
        if (!dark && lum(a) > 190) {
            val sat = maxOf(a.red, a.green, a.blue) - minOf(a.red, a.green, a.blue)
            if (sat < .12f) InkDark else lerp(a, Color.Black, .26f)
        } else a
    }
    val on = if (lum(acc) > 150) InkDark else Color.White
    /** Fill and text colour of primary buttons, selected chips and switches: accent in Glass, black/white in Coral. */
    val pri: Color = if (coral) ink else acc
    val onPri: Color = if (coral) bg else on
    val r = s.r.dp
    val ring = s.ring.dp
    val fs = when (s.fs) { "s" -> .92f; "l" -> 1.12f; else -> 1f }
    val pad = if (s.d == "compact") 8.dp else 14.dp
    val cond = s.f == "condensed"
    val fb: FontFamily = when (s.f) {
        "condensed" -> inter; "rounded" -> nunito; "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace
        "outfit" -> outfit; else -> manrope
    }
    val fd: FontFamily = when (s.f) {
        "condensed" -> anton; "rounded" -> nunito; "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace
        "outfit" -> outfit; else -> manrope
    }
    /** Coral uses lighter weights than Glass. */
    fun wt(w: Int): Int = if (!coral) w else when { w >= 800 -> 600; w >= 600 -> 500; else -> w }
    val shape = RoundedCornerShape(r)
    fun rs(d: Int) = RoundedCornerShape(maxOf(0.dp, r - d.dp))

    /** Header glow gradient stops (top to bottom) for a page colour. */
    fun glow(c: Color): List<Color> {
        val st = listOf(lerp(c, Color.White, .34f), c, lerp(bg, c, .38f), bg)
        return when (s.g) { "soft" -> st.map { it.copy(alpha = .55f) }; "mono" -> st.map { gray(it) }; else -> st }
    }

    /** Decide a light or dark header foreground from the colour in the middle of the glow. */
    fun hdrFor(c: Color): Hdr {
        if (coral) return Hdr(dark, if (dark) Color(0xFFF4F4F4) else Color(0xFF1B1B1B), sf, Color.Transparent)
        val g = glow(c)
        var m = lerp(g[1], g[2], .66f)
        if (s.g == "soft") m = m.compositeOver(bg)
        return Hdr(lum(m) <= 150)
    }

    /** Page background below the glow. Light theme gets a soft tint of the page colour instead of flat grey. */
    fun pageBg(c: Color): Brush =
        if (dark) SolidColor(bg) else Brush.verticalGradient(listOf(bg, lerp(bg, c, .09f), lerp(bg, c, .16f)))

    fun glowHeight(big: Boolean): Dp {
        val k = when (s.gh) { "s" -> .78f; "l" -> 1.22f; else -> 1f }
        return ((if (big) 520 else 360) * k).dp
    }
}

class Hdr(
    val light: Boolean,
    val fg: Color = if (light) Color.White else InkDark,
    val chip: Color = if (light) Color.White.copy(alpha = .16f) else Color.Black.copy(alpha = .08f),
    val chipEdge: Color = if (light) Color.White.copy(alpha = .22f) else Color.Black.copy(alpha = .10f),
)

val LocalU = staticCompositionLocalOf<U> { error("U not provided") }
val LocalHdr = compositionLocalOf { Hdr(true) }

// ================================================================ text
@Composable
fun Tx(
    t: String, size: Float, w: Int = 400, c: Color = Color.Unspecified, modifier: Modifier = Modifier,
    align: TextAlign? = null, lh: Float = 0f, maxLines: Int = Int.MAX_VALUE,
) {
    val u = LocalU.current
    Text(
        t, modifier, color = if (c == Color.Unspecified) u.ink else c, fontSize = size.sp, fontWeight = FontWeight(u.wt(w)),
        fontFamily = u.fb, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        lineHeight = if (lh > 0f) lh.sp else TextUnit.Unspecified,
    )
}

/** Display text (titles, big numbers). [w] = -1 picks the heading weight of the chosen typeface. */
@Composable
fun Hd(t: String, size: Float, c: Color = Color.Unspecified, modifier: Modifier = Modifier, align: TextAlign? = null, w: Int = -1, maxLines: Int = Int.MAX_VALUE) {
    val u = LocalU.current
    val weight = if (w > 0) u.wt(w) else if (u.cond) 400 else if (u.coral) 500 else 800
    Text(
        t, modifier, color = if (c == Color.Unspecified) u.ink else c, fontSize = size.sp, fontWeight = FontWeight(weight),
        fontFamily = u.fd, textAlign = align, lineHeight = (size * 1.12f).sp,
        letterSpacing = if (u.cond) 0.01.em else (-0.01).em, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

// ================================================================ icons
// Icon set (24 x 24 grid, drawn as round-capped strokes). New in 2.5: pitch, users, route, menu, grid, list, more, chevrons, swap, trash.
private val ICO = mapOf(
    "shield" to "M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z",
    "brush" to "M12 3C7 3 3 7 3 12s4 9 9 9c.9 0 1.5-.7 1.5-1.5 0-.4-.2-.8-.4-1-.3-.3-.4-.6-.4-1 0-.8.7-1.5 1.5-1.5H17c2.8 0 5-2.2 5-5 0-4.4-4.5-8-10-8zM7.5 11h.01M10 7.5h.01M14.5 7.5h.01M17 11h.01",
    "shape" to "M4 4h10v10H4zM20 16a4 4 0 1 1 -8 0 4 4 0 0 1 8 0z",
    "type" to "M5 7V4h14v3M12 4v16M9 20h6",
    "compass" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM15.5 8.5l-2 5-5 2 2-5z",
    "db" to "M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3zM4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6",
    "info" to "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v5M12 8h.01",
    "trophy" to "M8 4h8v5a4 4 0 0 1-8 0zM8 6H5v1a3 3 0 0 0 3 3M16 6h3v1a3 3 0 0 1-3 3M12 13v4M8 20h8",
    "plus" to "M12 5v14M5 12h14",
    "bars" to "M5 20v-5M10 20V8M15 20V4M20 20v-9",
    "search" to "M16 16l4.5 4.5",
    "close" to "M6 6l12 12M18 6L6 18",
    "back" to "M15 5l-7 7 7 7",
    "chev" to "M9 5l7 7-7 7",
    "chevd" to "M6 9l6 6 6-6",
    "chevu" to "M6 15l6-6 6 6",
    "edit" to "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4",
    "compare" to "M7 4v16M17 4v16M3 8h8M13 16h8",
    "cal" to "M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-9a3 3 0 0 1 3 -3zM4 10h16M9 3v4M15 3v4",
    "up" to "M12 19V6M6 12l6-6 6 6",
    "down" to "M12 5v13M6 12l6 6 6-6",
    "save" to "M12 4v11M7 10l5 5 5-5M5 20h14",
    "load" to "M12 16V5M7 10l5-5 5 5M5 20h14",
    "check" to "M5 12l5 5 9-10",
    "menu" to "M4 7h16M4 12h16M4 17h10",
    "more" to "M6 12h.01M12 12h.01M18 12h.01",
    "grid" to "M5 5h5v5h-5zM14 5h5v5h-5zM5 14h5v5h-5zM14 14h5v5h-5z",
    "list" to "M4 7h16M4 12h16M4 17h16",
    "pitch" to "M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2zM4 12h16M9 3v3h6v-3M9 21v-3h6v3M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0 -5z",
    "users" to "M9 11a3 3 0 1 0 0 -6a3 3 0 0 0 0 6zM3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M16 5.2a3 3 0 0 1 0 5.6M17 14.2c2.4.6 4 2.8 4 5.8",
    "route" to "M5 18c0-4 4-4 7-6s3-6 7-6M15 6h4v4",
    "swap" to "M7 4v14M7 18l-3-3M7 18l3-3M17 20V6M17 6l-3 3M17 6l3 3",
    "trash" to "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13",
    "gear" to "M19.4 15 a1.65 1.65 0 0 0 .33 1.82 l.06 .06 a2 2 0 1 1 -2.83 2.83 l-.06 -.06 a1.65 1.65 0 0 0 -1.82 -.33 " +
        "a1.65 1.65 0 0 0 -1 1.51 V21 a2 2 0 1 1 -4 0 v-.09 A1.65 1.65 0 0 0 9 19.4 a1.65 1.65 0 0 0 -1.82 .33 l-.06 .06 " +
        "a2 2 0 1 1 -2.83 -2.83 l.06 -.06 a1.65 1.65 0 0 0 .33 -1.82 a1.65 1.65 0 0 0 -1.51 -1 H3 a2 2 0 1 1 0 -4 h.09 " +
        "A1.65 1.65 0 0 0 4.6 9 a1.65 1.65 0 0 0 -.33 -1.82 l-.06 -.06 a2 2 0 1 1 2.83 -2.83 l.06 .06 a1.65 1.65 0 0 0 1.82 .33 " +
        "H9 a1.65 1.65 0 0 0 1 -1.51 V3 a2 2 0 1 1 4 0 v.09 a1.65 1.65 0 0 0 1 1.51 a1.65 1.65 0 0 0 1.82 -.33 l.06 -.06 " +
        "a2 2 0 1 1 2.83 2.83 l-.06 .06 a1.65 1.65 0 0 0 -.33 1.82 V9 a1.65 1.65 0 0 0 1.51 1 H21 a2 2 0 1 1 0 4 h-.09 " +
        "a1.65 1.65 0 0 0 -1.51 1 z",
)

@Composable
fun Ico(name: String, size: Dp, color: Color, sw: Float = 1.7f) {
    val path = remember(name) { PathParser().parsePathString(ICO[name] ?: ICO["plus"]!!).toPath() }
    Canvas(Modifier.size(size)) {
        val sc = this.size.width / 24f
        withTransform({ scale(sc, sc, Offset.Zero) }) {
            if (name == "gear") drawCircle(color, 3f, Offset(12f, 12f), style = Stroke(sw))
            if (name == "search") drawCircle(color, 6.5f, Offset(11f, 11f), style = Stroke(sw))
            drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ================================================================ images
object ImageStore {
    /** Decode a picked image, downsampled so the longest side is at most [maxSide] px. */
    fun decode(ctx: Context, uri: Uri, maxSide: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    fun save(ctx: Context, bmp: Bitmap): String {
        val f = File(ctx.filesDir, "img_${System.nanoTime()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }
}

@Composable
fun rememberBitmap(path: String): ImageBitmap? {
    if (path.isBlank()) return null
    val st = produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    }
    return st.value
}

/** Like rememberBitmap, but tells "still loading" (null) apart from "can't be read" (false). */
@Composable
fun rememberBitmapOrFail(path: String): Any? {
    val st = produceState<Any?>(if (path.isBlank()) false else null, path) {
        value = if (path.isBlank()) false
        else withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() ?: false }
    }
    return st.value
}

// ================================================================ crest
private val ShieldShape = GenericShape { s, _ ->
    moveTo(0f, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height * .62f); lineTo(s.width / 2, s.height); lineTo(0f, s.height * .62f); close()
}
private val HexShape = GenericShape { s, _ ->
    moveTo(s.width * .25f, s.height * .06f); lineTo(s.width * .75f, s.height * .06f); lineTo(s.width, s.height * .5f)
    lineTo(s.width * .75f, s.height * .94f); lineTo(s.width * .25f, s.height * .94f); lineTo(0f, s.height * .5f); close()
}

@Composable
fun Crest(color: String, c2: String, logo: String, text: String, size: Dp = 44.dp, xl: Boolean = false, backdrop: Boolean = false) {
    val u = LocalU.current
    val bmp = rememberBitmap(logo)
    // Uploaded logo without backdrop: show it as-is, so transparent PNGs stay transparent
    if (logo.isNotBlank() && !backdrop) {
        Box(Modifier.size(size)) { if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        return
    }
    val main = hex(color)
    val edge = if (c2.isNotBlank()) hex(c2) else lerp(Color.Black, main, .62f)
    val shape: androidx.compose.ui.graphics.Shape = when (u.s.c) {
        "square" -> RoundedCornerShape(16); "shield" -> ShieldShape; "hex" -> HexShape; else -> CircleShape
    }
    Box(Modifier.size(size).clip(shape).background(edge).padding(if (xl) u.ring else u.ring * .35f * (size.value / 44f))) {
        Box(Modifier.fillMaxSize().clip(shape).background(main), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(
                text, color = inkOn(main), fontSize = (size.value * (if (xl) .30f else .30f)).sp,
                fontWeight = FontWeight.ExtraBold, fontFamily = u.fb, maxLines = 1, softWrap = false,
            )
        }
    }
}

// ================================================================ glass surfaces
/** The glass look: translucent fill, soft sheen and a lit top edge. Switches with Settings > Surface. */
@Composable
fun Modifier.glass(shape: Shape? = null): Modifier {
    val u = LocalU.current
    val sh = shape ?: u.shape
    if (u.coral) {
        val base = if (u.dark) this else this.shadow(10.dp, sh, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
        return base.clip(sh).background(u.sf)
    }
    return when (u.s.surf) {
        "solid" -> this.clip(sh).background(u.sf).border(1.dp, u.glassEdge, sh)
        "outline" -> this.clip(sh).border(1.dp, u.outline, sh)
        else -> {
            val base = if (u.dark) this else this.shadow(8.dp, sh, ambientColor = u.shadowC, spotColor = u.shadowC)
            base.clip(sh).background(u.glassFill)
                .background(Brush.verticalGradient(listOf(u.glassSheen, Color.Transparent)))
                .border(1.dp, Brush.verticalGradient(listOf(u.glassTop, u.glassEdge)), sh)
        }
    }
}

@Composable
fun Glass(
    modifier: Modifier = Modifier, radius: Dp? = null, pad: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null, border: Color? = null, content: @Composable ColumnScope.() -> Unit,
) {
    val u = LocalU.current
    val shape = RoundedCornerShape(radius ?: u.r)
    val m = modifier.glass(shape).then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Column(m.padding(pad), content = content)
}

fun Modifier.bd(top: Dp = 10.dp, bottom: Dp = 120.dp) = padding(start = 16.dp, top = top, end = 16.dp, bottom = bottom)

@Composable
fun KvRow(last: Boolean = false, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val u = LocalU.current
    Row(
        modifier.fillMaxWidth()
            .then(if (last) Modifier else Modifier.drawBehind { drawLine(u.ln, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) })
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, content = content,
    )
}

/** Glass card of label / value rows. Blank values are skipped. */
@Composable
fun KvPanel(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val shown = rows.filter { it.second.isNotBlank() }
    if (shown.isEmpty()) return
    val u = LocalU.current
    Glass(modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
        shown.forEachIndexed { i, (k, v) ->
            KvRow(i == shown.lastIndex) {
                Tx(k, 14 * u.fs, 500, u.mu)
                Spacer(Modifier.width(14.dp))
                Tx(v, 14 * u.fs, 700, modifier = Modifier.weight(1f, false), align = TextAlign.End)
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val u = LocalU.current
    Column(modifier.glass(RoundedCornerShape(maxOf(14.dp, u.r - 6.dp))).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Tx(label, 12 * u.fs, 500, u.mu, maxLines = 1)
        Tx(value, 22 * u.fs, 800, maxLines = 1)
    }
}

@Composable
fun TileGrid(items: List<Pair<String, String>>, cols: Int, gap: Dp = 10.dp) {
    items.chunked(cols).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = gap).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
            row.forEach { (l, v) -> StatTile(l, v, Modifier.weight(1f).fillMaxHeight()) }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun H3(t: String, start: Dp = 6.dp) {
    val u = LocalU.current
    Tx(t, 15 * u.fs, 800, u.ink, Modifier.padding(start = start, top = 18.dp, bottom = 10.dp))
}

@Composable
fun EmptyBlock(text: String, content: @Composable ColumnScope.() -> Unit = {}) {
    val u = LocalU.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Tx(text, 14 * u.fs, 500, u.mu, align = TextAlign.Center, lh = 14 * u.fs * 1.6f)
        content()
    }
}

// ================================================================ buttons / chips
@Composable
fun Btn(text: String, kind: String = "p", side: Dp = 16.dp, bottom: Dp = 10.dp, onClick: () -> Unit) {
    val u = LocalU.current
    val shape = RoundedCornerShape(minOf(u.r, 26.dp))
    val m = Modifier.fillMaxWidth().padding(start = side, end = side, bottom = bottom)
    val inner = when (kind) {
        "s" -> m.glass(shape)
        "red" -> m.clip(shape).border(1.dp, u.red.copy(alpha = .5f), shape)
        else -> m.clip(shape).background(u.pri)
    }
    Box(inner.clickable(onClick = onClick).padding(16.dp), contentAlignment = Alignment.Center) {
        Tx(text, 15 * u.fs, 800, when (kind) { "s" -> u.ink; "red" -> u.red; else -> u.onPri })
    }
}

/** Compact button used inside empty states. */
@Composable
fun SmallBtn(text: String, kind: String = "p", onClick: () -> Unit) {
    val u = LocalU.current
    val shape = RoundedCornerShape(minOf(u.r, 24.dp))
    val m = Modifier.padding(top = 10.dp)
    Box((if (kind == "s") m.glass(shape) else m.clip(shape).background(u.pri)).clickable(onClick = onClick)
        .padding(horizontal = 22.dp, vertical = 12.dp)) { Tx(text, 15 * u.fs, 800, if (kind == "s") u.ink else u.onPri) }
}

/** Dashed outline button, like "Add club" in the mockups. */
@Composable
fun DashedBtn(text: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val u = LocalU.current
    val rad = minOf(u.r, 24.dp)
    Row(
        modifier.height(68.dp).clip(RoundedCornerShape(rad))
            .drawBehind {
                drawRoundRect(u.outline, cornerRadius = CornerRadius(rad.toPx()),
                    style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f))))
            }.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Ico(icon, 20.dp, u.ink, 1.9f)
        Spacer(Modifier.width(10.dp))
        Tx(text, 15 * u.fs, 800, maxLines = 1)
    }
}

@Composable
fun GlassBtn(text: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val u = LocalU.current
    val rad = minOf(u.r, 24.dp)
    Row(
        modifier.height(68.dp).glass(RoundedCornerShape(rad)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Ico(icon, 20.dp, u.ink, 1.9f)
        Spacer(Modifier.width(10.dp))
        Tx(text, 15 * u.fs, 800, maxLines = 1)
    }
}

@Composable
fun DeleteBtn(text: String, side: Dp = 16.dp, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(2500); armed = false } }
    Btn(if (armed) "Tap again to confirm" else text, "red", side) { if (armed) onConfirm() else armed = true }
}

@Composable
fun SaveBtn(text: String, bottom: Dp = 10.dp, onSave: () -> String?) {
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { if (msg != null) { delay(1600); msg = null } }
    Btn(msg ?: text, side = 0.dp, bottom = bottom) { msg = onSave() }
}

/** Header chip row: 44dp pills, the selected one takes the accent colour. */
@Composable
fun HChips(items: List<Pair<String, String>>, cur: String?, onPick: (String) -> Unit) {
    val h = LocalHdr.current
    val u = LocalU.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { (k, t) ->
            val on = k == cur
            Box(
                Modifier.height(44.dp).clip(CircleShape).background(if (on) u.pri else h.chip)
                    .then(if (on) Modifier else Modifier.border(1.dp, h.chipEdge, CircleShape))
                    .clickable { onPick(k) }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) { Tx(t, 14 * u.fs, if (on) 800 else 700, if (on) u.onPri else h.fg, maxLines = 1) }
        }
    }
}

/** Plain chip for normal surfaces (dialogs, suggestions, settings). */
@Composable
fun Chip(t: String, on: Boolean, onClick: () -> Unit) {
    val u = LocalU.current
    Box(
        Modifier.height(38.dp).clip(CircleShape).background(if (on) u.pri else Color.Transparent)
            .then(if (on) Modifier else Modifier.border(1.dp, u.outline.copy(alpha = u.outline.alpha * .7f), CircleShape))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(t, 13 * u.fs, if (on) 800 else 600, if (on) u.onPri else u.ink, maxLines = 1) }
}

@Composable
fun SearchField(value: String, ph: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val u = LocalU.current
    val h = LocalHdr.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp).fillMaxWidth().height(48.dp).clip(CircleShape)
            .background(h.chip).border(1.dp, h.chipEdge, CircleShape).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ico("search", 20.dp, h.fg, 1.9f)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(ph, color = h.fg.copy(alpha = .7f), fontSize = 15.sp, fontFamily = u.fb)
            BasicTextField(value, onChange, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                textStyle = TextStyle(color = h.fg, fontSize = 15.sp, fontFamily = u.fb), cursorBrush = SolidColor(h.fg))
        }
        Box(Modifier.size(28.dp).clip(CircleShape).clickable { if (value.isNotEmpty()) onChange("") else onClose() }, contentAlignment = Alignment.Center) {
            Ico("close", 18.dp, h.fg, 1.9f)
        }
    }
}

// ================================================================ form inputs
@Composable
fun Lbl(t: String) {
    val u = LocalU.current
    Tx(t, 13 * u.fs, 700, u.mu, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
fun Modifier.field(focused: Boolean = false): Modifier {
    val u = LocalU.current
    val sh = RoundedCornerShape(minOf(u.r, 18.dp))
    return this.clip(sh).background(u.fieldFill).border(1.dp, if (focused) u.acc else u.glassEdge, sh)
}

@Composable
fun Toggle(on: Boolean, onClick: () -> Unit) {
    val u = LocalU.current
    val x by animateDpAsState(if (on) 23.dp else 3.dp, label = "knob")
    Box(Modifier.size(48.dp, 28.dp).clip(CircleShape).background(if (on) u.pri else u.outline.copy(alpha = .35f)).clickable(onClick = onClick)) {
        Box(Modifier.offset(x, 3.dp).size(22.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
    }
}

@Composable
fun Fld(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    number: Boolean = false, ph: String = "", multi: Boolean = false,
) {
    val u = LocalU.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Column(modifier) {
        Lbl(label)
        BasicTextField(
            value, { onChange(if (number) it.filter(Char::isDigit) else it) }, Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = u.ink, fontSize = 16.sp, fontFamily = u.fb),
            cursorBrush = SolidColor(u.acc), interactionSource = src, singleLine = !multi,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().then(if (multi) Modifier.heightIn(min = 76.dp) else Modifier)
                        .field(focused).padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (value.isEmpty() && ph.isNotEmpty()) Text(ph, color = u.mu.copy(alpha = .7f), fontSize = 16.sp, fontFamily = u.fb)
                    inner()
                }
            },
        )
    }
}

@Composable
fun DropSelect(
    label: String, valueName: String, options: List<Pair<String, String>>, onPick: (String) -> Unit,
    modifier: Modifier = Modifier, none: String? = null,
) {
    val u = LocalU.current
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Lbl(label)
        Box {
            Row(Modifier.fillMaxWidth().field().clickable { open = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Tx(valueName.ifBlank { none ?: "Select" }, 16 * u.fs, modifier = Modifier.weight(1f), maxLines = 1)
                Tx("▾", 14f, c = u.mu)
            }
            DropdownMenu(open, { open = false }) {
                if (none != null) DropdownMenuItem(text = { Text(none) }, onClick = { onPick(""); open = false })
                options.forEach { (id, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { onPick(id); open = false }) }
            }
        }
    }
}

@Composable
fun Upload(label: String, d: Draft, k: String, aspect: Float = 1f) {
    val u = LocalU.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Bitmap?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { pending = withContext(Dispatchers.IO) { ImageStore.decode(ctx, uri) } }
    }
    val rr = minOf(u.r, 18.dp)
    Lbl(label)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(rr)).background(u.fieldFill)
            .drawBehind {
                drawRoundRect(u.outline, cornerRadius = CornerRadius(rr.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            .clickable { pick.launch("image/*") }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(u.ln)) {
            val bmp = rememberBitmap(d[k])
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column {
            Tx(if (d[k].isEmpty()) "Upload image" else "Change image", 14 * u.fs, 700)
            Tx("PNG or JPG, then crop and resize", 12f, 500, u.mu)
        }
    }
    pending?.let { bmp ->
        ImageEditor(bmp, aspect, onDone = { out ->
            pending = null
            scope.launch { d[k] = withContext(Dispatchers.IO) { ImageStore.save(ctx, out) } }
        }, onCancel = { pending = null })
    }
}

/** Crop and resize dialog: drag and pinch (or slider) to place the image in the frame, pick the output size. */
@Composable
fun ImageEditor(src: Bitmap, aspect: Float, onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    val u = LocalU.current
    var zoom by remember { mutableFloatStateOf(1f) }          // 1 = whole image fits inside the frame
    var off by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var outW by remember { mutableIntStateOf(512) }
    val img = remember(src) { src.asImageBitmap() }
    fun fit() = if (frame.width == 0) 1f else minOf(frame.width / src.width.toFloat(), frame.height / src.height.toFloat())
    fun fill() = if (frame.width == 0) 1f else maxOf(frame.width / src.width.toFloat(), frame.height / src.height.toFloat())

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(u.bg).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Hd("Crop and resize", 28f, modifier = Modifier.padding(top = 8.dp))
            Tx("Drag and pinch to place the image. Shrink it to leave transparent space around it.", 13f, 500, u.mu,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(u.rs(10)).onSizeChanged { frame = it }
                .pointerInput(Unit) { detectTransformGestures { _, pan, z, _ -> zoom = (zoom * z).coerceIn(.1f, 6f); off += pan } }) {
                Canvas(Modifier.fillMaxSize()) {
                    val cs = 12.dp.toPx()
                    var y = 0f; var row = 0
                    while (y < size.height) {
                        var x = 0f; var col = 0
                        while (x < size.width) {
                            drawRect(if ((row + col) % 2 == 0) Color(0xFFE9E9E9) else Color.White, Offset(x, y), Size(cs, cs))
                            x += cs; col++
                        }
                        y += cs; row++
                    }
                    val s = minOf(size.width / src.width, size.height / src.height) * zoom
                    val w = src.width * s; val h = src.height * s
                    drawImage(img, dstOffset = IntOffset(((size.width - w) / 2 + off.x).roundToInt(), ((size.height - h) / 2 + off.y).roundToInt()),
                        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)), filterQuality = FilterQuality.High)
                    val g = Color.Black.copy(alpha = .35f)
                    for (i in 1..2) {
                        drawLine(g, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.dp.toPx())
                        drawLine(g, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1.dp.toPx())
                    }
                }
            }
            Lbl("Zoom")
            Slider(zoom.coerceIn(.1f, 6f), { zoom = it }, valueRange = .1f..6f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = u.pri, inactiveTrackColor = u.ln))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Fit", false) { zoom = 1f; off = Offset.Zero }
                Chip("Fill", false) { zoom = fill() / fit(); off = Offset.Zero }
            }
            Lbl("Output size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(128, 256, 512, 1024).forEach { Chip("$it px", outW == it) { outW = it } }
            }
            Tx("$outW x ${(outW / aspect).roundToInt()} px, transparency kept", 12f, 500, u.mu, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Btn("Cancel", "s", side = 0.dp) { onCancel() } }
                Box(Modifier.weight(1f)) {
                    Btn("Use image", side = 0.dp) {
                        if (frame.width == 0) return@Btn
                        val k = outW / frame.width.toFloat()
                        val outH = (outW / aspect).roundToInt().coerceAtLeast(1)
                        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        val s = fit() * zoom
                        val w = src.width * s; val h = src.height * s
                        val l = (frame.width - w) / 2 + off.x; val t = (frame.height - h) / 2 + off.y
                        android.graphics.Canvas(out).drawBitmap(
                            src, null, android.graphics.RectF(l * k, t * k, (l + w) * k, (t + h) * k),
                            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG),
                        )
                        onDone(out)
                    }
                }
            }
        }
    }
}

// ================================================================ colour pickers
val SWATCHES = listOf("#0ea5e9", "#2da678", "#f5d400", "#f97316", "#e11d48", "#7c3aed", "#111111", "#ffffff")

@Composable
fun Swatch(color: String, on: Boolean, onClick: () -> Unit) {
    val u = LocalU.current
    Box(Modifier.size(38.dp).border(2.dp, if (on) u.ink else u.glassEdge, CircleShape).padding(4.dp).clip(CircleShape).background(hex(color)).clickable(onClick = onClick))
}

@Composable
fun CustomSwatch(color: String, onPick: (String) -> Unit) {
    val u = LocalU.current
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(52.dp, 38.dp).clip(u.rs(14)).border(1.dp, u.outline, u.rs(14)).padding(3.dp)
        .clip(u.rs(17)).background(hex(color)).clickable { open = true })
    if (open) ColorDialog(color, { onPick(it); open = false }, { open = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorField(label: String, color: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    Column(modifier) {
        Lbl(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SWATCHES.forEach { c -> Swatch(c, color.equals(c, true)) { onPick(c) } }
            CustomSwatch(color, onPick)
        }
    }
}

@Composable
fun ColorDialog(initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val u = LocalU.current
    val c0 = hex(initial)
    var r by remember { mutableFloatStateOf(c0.red) }
    var g by remember { mutableFloatStateOf(c0.green) }
    var b by remember { mutableFloatStateOf(c0.blue) }
    var text by remember { mutableStateOf(c0.toHex()) }
    @Composable fun ch(v: Float, c: Color, on: (Float) -> Unit) =
        Slider(v, { on(it); text = Color(r, g, b).toHex() },
            colors = SliderDefaults.colors(thumbColor = c, activeTrackColor = c, inactiveTrackColor = u.ln))
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = if (u.dark) Color(0xFF16181D) else Color.White,
        confirmButton = { TextButton({ onDone(Color(r, g, b).toHex()) }) { Text("OK", color = u.ink) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = u.mu) } },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(u.rs(14)).background(Color(r, g, b)).border(1.dp, u.glassEdge, u.rs(14)))
                Fld("Hex colour (for example #1b5fc1)", text, { t ->
                    text = t
                    parseHex(t)?.let { c -> r = c.red; g = c.green; b = c.blue }
                }, ph = "#1b5fc1")
                Spacer(Modifier.height(8.dp))
                ch(r, Color(0xFFE5484D)) { r = it }
                ch(g, Color(0xFF2DA678)) { g = it }
                ch(b, Color(0xFF2F6BFF)) { b = it }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountryField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Fld("Country", value, onChange, ph = "Start typing")
        val q = value.trim()
        val hits = if (q.isEmpty()) emptyList()
        else COUNTRIES.map { it.first }.filter { it.startsWith(q, true) && !it.equals(q, true) }.take(4)
        if (hits.isNotEmpty()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            hits.forEach { n -> Chip("${flag(n)}$n", false) { onChange(n) } }
        }
    }
}


/** Segmented control: soft track with the selected option as a filled pill (black in Coral, accent in Glass). */
@Composable
fun SegRow(opts: List<Pair<String, String>>, cur: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val u = LocalU.current
    val outer = if (u.coral) CircleShape else u.rs(12)
    val inner = if (u.coral) CircleShape else u.rs(15)
    Row(
        modifier.fillMaxWidth().clip(outer).background(if (u.coral) u.track else u.fieldFill)
            .then(if (u.coral) Modifier else Modifier.border(1.dp, u.glassEdge, outer)).padding(if (u.coral) 4.dp else 3.dp),
    ) {
        opts.forEach { (v, t) ->
            val on = v == cur
            Box(
                Modifier.weight(1f).clip(inner).background(if (on) u.pri else Color.Transparent)
                    .clickable { onPick(v) }.padding(horizontal = 3.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Tx(t, 13 * u.fs, if (on) 800 else 600, if (on) u.onPri else u.mu, maxLines = 1) }
        }
    }
}
