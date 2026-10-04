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

/** Mockup's adj(): keeps dark colours, darkens light ones so white header text stays readable. */
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
class U(val s: Settings, val dark: Boolean, anton: FontFamily, inter: FontFamily, nunito: FontFamily) {
    val bg = if (dark) Color(0xFF0B0B0D) else Color(0xFFF4F4F6)
    val sf = if (dark) Color(0xFF17171B) else Color.White
    val ink = if (dark) Color(0xFFF4F4F6) else InkDark
    val mu = if (dark) Color(0xFF9A9AA3) else Color(0xFF6D6D78)
    val ln = if (dark) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .09f)
    val red = Color(0xFFFF5A5F)
    val acc: Color = hex(s.acc).let { if (!dark && lum(it) > 190) InkDark else it }
    val on = if (lum(acc) > 150) InkDark else Color.White
    val r = s.r.dp
    val ring = s.ring.dp
    val fs = when (s.fs) { "s" -> .92f; "l" -> 1.12f; else -> 1f }
    val pad = if (s.d == "compact") 7.dp else 12.dp
    val sans = s.f == "sans"
    val fb: FontFamily = when (s.f) { "rounded" -> nunito; "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> inter }
    val fd: FontFamily = when (s.f) { "rounded" -> nunito; "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> anton }
    val shape = RoundedCornerShape(r)
    fun rs(d: Int) = RoundedCornerShape(maxOf(0.dp, r - d.dp))

    /** Header glow gradient stops (top to bottom) for a page colour. */
    fun glow(c: Color): List<Color> {
        val st = listOf(lerp(c, Color.White, .34f), c, lerp(bg, c, .38f), bg)
        return when (s.g) { "soft" -> st.map { it.copy(alpha = .55f) }; "mono" -> st.map { gray(it) }; else -> st }
    }

    /** Decide light or dark header foreground from the colour in the middle of the glow. */
    fun hdrFor(c: Color): Hdr {
        val g = glow(c)
        var m = lerp(g[1], g[2], .66f)
        if (s.g == "soft") m = m.compositeOver(bg)
        return Hdr(lum(m) <= 150)
    }
}

class Hdr(val light: Boolean) {
    val fg = if (light) Color.White else InkDark
    val chip = if (light) Color.White.copy(alpha = .2f) else Color.Black.copy(alpha = .08f)
    val chipOn = if (light) Color.White else InkDark
    val chipOnFg = if (light) Color(0xFF111111) else Color.White
    val pillBorder = if (light) Color.White.copy(alpha = .3f) else Color.Black.copy(alpha = .15f)
}

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
        t, modifier, color = if (c == Color.Unspecified) u.ink else c, fontSize = size.sp, fontWeight = FontWeight(w),
        fontFamily = u.fb, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        lineHeight = if (lh > 0f) lh.sp else TextUnit.Unspecified,
    )
}

/** Display text (titles, big numbers): Anton / Nunito / Georgia / mono depending on the typeface setting. */
@Composable
fun Hd(t: String, size: Float, c: Color = Color.Unspecified, modifier: Modifier = Modifier, align: TextAlign? = null, w: Int = 400, maxLines: Int = Int.MAX_VALUE) {
    val u = LocalU.current
    Text(
        t, modifier, color = if (c == Color.Unspecified) u.ink else c, fontSize = size.sp, fontWeight = FontWeight(w),
        fontFamily = u.fd, textAlign = align, lineHeight = (size * 1.1f).sp, letterSpacing = 0.01.em,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

// ================================================================ icons
private val ICO = mapOf(
    "shield" to "M12 3l7 3v5.5c0 4-3 7.5-7 9.5-4-2-7-5.5-7-9.5V6l7-3z",
    "trophy" to "M8 4h8v5a4 4 0 0 1-8 0V4zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 20h8",
    "plus" to "M12 5v14M5 12h14",
    "bars" to "M5 20v-5M10 20V8M15 20V4M20 20v-9",
    "search" to "M15.5 15.5L20 20",
    "close" to "M6 6l12 12M18 6L6 18",
    "back" to "M15 5l-7 7 7 7",
    "gear" to "M19.4 15 a1.65 1.65 0 0 0 .33 1.82 l.06 .06 a2 2 0 1 1 -2.83 2.83 l-.06 -.06 a1.65 1.65 0 0 0 -1.82 -.33 " +
        "a1.65 1.65 0 0 0 -1 1.51 V21 a2 2 0 1 1 -4 0 v-.09 A1.65 1.65 0 0 0 9 19.4 a1.65 1.65 0 0 0 -1.82 .33 l-.06 .06 " +
        "a2 2 0 1 1 -2.83 -2.83 l.06 -.06 a1.65 1.65 0 0 0 .33 -1.82 a1.65 1.65 0 0 0 -1.51 -1 H3 a2 2 0 1 1 0 -4 h.09 " +
        "A1.65 1.65 0 0 0 4.6 9 a1.65 1.65 0 0 0 -.33 -1.82 l-.06 -.06 a2 2 0 1 1 2.83 -2.83 l.06 .06 a1.65 1.65 0 0 0 1.82 .33 " +
        "H9 a1.65 1.65 0 0 0 1 -1.51 V3 a2 2 0 1 1 4 0 v.09 a1.65 1.65 0 0 0 1 1.51 a1.65 1.65 0 0 0 1.82 -.33 l.06 -.06 " +
        "a2 2 0 1 1 2.83 2.83 l-.06 .06 a1.65 1.65 0 0 0 -.33 1.82 V9 a1.65 1.65 0 0 0 1.51 1 H21 a2 2 0 1 1 0 4 h-.09 " +
        "a1.65 1.65 0 0 0 -1.51 1 z",
)

@Composable
fun Ico(name: String, size: Dp, color: Color, sw: Float = 1.5f) {
    val path = remember(name) { PathParser().parsePathString(ICO[name]!!).toPath() }
    Canvas(Modifier.size(size)) {
        val sc = this.size.width / 24f
        withTransform({ scale(sc, sc, Offset.Zero) }) {
            if (name == "gear") drawCircle(color, 3f, Offset(12f, 12f), style = Stroke(sw))
            if (name == "search") drawCircle(color, 6.5f, Offset(10.5f, 10.5f), style = Stroke(sw))
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
    Box(Modifier.size(size).clip(shape).background(edge).padding(if (xl) u.ring else u.ring * .35f)) {
        Box(Modifier.fillMaxSize().clip(shape).background(main), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(
                text, color = inkOn(main), fontSize = (if (xl) 34 else 13).sp,
                fontWeight = FontWeight.ExtraBold, fontFamily = u.fb, maxLines = 1, softWrap = false,
            )
        }
    }
}

// ================================================================ layout pieces
fun Modifier.bd(top: Dp = 10.dp, bottom: Dp = 110.dp) = padding(start = 16.dp, top = top, end = 16.dp, bottom = bottom)

@Composable
fun Panel(
    modifier: Modifier = Modifier, side: Dp = 16.dp, bottom: Dp = 14.dp, h: Dp = 16.dp, v: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val u = LocalU.current
    Column(
        modifier.padding(start = side, end = side, bottom = bottom).fillMaxWidth().clip(u.shape).background(u.sf)
            .border(1.dp, u.ln, u.shape).padding(horizontal = h, vertical = v),
        content = content,
    )
}

@Composable
fun KvRow(last: Boolean = false, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val u = LocalU.current
    Row(
        modifier.fillMaxWidth()
            .then(if (last) Modifier else Modifier.drawBehind { drawLine(u.ln, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) })
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, content = content,
    )
}

@Composable
fun KvPanel(rows: List<Pair<String, String>>, modifier: Modifier = Modifier, side: Dp = 16.dp) {
    val shown = rows.filter { it.second.isNotBlank() }
    if (shown.isEmpty()) return
    val u = LocalU.current
    Panel(modifier, side) {
        shown.forEachIndexed { i, (k, v) ->
            KvRow(i == shown.lastIndex) {
                Tx(k, 14 * u.fs, c = u.mu)
                Spacer(Modifier.width(14.dp))
                Tx(v, 14 * u.fs, 600, modifier = Modifier.weight(1f, false), align = TextAlign.End)
            }
        }
    }
}

@Composable
fun H3(t: String, start: Dp = 6.dp) {
    val u = LocalU.current
    Tx(t, 14 * u.fs * .95f, 600, u.mu, Modifier.padding(start = start, top = 16.dp, bottom = 8.dp))
}

@Composable
fun EmptyBlock(text: String, content: @Composable ColumnScope.() -> Unit = {}) {
    val u = LocalU.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Tx(text, 14 * u.fs, c = u.mu, align = TextAlign.Center, lh = 14 * u.fs * 1.6f)
        content()
    }
}

// ================================================================ buttons / chips
@Composable
fun Btn(text: String, kind: String = "p", side: Dp = 16.dp, bottom: Dp = 10.dp, onClick: () -> Unit) {
    val u = LocalU.current
    val shape = RoundedCornerShape(u.r + 4.dp)
    val bgc = when (kind) { "s" -> u.sf; "red" -> Color.Transparent; else -> u.acc }
    val fg = when (kind) { "s" -> u.ink; "red" -> u.red; else -> u.on }
    Box(
        Modifier.fillMaxWidth().padding(start = side, end = side, bottom = bottom).clip(shape).background(bgc)
            .then(if (kind == "s") Modifier.border(1.dp, u.ln, shape) else Modifier)
            .clickable(onClick = onClick).padding(15.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(text, 15f, 600, fg) }
}

/** Compact button used inside empty states. */
@Composable
fun SmallBtn(text: String, kind: String = "p", onClick: () -> Unit) {
    val u = LocalU.current
    val shape = RoundedCornerShape(u.r + 4.dp)
    Box(
        Modifier.padding(top = 10.dp).clip(shape).background(if (kind == "s") u.sf else u.acc)
            .then(if (kind == "s") Modifier.border(1.dp, u.ln, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 12.dp),
    ) { Tx(text, 15f, 600, if (kind == "s") u.ink else u.on) }
}

@Composable
fun DeleteBtn(text: String, side: Dp = 16.dp, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(2500); armed = false } }
    Btn(if (armed) "Tap again to confirm" else text, "red", side) { if (armed) onConfirm() else armed = true }
}

@Composable
fun SaveBtn(text: String, bottom: Dp = 10.dp, onSave: () -> Boolean) {
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { if (msg != null) { delay(1400); msg = null } }
    Btn(msg ?: text, side = 0.dp, bottom = bottom) { if (!onSave()) msg = "Add a name first" }
}

/** Chip row that sits on the coloured header. */
@Composable
fun HChips(items: List<Pair<String, String>>, cur: String?, onPick: (String) -> Unit) {
    val h = LocalHdr.current
    val u = LocalU.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (k, t) ->
            val on = k == cur
            Box(Modifier.clip(CircleShape).background(if (on) h.chipOn else h.chip).clickable { onPick(k) }.padding(horizontal = 15.dp, vertical = 8.dp)) {
                Tx(t, 14 * u.fs * .93f, 500, if (on) h.chipOnFg else h.fg, maxLines = 1)
            }
        }
    }
}

/** Plain chip for use on normal surfaces (dialogs, suggestions). */
@Composable
fun Chip(t: String, on: Boolean, onClick: () -> Unit) {
    val u = LocalU.current
    Box(Modifier.clip(CircleShape).background(if (on) u.ink else u.sf).border(1.dp, u.ln, CircleShape).clickable(onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 8.dp)) {
        Tx(t, 13f, 500, if (on) u.bg else u.mu)
    }
}

@Composable
fun SearchField(value: String, ph: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    val u = LocalU.current
    val h = LocalHdr.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp).fillMaxWidth().clip(CircleShape).background(h.chip)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Ico("search", 20.dp, h.fg, 1.8f)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(ph, color = h.fg.copy(alpha = .7f), fontSize = 15.sp, fontFamily = u.fb)
            BasicTextField(value, onChange, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                textStyle = TextStyle(color = h.fg, fontSize = 15.sp, fontFamily = u.fb), cursorBrush = SolidColor(h.fg))
        }
        Box(Modifier.size(24.dp).clip(CircleShape).clickable { if (value.isNotEmpty()) onChange("") else onClose() }, contentAlignment = Alignment.Center) {
            Ico("close", 18.dp, h.fg, 1.8f)
        }
    }
}

// ================================================================ form inputs
@Composable
fun Lbl(t: String) {
    val u = LocalU.current
    Tx(t, 14 * u.fs * .86f, 500, u.mu, Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
fun Toggle(on: Boolean, onClick: () -> Unit) {
    val u = LocalU.current
    val x by animateDpAsState(if (on) 23.dp else 3.dp, label = "knob")
    Box(Modifier.size(48.dp, 28.dp).clip(CircleShape).background(if (on) u.acc else u.ln).clickable(onClick = onClick)) {
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
            cursorBrush = SolidColor(u.ink), interactionSource = src, singleLine = !multi,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().then(if (multi) Modifier.heightIn(min = 70.dp) else Modifier)
                        .clip(u.rs(6)).background(u.sf).border(1.dp, if (focused) u.ink else u.ln, u.rs(6))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    if (value.isEmpty() && ph.isNotEmpty()) Text(ph, color = u.mu.copy(alpha = .7f), fontSize = 16.sp, fontFamily = u.fb)
                    inner()
                }
            },
        )
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
    val rr = maxOf(0.dp, u.r - 6.dp)
    Lbl(label)
    Row(
        Modifier.fillMaxWidth().clip(u.rs(6)).background(u.sf)
            .drawBehind {
                drawRoundRect(u.ln, cornerRadius = CornerRadius(rr.toPx()),
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
            Tx(if (d[k].isEmpty()) "Upload image" else "Change image", 14 * u.fs)
            Tx("PNG or JPG, then crop and resize", 12f, c = u.mu)
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
            Tx("Drag and pinch to place the image. Shrink it to leave transparent space around it.", 13f, c = u.mu,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(u.rs(6)).onSizeChanged { frame = it }
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
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = u.acc, inactiveTrackColor = u.ln))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Fit", false) { zoom = 1f; off = Offset.Zero }
                Chip("Fill", false) { zoom = fill() / fit(); off = Offset.Zero }
            }
            Lbl("Output size")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(128, 256, 512, 1024).forEach { Chip("$it px", outW == it) { outW = it } }
            }
            Tx("$outW x ${(outW / aspect).roundToInt()} px, transparency kept", 12f, c = u.mu, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 18.dp))
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
    Box(Modifier.size(38.dp).border(2.dp, if (on) u.ink else u.ln, CircleShape).padding(4.dp).clip(CircleShape).background(hex(color)).clickable(onClick = onClick))
}

@Composable
fun CustomSwatch(color: String, onPick: (String) -> Unit) {
    val u = LocalU.current
    var open by remember { mutableStateOf(false) }
    Box(Modifier.size(52.dp, 36.dp).clip(u.rs(6)).background(u.sf).border(1.dp, u.ln, u.rs(6)).padding(3.dp)
        .clip(u.rs(9)).background(hex(color)).clickable { open = true })
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
        onDismissRequest = onDismiss, containerColor = u.bg,
        confirmButton = { TextButton({ onDone(Color(r, g, b).toHex()) }) { Text("OK", color = u.ink) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = u.mu) } },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(u.rs(6)).background(Color(r, g, b)).border(1.dp, u.ln, u.rs(6)))
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

@Composable
fun LeagueSelect(d: Draft, leagues: List<League>) {
    val u = LocalU.current
    var open by remember { mutableStateOf(false) }
    Lbl("League")
    Box {
        Row(Modifier.fillMaxWidth().clip(u.rs(6)).background(u.sf).border(1.dp, u.ln, u.rs(6)).clickable { open = true }
            .padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Tx(leagues.find { it.id == d["lid"] }?.name ?: "Create a league first", 16f, modifier = Modifier.weight(1f))
            Tx("▾", 14f, c = u.mu)
        }
        DropdownMenu(open, { open = false }) {
            leagues.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { d["lid"] = l.id; open = false }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountryField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Fld("Country", value, onChange, ph = "Start typing")
        val q = value.trim()
        val hits = if (q.isEmpty()) emptyList()
        else COUNTRIES.map { it.first }.filter { it.startsWith(q, true) && !it.equals(q, true) }.take(4)
        if (hits.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            hits.forEach { n -> Chip("${flag(n)}$n", false) { onChange(n) } }
        }
    }
}
