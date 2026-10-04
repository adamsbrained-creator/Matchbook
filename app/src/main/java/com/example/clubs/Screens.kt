package com.example.clubs

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt

// =============================================================== shell
@Composable
fun ClubsApp(vm: AppVM) {
    val s = vm.settings
    val dark = when (s.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val u = remember(s, dark) { U(s, dark) }
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
    }
    BackHandler(enabled = vm.screen != null) { vm.goBack() }
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    CompositionLocalProvider(LocalU provides u) {
        MaterialTheme {
            Box(Modifier.fillMaxSize().background(u.bgt).windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (land) Landscape(vm) else Portrait(vm)
            }
        }
    }
}

@Composable
fun Portrait(vm: AppVM) {
    Column(Modifier.fillMaxSize()) {
        Section(vm, Modifier.weight(1f).fillMaxWidth())
        NavBar(vm, false)
    }
}

@Composable
fun Landscape(vm: AppVM) {
    val u = LocalU.current
    Row(Modifier.fillMaxSize()) {
        NavBar(vm, true)
        Box(Modifier.width(236.dp).fillMaxHeight().drawBehind {
            drawLine(u.ln, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
        }) {
            key(vm.tab) { Column(Modifier.verticalScroll(rememberScrollState())) { ListV(vm) } }
        }
        if (vm.screen == null) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Tx("Pick a club or league\nto see it here", 14f, c = u.mu, align = TextAlign.Center, lh = 21f)
        } else Section(vm, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
fun Section(vm: AppVM, modifier: Modifier) {
    val sc = vm.screen
    key(vm.tab, sc) {
        Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            if (sc == null) ListV(vm) else Detail(vm, sc)
        }
    }
}

@Composable
fun NavBar(vm: AppVM, land: Boolean) {
    val u = LocalU.current
    val float = u.s.navs == "float"
    val labels = u.s.lbl == "on"
    val outerR = u.r + if (land) 10.dp else 14.dp
    val shape = RoundedCornerShape(outerR)
    // Float bar has 5dp inner padding, so the selected pill's radius = outer radius - 5dp (concentric corners)
    val pill = if (float) RoundedCornerShape(maxOf(0.dp, outerR - 5.dp)) else u.rs(4)
    val items = listOf("clubs" to "shield", "leagues" to "trophy", "add" to "plus", "stats" to "bars")
    val base = if (land) Modifier.fillMaxHeight() else Modifier.fillMaxWidth()
    val mod = if (float) {
        (if (land) base.padding(start = 10.dp, top = 10.dp, bottom = 10.dp).width(68.dp) else base.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(.12f), spotColor = Color.Black.copy(.12f))
            .border(1.dp, u.ln, shape).background(u.bgt, shape)
            .padding(if (land) PaddingValues(horizontal = 5.dp, vertical = 10.dp) else PaddingValues(5.dp))
    } else {
        (if (land) base.width(68.dp) else base).background(u.bgt).drawBehind {
            if (land) drawLine(u.ln, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
            else drawLine(u.ln, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.padding(if (land) PaddingValues(horizontal = 5.dp, vertical = 10.dp) else PaddingValues(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 10.dp))
    }
    @Composable fun item(k: String, ic: String, m: Modifier) {
        val on = vm.tab == k
        val col = if (on) u.acc else u.mu
        Column(
            m.clip(pill).background(if (on) lerp(u.bg, u.acc, .13f) else Color.Transparent).clickable { vm.selectTab(k) }
                .padding(horizontal = if (labels) 2.dp else 14.dp, vertical = if (labels) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Ico(ic, 22.dp, col, 1.5f)
            if (labels) Tx(TN[k] ?: "", 11f, 500, col, maxLines = 1)
        }
    }
    if (land) Column(mod, verticalArrangement = Arrangement.spacedBy(4.dp)) { items.forEach { (k, i) -> item(k, i, Modifier.fillMaxWidth()) } }
    else Row(mod, horizontalArrangement = Arrangement.spacedBy(4.dp)) { items.forEach { (k, i) -> item(k, i, Modifier.weight(1f)) } }
}

@Composable
fun Detail(vm: AppVM, sc: Screen) {
    when (sc) {
        is Screen.ClubS -> ClubDetail(vm, sc.id)
        is Screen.LeagueS -> LeagueDetail(vm, sc.id)
        Screen.SettingsS -> SettingsScreen(vm)
        is Screen.FormL -> LeagueForm(vm, sc.draft)
        is Screen.FormC -> ClubForm(vm, sc.draft)
    }
}

// =============================================================== lists
@Composable
fun ClubRow(vm: AppVM, c: Club, modifier: Modifier = Modifier, plain: Boolean = false) {
    val u = LocalU.current
    val m = if (plain) modifier.fillMaxWidth()
    else modifier.fillMaxWidth().clip(u.shape).background(u.sft).clickable { vm.openClub(c.id) }.padding(horizontal = 14.dp, vertical = u.pad)
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Crest(c.color, c.c2, c.logo, ab(c.name, c.short))
        Column(Modifier.weight(1f)) {
            Tx(vm.nm(c), 15 * u.fs, 700, maxLines = 1)
            Tx(vm.lg(c.lid)?.name ?: "", 12 * u.fs, c = u.mu)
        }
        Tx(vm.pts(c).toString(), 17f, 700)
    }
}

@Composable
fun ClubTile(vm: AppVM, c: Club, modifier: Modifier) {
    val u = LocalU.current
    Column(
        modifier.clip(u.shape).background(u.sft).clickable { vm.openClub(c.id) }.padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Crest(c.color, c.c2, c.logo, ab(c.name, c.short))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Tx(vm.nm(c), 15 * u.fs, 700, align = TextAlign.Center)
            Tx(vm.lg(c.lid)?.name ?: "", 12 * u.fs, c = u.mu, align = TextAlign.Center)
        }
        Tx(vm.pts(c).toString(), 13f, 700, u.mu)
    }
}

@Composable
fun LeagueItem(vm: AppVM, l: League) {
    val u = LocalU.current
    val n = vm.clubs.count { it.lid == l.id }
    val body: @Composable RowScope.() -> Unit = {
        Crest(l.color, "", l.logo, ab(l.name, ""))
        Column(Modifier.weight(1f)) {
            Tx(l.name, 15 * u.fs, 700, maxLines = 1)
            Tx("${l.country}, $n clubs", 12 * u.fs, c = u.mu)
        }
    }
    if (u.s.lay == "cards") {
        Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(u.shape).background(u.sft).clickable { vm.openLeague(l.id) }) {
            val bmp = rememberBitmap(l.banner)
            Box(Modifier.fillMaxWidth().height(76.dp).background(Brush.linearGradient(listOf(hex(l.color), lerp(Color.Black, hex(l.color), .6f))))) {
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Row(Modifier.padding(horizontal = 14.dp, vertical = u.pad), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) { body() }
        }
    } else {
        Row(Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(u.shape).background(u.sft).clickable { vm.openLeague(l.id) }
            .padding(horizontal = 14.dp, vertical = u.pad), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) { body() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListV(vm: AppVM) {
    val u = LocalU.current
    when (vm.tab) {
        "clubs" -> {
            TopBar("Clubs", true) { vm.openSettings() }
            Column(Modifier.bd()) {
                if (vm.clubs.isEmpty()) Empty("No clubs yet.\nOpen Add to create one.")
                else if (u.s.cl == "grid") vm.clubs.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { ClubTile(vm, it, Modifier.weight(1f).fillMaxHeight()) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                } else vm.clubs.forEach { ClubRow(vm, it, Modifier.padding(bottom = 10.dp)) }
            }
        }
        "leagues" -> {
            TopBar("Leagues", true) { vm.openSettings() }
            Column(Modifier.bd()) {
                if (vm.leagues.isEmpty()) Empty("No leagues yet.\nOpen Add to create one.")
                else vm.leagues.forEach { LeagueItem(vm, it) }
            }
        }
        "add" -> {
            TopBar("Add", true) { vm.openSettings() }
            Column(Modifier.bd()) {
                @Composable fun opt(t: String, sub: String, go: () -> Unit) =
                    Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(u.shape).background(u.sft).clickable(onClick = go)
                        .padding(horizontal = 18.dp, vertical = 20.dp)) {
                        Tx(t, 18f, 700)
                        Spacer(Modifier.height(4.dp))
                        Tx(sub, 14f, c = u.mu)
                    }
                opt("New club", "Crest, colors, stadium, stats and history") { vm.newClub() }
                opt("New league", "Logo, banner, season and points rules") { vm.newLeague() }
            }
        }
        else -> {
            TopBar("Stats", true) { vm.openSettings() }
            val cs = vm.clubs.filter { vm.statsLeague == "all" || it.lid == vm.statsLeague }
            val g = cs.sumOf { it.gf }
            val m = (cs.sumOf { it.played() } / 2f).roundToInt()
            Column(Modifier.bd()) {
                FlowRow(Modifier.padding(top = 4.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("All", vm.statsLeague == "all") { vm.statsLeague = "all" }
                    vm.leagues.forEach { l -> Chip(l.name, vm.statsLeague == l.id) { vm.statsLeague = l.id } }
                }
                if (cs.isEmpty()) Empty("No clubs here yet.", 40.dp)
                else {
                    KvPanel(listOf("Clubs" to "${cs.size}", "Matches played" to "$m", "Goals scored" to "$g",
                        "Goals per match" to (if (m > 0) "%.2f".format(g.toFloat() / m) else "0")), side = 0.dp)
                    Top(vm, "Most points", cs, -1) { vm.pts(it) }
                    Top(vm, "Best attack", cs, -1) { it.gf }
                    Top(vm, "Best defence", cs, 1) { it.ga }
                    Top(vm, "Most wins", cs, -1) { it.w }
                }
            }
        }
    }
}

@Composable
fun Top(vm: AppVM, title: String, cs: List<Club>, dir: Int, fn: (Club) -> Int) {
    val u = LocalU.current
    H3(title)
    val rows = cs.sortedBy { dir * fn(it) }.take(3)
    Panel(side = 0.dp) {
        rows.forEachIndexed { i, c ->
            KvRow(i == rows.lastIndex, Modifier.clickable { vm.openClub(c.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Crest(c.color, c.c2, c.logo, ab(c.name, c.short))
                    Tx(vm.nm(c), 14 * u.fs)
                }
                Tx(fn(c).toString(), 14 * u.fs, 700)
            }
        }
    }
}

// =============================================================== details
@Composable
fun Hero(color: String, title: String, sub: String, crest: @Composable () -> Unit) {
    val u = LocalU.current
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to hex(color).copy(alpha = .15f), .9f to Color.Transparent, 1f to Color.Transparent))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        crest()
        Tx(title, 27 * u.fs, u.tw, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp), align = TextAlign.Center)
        Tx(sub, 14 * u.fs, c = u.mu, align = TextAlign.Center)
    }
}

@Composable
fun TextBlock(title: String, body: String) {
    val u = LocalU.current
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
        H3(title)
        Tx(body, 14f, c = u.mu, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp), lh = 14 * 1.55f)
    }
}

@Composable
fun ClubDetail(vm: AppVM, id: String) {
    val c = vm.cl(id) ?: return
    val F = LocalU.current.s.fields
    val l = vm.lg(c.lid)
    BackBtn(vm.back?.label ?: "Back") { vm.goBack() }
    Hero(c.color, c.name, listOf(c.city, if (c.founded.isNotBlank()) "founded ${c.founded}" else "").filter { it.isNotBlank() }.joinToString(", ")) {
        Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 112.dp, true)
    }
    KvPanel(listOfNotNull(
        "League" to (l?.name ?: ""),
        if ("stadium" in F) "Stadium" to c.stadium else null,
        if ("colours" in F) "Colours" to "${c.color} / ${c.c2}" else null,
        if ("titles" in F) "League titles" to "${c.titles}" else null,
        if ("points" in F) "Points" to "${vm.pts(c)}" else null,
    ))
    Btn("Edit club") { vm.editClub(c.id) }
    Spacer(Modifier.height(16.dp))
    KvPanel(listOfNotNull(
        if ("nick" in F) "Nickname" to c.nick else null,
        if ("mgr" in F) "Manager" to c.mgr else null,
        if ("chair" in F) "Chairman" to c.chair else null,
        if ("cap" in F) "Capacity" to (if (c.cap > 0) "%,d".format(c.cap) else "") else null,
        if ("played" in F) "Played" to "${c.played()}" else null,
        if ("rec" in F) "Won / drawn / lost" to "${c.w} / ${c.d} / ${c.l}" else null,
        if ("goals" in F) "Goals" to "${c.gf} : ${c.ga}" else null,
    ))
    if (c.hon.isNotBlank() && "hon" in F) TextBlock("Honours", c.hon)
    if (c.info.isNotBlank() && "about" in F) TextBlock("About", c.info)
}

@Composable
private fun TRow(cells: List<String>, head: Boolean, edge: Color?, last: Boolean, club: Club?, vm: AppVM) {
    val u = LocalU.current
    val sz = if (head) 11f else 13 * u.fs
    val mu = u.mu
    Row(
        Modifier.fillMaxWidth()
            .then(if (last) Modifier else Modifier.drawBehind { drawLine(u.ln, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) })
            .then(if (club != null) Modifier.clickable { vm.openClub(club.id) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).drawBehind { if (edge != null) drawRect(edge, size = Size(3.dp.toPx(), size.height)) }.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center) { Tx(cells[0], sz, if (head) 500 else 700, if (head) mu else u.ink) }
        Row(Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (club != null) Box(Modifier.padding(end = 7.dp).size(10.dp).clip(CircleShape).background(hex(club.color)))
            Tx(cells[1], sz, if (head) 500 else 400, if (head) mu else u.ink, maxLines = 1)
        }
        for (i in 2 until cells.size) {
            val lastCol = i == cells.lastIndex
            Box(Modifier.width(if (lastCol) 34.dp else 30.dp).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Tx(cells[i], sz, if (head) 500 else if (lastCol) 700 else 400, if (head) mu else u.ink)
            }
        }
    }
}

@Composable
fun LeagueDetail(vm: AppVM, id: String) {
    val u = LocalU.current
    val l = vm.lg(id) ?: return
    val C = u.s.cols
    val cs = vm.clubs.filter { it.lid == id }.sortedWith(compareByDescending<Club> { vm.pts(it) }.thenByDescending { it.gf - it.ga })
    BackBtn(vm.back?.label ?: "Back") { vm.goBack() }
    Hero(l.color, l.name, "${l.country}, ${l.season}") { Crest(l.color, "", l.logo, ab(l.name, ""), 112.dp, true) }
    val banner = rememberBitmap(l.banner)
    if (banner != null) Image(banner, null, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().height(84.dp).clip(u.rs(6)), contentScale = ContentScale.Crop)
    KvPanel(listOf("Country" to l.country, "Season" to l.season, "Clubs" to "${cs.size}", "Points per win" to "${l.pw}",
        "Points per draw" to "${l.pd}", "Promotion spots" to "${l.promo}", "Relegation spots" to "${l.rel}"))
    Panel(h = 14.dp, v = u.pad) {
        if (cs.isEmpty()) Empty("No clubs yet.", 30.dp)
        else {
            fun cells(first: String, second: String, p: String, w: String, d: String, ll: String, gd: String, pts: String) =
                listOf(first, second, p) + listOfNotNull(
                    if ("w" in C) w else null, if ("d" in C) d else null, if ("l" in C) ll else null, if ("gd" in C) gd else null,
                ) + pts
            TRow(cells("#", "Club", "P", "W", "D", "L", "GD", "Pts"), true, null, false, null, vm)
            cs.forEachIndexed { i, c ->
                val edge = when {
                    i < l.promo -> u.acc
                    l.rel > 0 && i >= cs.size - l.rel -> u.red
                    else -> null
                }
                TRow(cells("${i + 1}", vm.nm(c), "${c.played()}", "${c.w}", "${c.d}", "${c.l}", "${c.gf - c.ga}", "${vm.pts(c)}"),
                    false, edge, i == cs.lastIndex, c, vm)
            }
        }
    }
    Btn("Add club") { vm.newClub(l.id) }
    Btn("Edit league", "s") { vm.editLeague(l.id) }
}

// =============================================================== forms
@Composable
fun FRow(gap: Int = 12, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp), content = content)

@Composable
fun LeagueForm(vm: AppVM, d: Draft) {
    val ed = d.eid != null
    BackBtn(vm.back?.label ?: "Back") { vm.goBack() }
    TopBar(if (ed) "Edit league" else "New league", false)
    Column(Modifier.bd()) {
        Fld("League name", d["name"], { d["name"] = it }, ph = "Premier League")
        FRow { Fld("Country", d["country"], { d["country"] = it }, Modifier.weight(1f)); Fld("Season", d["season"], { d["season"] = it }, Modifier.weight(1f), ph = "2026/27") }
        Upload("Logo", d, "logo")
        Upload("Banner", d, "banner")
        ColorField("League color", d["color"]) { d["color"] = it }
        FRow { Fld("Points per win", d["pw"], { d["pw"] = it }, Modifier.weight(1f), number = true); Fld("Points per draw", d["pd"], { d["pd"] = it }, Modifier.weight(1f), number 