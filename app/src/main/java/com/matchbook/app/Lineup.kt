package com.matchbook.app

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// =============================================================== formations (ported from the lineup builder mockup)
private val FM = linkedMapOf(
    "4-4-2" to listOf(4, 4, 2), "4-3-3" to listOf(4, 3, 3), "4-2-3-1" to listOf(4, 2, 3, 1), "4-1-4-1" to listOf(4, 1, 4, 1),
    "4-4-1-1" to listOf(4, 4, 1, 1), "4-3-2-1" to listOf(4, 3, 2, 1), "4-5-1" to listOf(4, 5, 1), "4-2-2-2" to listOf(4, 2, 2, 2),
    "4-1-2-1-2" to listOf(4, 1, 2, 1, 2), "3-5-2" to listOf(3, 5, 2), "3-4-3" to listOf(3, 4, 3), "3-4-2-1" to listOf(3, 4, 2, 1),
    "3-4-1-2" to listOf(3, 4, 1, 2), "5-3-2" to listOf(5, 3, 2), "5-4-1" to listOf(5, 4, 1), "5-2-3" to listOf(5, 2, 3),
)
val FORMATIONS: List<String> = FM.keys.toList()
private val SPREAD = mapOf(1 to 0f, 2 to 34f, 3 to 56f, 4 to 72f, 5 to 82f)
private fun mentShift(m: String) = when (m) { "D" -> 7f; "A" -> -7f; else -> 0f }
private fun widthScale(w: String) = when (w) { "N" -> .8f; "W" -> 1.15f; else -> 1f }

class PSlot(val x: Float, val y: Float, val role: String)

private fun roleLabels(r: List<Int>, i: Int): List<String> {
    val c = r[i]; val n = r.size; val k = n - 1 - i
    if (i == 0) return when (c) { 3 -> listOf("CB", "CB", "CB"); 4 -> listOf("LB", "CB", "CB", "RB"); else -> listOf("LWB", "CB", "CB", "CB", "RWB") }
    if (k == 0) return when (c) { 1 -> listOf("ST"); 2 -> listOf("ST", "ST"); else -> listOf("LW", "ST", "RW") }
    if (c == 1) return listOf(if (k == 1) "CAM" else "CDM")
    if (c == 2) return if (k == 1 && n >= 4) listOf("CAM", "CAM") else if (i == 1 && n >= 4) listOf("CDM", "CDM") else listOf("CM", "CM")
    if (c == 3) return if (k == 1 && r[n - 1] == 1) listOf("LW", "CAM", "RW") else listOf("CM", "CM", "CM")
    return if (c == 4) listOf("LM", "CM", "CM", "RM") else listOf("LM", "CM", "CM", "CM", "RM")
}

fun baseSlots(fm: String): List<PSlot> {
    val r = FM[fm] ?: FM.getValue("4-3-3")
    val n = r.size
    val out = ArrayList<PSlot>()
    out.add(PSlot(50f, 91f, "GK"))
    r.forEachIndexed { i, c ->
        val y = 76f - i * (60f / (n - 1))
        val sp = SPREAD[c] ?: 0f
        val labels = roleLabels(r, i)
        for (j in 0 until c) out.add(PSlot(if (c == 1) 50f else 50f - sp / 2f + j * sp / (c - 1), y, labels[j]))
    }
    return out
}

fun lineupSlots(l: Lineup): List<PSlot> {
    val base = baseSlots(l.fm)
    return if (l.px.size == 11 && l.py.size == 11) base.mapIndexed { i, s -> PSlot(l.px[i], l.py[i], s.role) } else base
}

private fun disp(s: PSlot, l: Lineup): Pair<Float, Float> {
    val x = (50f + (s.x - 50f) * widthScale(l.wd)).coerceIn(10f, 90f)
    val y = if (s.role == "GK") s.y else (s.y + mentShift(l.mn)).coerceIn(9f, 93f)
    return x to y
}

private fun roleGroup(role: String) = when (role) {
    "GK" -> "GK"; "LB", "RB", "CB", "LWB", "RWB" -> "DEF"; "CDM", "CM", "CAM", "LM", "RM" -> "MID"; else -> "FWD"
}

private fun slotOf(l: Lineup, key: String): LineSlot = if (key.startsWith("p")) l.xi[key.drop(1).toInt()] else l.bench[key.drop(1).toInt()]

private fun withSlot(l: Lineup, key: String, s: LineSlot): Lineup {
    val i = key.drop(1).toInt()
    return if (key.startsWith("p")) l.copy(xi = l.xi.mapIndexed { n, o -> if (n == i) s else o })
    else l.copy(bench = l.bench.mapIndexed { n, o -> if (n == i) s else o })
}

private fun swapSlots(l: Lineup, a: String, b: String): Lineup {
    val sa = slotOf(l, a); val sb = slotOf(l, b)
    return withSlot(withSlot(l, a, sb), b, sa)
}

private fun fromPlayer(p: Player) = LineSlot(p.id, p.name, if (p.num > 0) p.num.toString() else "", p.img)

private fun autoFill(vm: AppVM, l: Lineup): Lineup {
    val squad = vm.playersOf(l.cid)
    val used = HashSet<String>()
    val slots = lineupSlots(l)
    val xi = slots.mapIndexed { i, s ->
        val g = roleGroup(s.role)
        val p = squad.firstOrNull { it.id !in used && it.pos == g } ?: squad.firstOrNull { it.id !in used }
        if (p != null) { used.add(p.id); fromPlayer(p) } else LineSlot(num = (i + 1).toString())
    }
    val bench = List(7) { i ->
        val p = squad.firstOrNull { it.id !in used }
        if (p != null) { used.add(p.id); fromPlayer(p) } else LineSlot(num = (i + 12).toString())
    }
    return l.copy(xi = xi, bench = bench)
}

private fun styleSummary(l: Lineup): String {
    val tempo = when (l.tempo) { "S" -> "Slow tempo"; "F" -> "Fast tempo"; else -> "Normal tempo" }
    val press = when (l.press) { "L" -> "Low press"; "H" -> "High press"; else -> "Mid press" }
    val line = when (l.line) { "D" -> "Deep line"; "H" -> "High line"; else -> "Normal line" }
    val ment = when (l.mn) { "D" -> "Defensive"; "A" -> "Attacking"; else -> "Balanced" }
    return "$ment · $tempo · $press · $line"
}

private val RunColor = Color.White
private val PassColor = Color(0xFFFFE066)
private val PressColor = Color(0xFFF46C4E)

private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, dashed: Boolean, width: Float) {
    drawLine(color, from, to, strokeWidth = width, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(18f, 12f)) else null)
    val ang = atan2(to.y - from.y, to.x - from.x)
    val len = 16.dp.toPx()
    for (da in listOf(Math.PI.toFloat() + .5f, Math.PI.toFloat() - .5f)) {
        val a = ang + da
        drawLine(color, to, Offset(to.x + len * cos(a), to.y + len * sin(a)), strokeWidth = width, cap = StrokeCap.Round)
    }
}

private fun arrowColor(kind: String) = when (kind) { "pass" -> PassColor; "press" -> PressColor; else -> RunColor }

// =============================================================== tokens
/** One player on the pitch or on the bench. Pitch tokens are moved by the parent; bench tokens follow the finger. */
@Composable
private fun PlayerToken(
    key: String, slot: LineSlot, role: String, gk: Boolean, selected: Boolean, duty: String, translate: Boolean, draggable: Boolean,
    rects: MutableMap<String, Rect>,
    onSelect: () -> Unit, onMove: (Float, Float) -> Unit, onDrop: (Offset) -> Unit, onCancel: () -> Unit,
) {
    val u = LocalU.current
    var acc by remember { mutableStateOf(Offset.Zero) }
    var start by remember { mutableStateOf(Offset.Zero) }
    val label = slot.name.ifBlank { role }
    val tint = if (gk) Color(0xFFF46C4E) else Color(0xFF1D1F24)
    Column(
        Modifier.width(84.dp)
            .then(if (translate) Modifier.graphicsLayer { translationX = acc.x; translationY = acc.y } else Modifier)
            .onGloballyPositioned { rects[key] = it.boundsInRoot() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect)
            .then(
                if (!draggable) Modifier else Modifier.pointerInput(key) {
                    detectDragGestures(
                        onDragStart = { start = rects[key]?.center ?: Offset.Zero; acc = Offset.Zero; onSelect() },
                        onDrag = { change, amount -> change.consume(); acc += amount; onMove(acc.x, acc.y) },
                        onDragEnd = { val g = start + acc; acc = Offset.Zero; onDrop(g) },
                        onDragCancel = { acc = Offset.Zero; onCancel() },
                    )
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.size(54.dp).clip(CircleShape).background(tint)
                    .border(if (selected) 3.dp else 2.dp, if (selected) u.acc else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = rememberBitmap(slot.img)
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                else Tx(slot.num, 18f, 800, Color.White, maxLines = 1)
            }
            if (duty.isNotEmpty()) Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(PressColor).border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Tx(duty, 10f, 800, Color.White) }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Tx(label, 11f, 700, Color(0xFF1B1B1B), maxLines = 1, modifier = Modifier.widthIn(max = 70.dp))
        }
    }
}

// =============================================================== the page
@Composable
fun LineupPage(vm: AppVM, cid: String) {
    val u = LocalU.current
    val c = vm.cl(cid) ?: return
    val l = vm.lineupOf(cid)
    val tactics = vm.lineupTactics
    val rects = remember { HashMap<String, Rect>() }
    val slots = lineupSlots(l)
    val squad = vm.playersOf(cid)
    val sel = vm.lineupSel
    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var live by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }

    fun edit(f: (Lineup) -> Lineup) = vm.editLineup(cid, f)

    TopRow(vm.backLabel, { vm.goBack() }) { CircleBtn("users", { vm.openSquad(cid) }) }
    Tp(if (tactics) "Tactics" else "Lineup", c.name)
    HChips(FORMATIONS.map { it to it }, l.fm) { f -> edit { it.copy(fm = f, px = emptyList(), py = emptyList()) } }

    Column(Modifier.bd(top = 14.dp)) {
        SegRow(listOf("L" to "Lineup", "T" to "Tactics"), if (tactics) "T" else "L") { vm.lineupTactics = it == "T"; vm.lineupSel = "" }
        Spacer(Modifier.height(10.dp))
        Lbl("Mentality")
        SegRow(listOf("D" to "Defensive", "B" to "Balanced", "A" to "Attacking"), l.mn) { m -> edit { it.copy(mn = m) } }
        Lbl("Width")
        SegRow(listOf("N" to "Narrow", "M" to "Normal", "W" to "Wide"), l.wd) { w -> edit { it.copy(wd = w) } }
        Spacer(Modifier.height(16.dp))

        // ---- pitch
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(.74f).clip(RoundedCornerShape(minOf(u.r, 28.dp)))
                .onGloballyPositioned { rects["pitch"] = it.boundsInRoot() }
                .pointerInput(tactics, vm.arrowKind) {
                    if (!tactics) return@pointerInput
                    var s0 = Offset.Zero
                    detectDragGestures(
                        onDragStart = { s0 = it; live = s0 to s0 },
                        onDrag = { change, _ -> change.consume(); live = s0 to change.position },
                        onDragEnd = {
                            val a = live
                            if (a != null && (a.second - a.first).getDistance() > 24f) {
                                val w = size.width.toFloat(); val h = size.height.toFloat()
                                val arrow = Arrow(a.first.x / w * 100f, a.first.y / h * 100f, a.second.x / w * 100f, a.second.y / h * 100f, vm.arrowKind)
                                vm.editLineup(cid) { it.copy(arrows = it.arrows + arrow) }
                            }
                            live = null
                        },
                        onDragCancel = { live = null },
                    )
                },
        ) {
            val boxW = maxWidth
            val boxH = maxHeight
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                drawRect(Color(0xFF2F9E57))
                for (i in 0 until 10 step 2) drawRect(Color(0xFF2A9250), Offset(0f, h / 10f * i), Size(w, h / 10f))
                val line = Color.White.copy(alpha = .85f)
                val sw = 2.dp.toPx(); val inset = 10.dp.toPx()
                drawRect(line, Offset(inset, inset), Size(w - 2 * inset, h - 2 * inset), style = Stroke(sw))
                drawLine(line, Offset(inset, h / 2f), Offset(w - inset, h / 2f), strokeWidth = sw)
                drawCircle(line, w * .12f, Offset(w / 2f, h / 2f), style = Stroke(sw))
                val bw = w * .5f; val bh = h * .15f
                drawRect(line, Offset((w - bw) / 2f, inset), Size(bw, bh), style = Stroke(sw))
                drawRect(line, Offset((w - bw) / 2f, h - inset - bh), Size(bw, bh), style = Stroke(sw))
                val gw = w * .24f; val gh = h * .06f
                drawRect(line, Offset((w - gw) / 2f, inset), Size(gw, gh), style = Stroke(sw))
                drawRect(line, Offset((w - gw) / 2f, h - inset - gh), Size(gw, gh), style = Stroke(sw))
                l.arrows.forEach { a ->
                    drawArrow(Offset(a.x1 / 100f * w, a.y1 / 100f * h), Offset(a.x2 / 100f * w, a.y2 / 100f * h), arrowColor(a.kind), a.kind == "run", if (a.kind == "press") 4.dp.toPx() else 3.dp.toPx())
                }
                live?.let { (a, b) -> drawArrow(a, b, arrowColor(vm.arrowKind), vm.arrowKind == "run", 3.dp.toPx()) }
            }
            slots.forEachIndexed { i, s ->
                val shown = if (i == dragIdx) PSlot(dragX, dragY, s.role) else s
                val (dx, dy) = disp(shown, l)
                val key = "p$i"
                Box(Modifier.offset(x = boxW * (dx / 100f) - 42.dp, y = boxH * (dy / 100f) - 30.dp)) {
                    PlayerToken(
                        key, l.xi[i], s.role, s.role == "GK", sel == key, l.duty[i], translate = false, draggable = !tactics, rects = rects,
                        onSelect = { vm.lineupSel = key },
                        onMove = { mx, my ->
                            val p = rects["pitch"]
                            if (p != null && p.width > 0f) {
                                if (dragIdx != i) { dragIdx = i; dragX = s.x; dragY = s.y }
                                dragX = (s.x + (mx / p.width * 100f) / widthScale(l.wd)).coerceIn(8f, 92f)
                                dragY = (s.y + my / p.height * 100f).coerceIn(8f, 95f)
                            }
                        },
                        onDrop = { g ->
                            val hit = rects.entries.firstOrNull { (k, r) -> k != key && k != "pitch" && r.contains(g) }?.key
                            if (hit != null) {
                                edit { swapSlots(it, key, hit) }
                            } else if (dragIdx == i) {
                                val nx = slots.mapIndexed { n, o -> if (n == i) dragX else o.x }
                                val ny = slots.mapIndexed { n, o -> if (n == i) dragY else o.y }
                                edit { it.copy(px = nx, py = ny) }
                            }
                            dragIdx = -1
                        },
                        onCancel = { dragIdx = -1 },
                    )
                }
            }
        }

        // ---- bench
        H3("Bench")
        l.bench.chunked(4).forEachIndexed { row, group ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                group.forEachIndexed { j, s ->
                    val idx = row * 4 + j
                    val key = "b$idx"
                    PlayerToken(
                        key, s, "Sub ${idx + 1}", false, sel == key, "", translate = true, draggable = !tactics, rects = rects,
                        onSelect = { vm.lineupSel = key }, onMove = { _, _ -> },
                        onDrop = { g ->
                            val hit = rects.entries.firstOrNull { (k, r) -> k != key && k != "pitch" && r.contains(g) }?.key
                            if (hit != null) edit { swapSlots(it, key, hit) }
                        },
                        onCancel = {},
                    )
                }
                repeat(4 - group.size) { Spacer(Modifier.width(84.dp)) }
            }
        }

        // ---- editor for the selected player
        Spacer(Modifier.height(6.dp))
        val validSel = sel.length > 1 && (sel.startsWith("p") || sel.startsWith("b")) && sel.drop(1).toIntOrNull() != null
        Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            if (!validSel) {
                Tx(
                    if (tactics) "Drag on the pitch to draw an arrow (from a player to show a run). Tap a player to give them a duty."
                    else "Tap a player to rename them or pick one from the squad. Drag players to position them. Drop one on another to swap, bench included.",
                    13 * u.fs, 500, u.mu, lh = 13 * u.fs * 1.5f,
                )
            } else {
                val slot = slotOf(l, sel)
                val isPitch = sel.startsWith("p")
                val idx = sel.drop(1).toInt()
                val d = remember(sel, slot.img) { Draft(null, mapOf("img" to slot.img)) }
                val img = d["img"]
                LaunchedEffect(img) { if (img != slot.img) edit { cur -> withSlot(cur, sel, slotOf(cur, sel).copy(img = img)) } }
                Tx(if (isPitch) "Starter · ${slots[idx].role}" else "Bench · Sub ${idx + 1}", 15 * u.fs, 800)
                if (tactics && isPitch) {
                    Lbl("Duty")
                    SegRow(listOf("" to "None", "A" to "Attack", "S" to "Support", "D" to "Defend"), l.duty[idx]) { v ->
                        edit { it.copy(duty = it.duty.mapIndexed { n, o -> if (n == idx) v else o }) }
                    }
                } else {
                    FRow {
                        Fld("Name", slot.name, { v -> edit { cur -> withSlot(cur, sel, slotOf(cur, sel).copy(name = v, pid = "")) } }, Modifier.weight(1.6f), ph = "Player name")
                        Fld("No.", slot.num, { v -> edit { cur -> withSlot(cur, sel, slotOf(cur, sel).copy(num = v)) } }, Modifier.weight(1f), number = true)
                    }
                    if (squad.isNotEmpty()) DropSelect(
                        "Pick from squad", "", squad.map { it.id to (if (it.num > 0) "${it.num}  ${it.name}" else it.name) },
                        { pid -> squad.find { it.id == pid }?.let { p -> edit { cur -> withSlot(cur, sel, fromPlayer(p)) } } },
                    )
                    Upload("Render", d, "img")
                    val others = (l.xi.indices.map { "p$it" } + l.bench.indices.map { "b$it" }).filter { it != sel }
                    DropSelect(
                        "Swap with", "", others.map { k -> k to (slotOf(l, k).name.ifBlank { if (k.startsWith("p")) slots[k.drop(1).toInt()].role else "Sub ${k.drop(1).toInt() + 1}" }) },
                        { k -> edit { swapSlots(it, sel, k) } },
                    )
                    Spacer(Modifier.height(10.dp))
                    Btn("Clear player", "red", 0.dp, 0.dp) {
                        val fresh = LineSlot(num = (if (isPitch) idx + 1 else idx + 12).toString())
                        edit { cur -> withSlot(cur, sel, fresh) }
                    }
                }
            }
        }

        // ---- tactics tools
        if (tactics) {
            Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Tx("Arrows", 15 * u.fs, 800)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("run" to "Run", "pass" to "Pass", "press" to "Press").forEach { (k, t) -> Chip(t, vm.arrowKind == k) { vm.arrowKind = k } }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Undo", false) { edit { it.copy(arrows = it.arrows.dropLast(1)) } }
                    Chip("Clear arrows", false) { edit { it.copy(arrows = emptyList()) } }
                }
            }
            Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Tx("Team instructions", 15 * u.fs, 800)
                Lbl("Tempo")
                SegRow(listOf("S" to "Slow", "N" to "Normal", "F" to "Fast"), l.tempo) { v -> edit { it.copy(tempo = v) } }
                Lbl("Pressing")
                SegRow(listOf("L" to "Low", "M" to "Mid", "H" to "High"), l.press) { v -> edit { it.copy(press = v) } }
                Lbl("Defensive line")
                SegRow(listOf("D" to "Deep", "N" to "Normal", "H" to "High"), l.line) { v -> edit { it.copy(line = v) } }
                Tx(styleSummary(l), 13 * u.fs, 700, u.mu, Modifier.padding(top = 14.dp))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassBtn("Auto-fill", "users", Modifier.weight(1f)) { edit { autoFill(vm, it) } }
            GlassBtn("Reset", "swap", Modifier.weight(1f)) { vm.resetLineup(cid) }
        }
        Spacer(Modifier.height(120.dp))
    }
}
