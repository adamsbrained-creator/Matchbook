package com.example.clubs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// "Rounded" typeface: the mockup uses Nunito. Add nunito.ttf to res/font and swap this for
// FontFamily(Font(R.font.nunito)) to match exactly. Falls back to the system sans meanwhile.
val RoundedFont: FontFamily = FontFamily.SansSerif

fun hex(s: String): Color = runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrDefault(Color.Gray)
fun Color.toHex(): String = String.format("#%06x", toArgb() and 0xFFFFFF)
fun lum(c: Color) = (c.red * 255 * 299 + c.green * 255 * 587 + c.blue * 255 * 114) / 1000

/** Every CSS variable from the mockup, resolved for the current settings. */
class U(val s: Settings, dark: Boolean) {
    val bg = if (dark) Color(0xFF0E1217) else Color.White
    val ink = if (dark) Color(0xFFEEF1F6) else Color(0xFF2D3A4D)
    val mu = if (dark) Color(0xFF8B96A8) else Color(0xFF7A8599)
    val ln = if (dark) Color(0xFF29323F) else Color(0xFFE3E6EE)
    val sf = if (dark) Color(0xFF1A212B) else Color(0xFFF4F5F9)
    val red = Color(0xFFE5484D)
    val acc = hex(s.acc)
    val on = if (lum(acc) > 150) Color(0xFF111111) else Color.White
    private val tn = when (s.tint) { "warm" -> Color(0xFFE0A040); "cool" -> Color(0xFF4A7FD0); "accent" -> acc; else -> bg }
    val bgt = lerp(bg, tn, .08f)
    val sft = lerp(sf, tn, .11f)
    val r = s.r.dp
    val ring = s.ring.dp
    val fs = when (s.fs) { "s" -> .92f; "l" -> 1.12f; else -> 1f }
    val pad = if (s.d == "compact") 7.dp else 12.dp
    val tw = when (s.tw) { "light" -> 300; "regular" -> 500; else -> 700 }
    val font: FontFamily = when (s.f) {
        "rounded" -> RoundedFont; "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> FontFamily.Default
    }
    val shape = RoundedCornerShape(r)
    fun rs(d: Int) = RoundedCornerShape(maxOf(0.dp, r - d.dp))
}

val LocalU = staticCompositionLocalOf<U> { error("U not provided") }

@Composable
fun Tx(
    t: String, size: Float, w: Int = 400, c: Color = Color.Unspecified, modifier: Modifier = Modifier,
    align: TextAlign? = null, lh: Float = 0f, maxLines: Int = Int.MAX_VALUE,
) {
    val u = LocalU.current
    Text(
        t, modifier, color = if (c == Color.Unspecified) u.ink else c, fontSize = size.sp, fontWeight = FontWeight(w),
        fontFamily = u.font, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        lineHeight = if (lh > 0f) lh.sp else TextUnit.Unspecified,
    )
}

// ---------- icons (same SVG paths as the mockup)
private val ICO = mapOf(
    "shield" to "M12 3l7 3v5.5c0 4-3 7.5-7 9.5-4-2-7-5.5-7-9.5V6l7-3z",
    "trophy" to "M8 4h8v5a4 4 0 0 1-8 0V4zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 20h8",
    "plus" to "M12 5v14M5 12h14",
    "bars" to "M5 20v-5M10 20V8M15 20V4M20 20v-9",
    "gear" to "M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8",
    "back" to "M15 5l-7 7 7 7",
)

@Composable
fun Ico(name: String, size: Dp, color: Color, sw: Float = 1.5f) {
    val path = remember(name) { PathParser().parsePathString(ICO[name]!!).toPath() }
    Canvas(Modifier.size(size)) {
        val sc = this.size.width / 24f
        withTransform({ scale(sc, sc, Offset.Zero) }) {
            if (name == "gear") drawCircle(color, 3f, Offset(12f, 12f), style = Stroke(sw))
            drawPath(path, color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ---------- images
object ImageStore {
    fun save(ctx: Context, uri: Uri): String {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return ""
        val sc = minOf(1f, 1024f / maxOf(bmp.width, bmp.height))
        val out = if (sc < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * sc).toInt(), (bmp.height * sc).toInt(), true) else bmp
        val f = File(ctx.filesDir, "img_${System.nanoTime()}.png")
        f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

// ---------- crest
private val ShieldShape = GenericShape { s, _ ->
    moveTo(0f, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height * .62f); lineTo(s.width / 2, s.height); lineTo(0f, s.height * .62f); close()
}
private val HexShape = GenericShape { s, _ ->
    moveTo(s.width * .25f, s.height * .06f); lineTo(s.width * .75f, s.height * .06f); lineTo(s.width, s.height * .5f)
    lineTo(s.width * .75f, s.height * .94f); lineTo(s.width * .25f, s.height * .94f); lineTo(0f, s.height * .5f); close()
}

@Composable
fun Crest(color: String, c2: String, logo: String, text: String, size: Dp = 44.dp, xl: Boolean = false) {
    val u = LocalU.current
    val main = hex(color)
    val edge = if (c2.isNotBlank()) hex(c2) else lerp(Color.Black, main, .62f)
    val shape: androidx.compose.ui.graphics.Shape = when (u.s.c) {
        "square" -> RoundedCornerShape(16); "shield" -> ShieldShape; "hex" -> HexShape; else -> CircleShape
    }
    Box(Modifier.size(size).clip(shape).background(edge).padding(if (xl) u.ring else u.ring * .33f)) {
        Box(Modifier.fillMaxSize().clip(shape).background(main), contentAlignment = Alignment.Center) {
            val bmp = rememberBitmap(logo)
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(
                text, color = if (lum(main) > 150) Color(0xFF2D3A4D) else Color.White, fontSize = (if (xl) 38 else 13).sp,
                fontWeight = FontWeight.Black, fontFamily = u.font, maxLines = 1, softWrap = false,
            )
        }
    }
}

// ---------- layout pieces
fun Modifier.bd() = padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 14.dp)

@Composable
fun TopBar(title: String, gear: Boolean, onGear: () -> Unit = {}) {
    val u = LocalU.current
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Tx(title, 28 * u.fs, u.tw, modifier = Modifier.weight(1f))
        if (gear) Box(Modifier.size(38.dp).clip(CircleShape).background(u.sf).clickable(onClick = onGear), contentAlignment = Alignment.Center) {
            Ico("gear", 22.dp, u.ink, 1.7f)
        }
    }
}

@Composable
fun BackBtn(label: String, onClick: () -> Unit) {
    val u = LocalU.current
    Row(Modifier.clickable(onClick = onClick).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Ico("back", 20.dp, u.mu, 1.6f)
        Tx(label, 17f, c = u.mu)
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier, side: Dp = 18.dp, h: Dp = 18.dp, v: Dp = 2.dp, content: @Composable ColumnScope.() -> Unit,
) {
    val u = LocalU.current
    Column(
        modifier.padding(start = side, end = side, bottom = 16.dp).fillMaxWidth().clip(u.shape).background(u.sft)
            .padding(horizontal = h, vertical = v),
        content = content,
    )
}

@Composable
fun KvRow(last: Boolean = false, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val u = LocalU.current
    val line = u.ln.copy(alpha = u.ln.alpha * .6f)
    Row(
        modifier.fillMaxWidth()
            .then(if (last) Modifier else Modifier.drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) })
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, content = content,
    )
}

@Composable
fun KvPanel(rows: List<Pair<String, String>>, modifier: Modifier = Modifier, side: Dp = 18.dp) {
    if (rows.isEmpty()) return
    val u = LocalU.current
    Panel(modifier, side) {
        rows.forEachIndexed { i, (k, v) ->
            KvRow(i == rows.lastIndex) {
                Tx(k, 14 * u.fs, c = u.mu)
                Spacer(Modifier.width(14.dp))
                Tx(v.ifBlank { "-" }, 14 * u.fs, 700, modifier = Modifier.weight(1f, false), align = TextAlign.End)
            }
        }
    }
}

@Composable
fun H3(t: String) = Tx(t, 16f, 700, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp))

@Composable
fun Empty(t: String, pad: Dp = 60.dp) = Box(Modifier.fillMaxWidth().padding(vertical = pad), contentAlignment = Alignment.Center) {
    Tx(t, 14f, c = LocalU.current.mu, align = TextAlign.Center, lh = 21f)
}

@Composable
fun Btn(text: String, kind: String = "p", side: Dp = 16.dp, onClick: () -> Unit) {
    val u = LocalU.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bgc = when {
        kind == "red" -> Color.Transparent
        pressed -> u.acc
        kind == "s" -> u.sf
        else -> lerp(u.bgt, u.acc, .14f)
    }
    val fg = when { kind == "red" -> u.red; pressed -> u.on; kind == "s" -> u.ink; else -> u.acc }
    Box(
        Modifier.fillMaxWidth().padding(start = side, end = side, bottom = 10.dp).clip(u.shape).background(bgc)
            .clickable(interactionSource = src, indication = null, onClick = onClick).padding(15.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(text, 15f, 600, fg) }
}

@Composable
fun SaveBtn(text: String, onSave: () -> Boolean) {
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { if (msg != null) { kotlinx.coroutines.delay(1400); msg = null } }
    Btn(msg ?: text) { if (!onSave()) msg = "Add a name first" }
}

@Composable
fun Chip(t: String, on: Boolean, onClick: () -> Unit) {
    val u = LocalU.current
    Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) u.acc else u.sft).clickable(onClick = onClick)
        .padding(horizontal = 13.dp, vertical = 8.dp)) {
        Tx(t, 13f, 500, if (on) u.on else u.mu)
    }
}

@Composable
fun Lbl(t: String) = Tx(t, 12f, 500, LocalU.current.mu, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 5.dp))

@Composable
fun Toggle(on: Boolean, onClick: () -> Unit) {
    val u = LocalU.current
    val x by animateDpAsState(if (on) 21.dp else 3.dp, label = "knob")
    Box(Modifier.size(46.dp, 28.dp).clip(CircleShape).background(if (on) u.acc else u.ln).clickable(onClick = onClick)) {
        Box(Modifier.offset(x, 3.dp).size(22.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

// ---------- form inputs
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
            textStyle = TextStyle(color = u.ink, fontSize = 15.sp, fontFamily = u.font),
            cursorBrush = SolidColor(u.acc), interactionSource = src, singleLine = !multi,
            keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().then(if (multi) Modifier.heightIn(min = 66.dp) else Modifier)
                        .clip(u.rs(6)).background(u.sft).border(1.5.dp, if (focused) u.acc else Color.Transparent, u.rs(6))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    if (value.isEmpty() && ph.isNotEmpty()) Text(ph, color = u.mu.copy(alpha = .7f), fontSize = 15.sp, fontFamily = u.font)
                    inner()
                }
            },
        )
    }
}

@Composable
fun Upload(label: String, d: Draft, k: String) {
    val u = LocalU.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { d[k] = withContext(Dispatchers.IO) { ImageStore.save(ctx, uri) } }
    }
    Lbl(label)
    Row(Modifier.fillMaxWidth().clip(u.rs(6)).background(u.sft).clickable { pick.launch("image/*") }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(u.ln)) {
            val bmp = rememberBitmap(d[k])
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column {
            Tx(if (d[k].isEmpty()) "Upload image" else "Change image", 14f)
            Tx("PNG or JPG", 12f, c = u.mu)
        }
    }
}

@Composable
fun ColorField(label: String, color: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val u = LocalU.current
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Lbl(label)
        Box(Modifier.fillMaxWidth().height(46.dp).clip(u.rs(6)).background(u.sft).clickable { open = true }.padding(4.dp)) {
            Box(Modifier.fillMaxSize().clip(u.rs(10)).background(hex(color)))
        }
    }
    if (open) ColorDialog(color, { onPick(it); open = false }, { open = false })
}

@Composable
fun ColorDialog(initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val u = LocalU.current
    val c0 = hex(initial)
    var r by remember { mutableFloatStateOf(c0.red) }
    var g by remember { mutableFloatStateOf(c0.green) }
    var b by remember { mutableFloatStateOf(c0.blue) }
    @Composable fun ch(v: Float, c: Color, on: (Float) -> Unit) =
        Slider(v, on, colors = SliderDefaults.colors(thumbColor = c, activeTrackColor = c, inactiveTrackColor = u.ln))
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = u.bg,
        confirmButton = { TextButton({ onDone(Color(r, g, b).toHex()) }) { Text("OK", color = u.acc) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = u.mu) } },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(u.rs(6)).background(Color(r, g, b)))
                Tx(Color(r, g, b).toHex(), 14f, c = u.mu, modifier = Modifier.padding(vertical = 8.dp))
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
        Row(Modifier.fillMaxWidth().clip(u.rs(6)).background(u.sft).clickable { open = true }.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Tx(leagues.find { it.id == d["lid"] }?.name ?: "", 15f, modifier = Modifier.weight(1f))
            Tx("▾", 14f, c = u.mu)
        }
        DropdownMenu(open, { open = false }) {
            leagues.forEach { l -> DropdownMenuItem(text = { Text(l.name) }, onClick = { d["lid"] = l.id; open = false }) }
        }
    }
}
