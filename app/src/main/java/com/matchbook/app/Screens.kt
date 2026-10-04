package com.matchbook.app

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt

// =============================================================== shell
/** Loads res/font/<name>.ttf if you add it (anton, inter, nunito); otherwise uses the fallback. */
@Composable
private fun rememberFont(res: String, fallback: FontFamily): FontFamily {
    val ctx = LocalContext.current
    return remember(res) {
        val id = ctx.resources.getIdentifier(res, "font", ctx.packageName)
        if (id != 0) FontFamily(Font(id)) else fallback
    }
}

fun pageHex(vm: AppVM, sc: Screen?): String {
    val c: String? = when (sc) {
        is Screen.ClubS -> vm.cl(sc.id)?.color
        is Screen.LeagueS -> vm.lg(sc.id)?.color
        is Screen.FormC -> sc.draft["color"]
        is Screen.FormL -> sc.draft["color"]
        else -> null
    }
    return c ?: vm.pickColor()
}

@Composable
fun MatchbookApp(vm: AppVM) {
    val s = vm.settings
    val dark = when (s.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val anton = rememberFont("anton", FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD)))
    val inter = rememberFont("inter", FontFamily.Default)
    val nunito = rememberFont("nunito", FontFamily.SansSerif)
    val u = remember(s, dark, anton, inter, nunito) { U(s, dark, anton, inter, nunito) }
    val cfg = LocalConfiguration.current
    val land = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && cfg.screenWidthDp >= 640

    // Status bar follows the page header: light icons on dark glow, dark icons on light glow
    val hdr = u.hdrFor(pageC(if (land) vm.pickColor() else pageHex(vm, vm.screen)))
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = !hdr.light
            isAppearanceLightNavigationBars = !dark
        }
    }
    BackHandler(enabled = vm.screen != null) { vm.goBack() }

    CompositionLocalProvider(LocalU provides u) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Box(Modifier.fillMaxSize().background(u.bg), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.widthIn(max = 1180.dp).fillMaxWidth().fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .imePadding(),
                ) { if (land) Landscape(vm) else Portrait(vm) }
            }
        }
    }
}

@Composable
fun Portrait(vm: AppVM) {
    val u = LocalU.current
    val sc = vm.screen
    val form = sc is Screen.FormL || sc is Screen.FormC
    val float = u.s.navs == "float"
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SectionPane(vm, Modifier.fillMaxSize())
            if (float && !form) Box(Modifier.align(Alignment.BottomCenter)) { NavBar(vm, false) }
        }
        if (!float && !form) NavBar(vm, false)
    }
}

@Composable
fun Landscape(vm: AppVM) {
    val u = LocalU.current
    Row(Modifier.fillMaxSize()) {
        NavBar(vm, true)
        Box(Modifier.width(300.dp).fillMaxHeight().drawBehind {
            drawLine(u.ln, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
        }) {
            key(vm.tab) { Page(vm.pickColor(), false, Modifier.fillMaxSize()) { ListV(vm) } }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (vm.screen == null) Page(vm.pickColor(), false, Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = 140.dp), contentAlignment = Alignment.Center) {
                    Tx("Pick a club or league\nto see it here", 14 * u.fs, c = u.mu, align = TextAlign.Center, lh = 14 * u.fs * 1.6f)
                }
            } else SectionPane(vm, Modifier.fillMaxSize())
        }
    }
}

/** A scrolling page whose top is covered by the colour glow (it also sits under the status bar). */
@Composable
fun Page(
    colorHex: String?, big: Boolean, modifier: Modifier,
    overlay: @Composable BoxScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit,
) {
    val u = LocalU.current
    val c = remember(colorHex) { pageC(colorHex) }
    val hdr = remember(u, c) { u.hdrFor(c) }
    val stops = remember(u, c) { u.glow(c) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val hh = (if (big) 440.dp else 230.dp) + top
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(hh)
                    .background(Brush.verticalGradient(0f to stops[0], .36f to stops[1], .72f to stops[2], 1f to stops[3])))
                CompositionLocalProvider(LocalHdr provides hdr) {
                    Column(Modifier.fillMaxWidth().padding(top = top), content = content)
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        overlay()
    }
}

@Composable
fun SectionPane(vm: AppVM, modifier: Modifier) {
    val sc = vm.screen
    val big = sc is Screen.ClubS || sc is Screen.LeagueS
    key(vm.tab, sc) {
        Page(pageHex(vm, sc), big, modifier, overlay = {
            when (sc) {
                is Screen.FormL -> Box(Modifier.align(Alignment.BottomCenter)) { SaveBar("Save league", { vm.goBack() }) { vm.saveLeague(sc.draft) } }
                is Screen.FormC -> Box(Modifier.align(Alignment.BottomCenter)) { SaveBar("Save club", { vm.goBack() }) { vm.saveClub(sc.draft) } }
                else -> {}
            }
        }) { if (sc == null) ListV(vm) else Detail(vm, sc) }
    }
}

@Composable
fun SaveBar(label: String, onCancel: () -> Unit, onSave: () -> Boolean) {
    val u = LocalU.current
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, .3f to u.bg, 1f to u.bg))
            .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) { Btn("Cancel", "s", 0.dp, 0.dp) { onCancel() } }
        Box(Modifier.weight(1f)) { SaveBtn(label, 0.dp, onSave) }
    }
}

// =============================================================== navigation
@Composable
fun NavBar(vm: AppVM, land: Boolean) {
    val u = LocalU.current
    val labels = u.s.lbl == "on"
    val items = listOf("clubs" to "shield", "leagues" to "trophy", "add" to "plus", "stats" to "bars")
    val cfgOn = vm.screen is Screen.SettingsS

    @Composable
    fun pill(on: Boolean, ic: String, label: String?, wide: Boolean, onClick: () -> Unit) {
        Row(
            Modifier.height(48.dp).widthIn(min = 48.dp).clip(CircleShape).background(if (on) u.ink else u.sf)
                .border(1.dp, u.ln, CircleShape).clickable(onClick = onClick).padding(horizontal = if (on && wide) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Ico(ic, 21.dp, if (on) u.bg else u.ink, 1.7f)
            if (on && wide && labels && label != null) Tx(label, 13f, 600, u.bg, maxLines = 1)
        }
    }

    if (land) {
        Column(
            Modifier.fillMaxHeight().background(u.bg)
                .drawBehind { drawLine(u.ln, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { (k, ic) -> pill(vm.tab == k && !cfgOn, ic, TN[k], false) { vm.selectTab(k) } }
            Spacer(Modifier.weight(1f))
            pill(cfgOn, "gear", null, false) { vm.openSettings() }
        }
    } else {
        val bar = u.s.navs == "bar"
        Row(
            Modifier.fillMaxWidth()
                .then(if (bar) Modifier.background(u.bg).drawBehind { drawLine(u.ln, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 12.dp, top = if (bar) 8.dp else 0.dp, bottom = if (bar) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEach { (k, ic) -> pill(vm.tab == k && !cfgOn, ic, TN[k], true) { vm.selectTab(k) } }
            Spacer(Modifier.weight(1f))
            pill(cfgOn, "gear", null, false) { vm.openSettings() }
        }
    }
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

// =============================================================== header pieces
@Composable
fun Tp(title: String, sub: String, trailing: (@Composable () -> Unit)? = null) {
    val u = LocalU.current
    val h = LocalHdr.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Tx(sub, 14 * u.fs * .86f, c = h.fg.copy(alpha = .85f), modifier = Modifier.padding(bottom = 3.dp))
            Hd(if (u.sans) title.uppercase() else title, if (u.sans) 38f else 30f, h.fg, w = if (u.sans) 400 else 800)
        }
        trailing?.invoke()
    }
}

@Composable
fun SearchBtn(vm: AppVM) {
    val h = LocalHdr.current
    Box(
        Modifier.padding(top = 6.dp).size(40.dp).clip(CircleShape).background(if (vm.searchOpen) h.chipOn else h.chip)
            .clickable { vm.toggleSearch() },
        contentAlignment = Alignment.Center,
    ) { Ico("search", 22.dp, if (vm.searchOpen) h.chipOnFg else h.fg, 1.8f) }
}

@Composable
fun BackPill(label: String, onClick: () -> Unit) {
    val h = LocalHdr.current
    Row(
        Modifier.padding(start = 14.dp, top = 14.dp).clip(CircleShape).background(h.chip).clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Ico("back", 18.dp, h.fg, 2f)
        Tx(label, 13f, 500, h.fg)
    }
}

@Composable
fun Ctr(title: String, sub: String, pill: String, crest: @Composable () -> Unit) {
    val u = LocalU.current
    val h = LocalHdr.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        crest()
        Spacer(Modifier.height(14.dp))
        Hd(if (u.sans) title.uppercase() else title, if (u.sans) 36f else 30f, h.fg, align = TextAlign.Center, w = if (u.sans) 400 else 800)
        if (sub.isNotBlank()) Tx(sub, 14 * u.fs, c = h.fg.copy(alpha = .88f), modifier = Modifier.padding(top = 6.dp), align = TextAlign.Center)
        Row(
            Modifier.padding(top = 12.dp).clip(CircleShape).background(h.chip).border(1.dp, h.pillBorder, CircleShape)
                .padding(horizontal = 15.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(h.fg))
            Tx(pill, 14 * u.fs * .88f, 500, h.fg)
        }
    }
}

// =============================================================== lists
@Composable
fun ClubRow(vm: AppVM, c: Club, modifier: Modifier = Modifier, plain: Boolean = false) {
    val u = LocalU.current
    val m = if (plain) modifier.fillMaxWidth()
    else modifier.fillMaxWidth().clip(u.shape).background(u.sf).border(1.dp, u.ln, u.shape).clickable { vm.openClub(c.id) }
        .padding(horizontal = 14.dp, vertical = u.pad)
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Crest(c.color, c.c2, c.logo, ab(c.name, c.short), backdrop = c.logoBg)
        Column(Modifier.weight(1f)) {
            Tx(vm.nm(c), 14 * u.fs * 1.07f, 600, maxLines = 1)
            Tx(vm.lg(c.lid)?.name ?: "", 14 * u.fs * .86f, c = u.mu)
        }
        Hd("${vm.pts(c)}", 22f, u.mu)
    }
}

@Composable
fun ClubTile(vm: AppVM, c: Club, modifier: Modifier) {
    val u = LocalU.current
    Column(
        modifier.clip(u.shape).background(u.sf).border(1.dp, u.ln, u.shape).clickable { vm.openClub(c.id) }.padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Crest(c.color, c.c2, c.logo, ab(c.name, c.short), backdrop = c.logoBg)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Tx(vm.nm(c), 14 * u.fs * 1.07f, 600, align = TextAlign.Center)
            Tx(vm.lg(c.lid)?.name ?: "", 14 * u.fs * .86f, c = u.mu, align = TextAlign.Center)
        }
        Hd("${vm.pts(c)}", 22f, u.mu)
    }
}

@Composable
fun LeagueItem(vm: AppVM, l: League) {
    val u = LocalU.current
    val n = vm.clubs.count { it.lid == l.id }
    val sub = "${flag(l.country)}${l.country}${if (l.country.isNotEmpty()) ", " else ""}$n clubs"
    val body: @Composable RowScope.() -> Unit = {
        Crest(l.color, "", l.logo, ab(l.name, ""))
        Column(Modifier.weight(1f)) {
            Tx(l.name, 14 * u.fs * 1.07f, 600, maxLines = 1)
            Tx(sub, 14 * u.fs * .86f, c = u.mu)
        }
    }
    if (u.s.lay == "cards") {
        Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(u.shape).background(u.sf).border(1.dp, u.ln, u.shape).clickable { vm.openLeague(l.id) }) {
            val bmp = rememberBitmap(l.banner)
            val c = hex(l.color)
            Box(Modifier.fillMaxWidth().height(84.dp).background(Brush.linearGradient(listOf(c, lerp(Color.Black, c, .55f))))) {
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Row(Modifier.padding(horizontal = 14.dp, vertical = u.pad), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) { body() }
        }
    } else {
        Row(Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(u.shape).background(u.sf).border(1.dp, u.ln, u.shape).clickable { vm.openLeague(l.id) }
            .padding(horizontal = 14.dp, vertical = u.pad), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) { body() }
    }
}

@Composable
fun ListV(vm: AppVM) {
    val u = LocalU.current
    when (vm.tab) {
        "clubs" -> {
            Tp("Clubs", "${vm.clubs.size} clubs, ${vm.leagues.size} leagues", { SearchBtn(vm) })
            if (vm.searchOpen) SearchField(vm.query, "Search clubs", { vm.query = it }) { vm.toggleSearch() }
            HChips(listOf("all" to "All") + vm.leagues.map { it.id to it.name }, vm.clubFilter) { vm.clubFilter = it }
            val shown = vm.shownClubs()
            Column(Modifier.bd()) {
                if (shown.isEmpty()) EmptyBlock(if (vm.query.isNotBlank()) "No clubs match \"${vm.query.trim()}\"." else "No clubs here yet.") {
                    if (vm.query.isBlank()) {
                        SmallBtn("Add a club") { vm.newClub() }
                        SmallBtn("Load sample data", "s") { vm.loadSample() }
                    }
                }
                else if (u.s.cl == "grid") shown.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { ClubTile(vm, it, Modifier.weight(1f).fillMaxHeight()) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                } else shown.forEach { ClubRow(vm, it, Modifier.padding(bottom = 10.dp)) }
            }
        }
        "leagues" -> {
            Tp("Leagues", "Your competitions", { SearchBtn(vm) })
            if (vm.searchOpen) SearchField(vm.query, "Search leagues", { vm.query = it }) { vm.toggleSearch() }
            val shown = vm.shownLeagues()
            Column(Modifier.bd(top = 18.dp)) {
                if (shown.isEmpty()) EmptyBlock(if (vm.query.isNotBlank()) "No leagues match \"${vm.query.trim()}\"." else "No leagues yet.") {
                    if (vm.query.isBlank()) {
                        SmallBtn("Create a league") { vm.newLeague() }
                        SmallBtn("Load sample data", "s") { vm.loadSample() }
                    }
                }
                else shown.forEach { LeagueItem(vm, it) }
            }
        }
        "add" -> {
            Tp("Add", "Create something new")
            Column(Modifier.bd(top = 20.dp)) {
                @Composable
                fun opt(t: String, sub: String, go: () -> Unit) =
                    Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().clip(u.shape).background(u.sf).border(1.dp, u.ln, u.shape)
                        .clickable(onClick = go).padding(horizontal = 18.dp, vertical = 20.dp)) {
                        Tx(t, 14 * u.fs * 1.2f, 600)
                        Spacer(Modifier.height(4.dp))
                        Tx(sub, 14 * u.fs, c = u.mu)
                    }
                opt("New club", "Crest, colors, stadium, stats and history") { vm.newClub() }
                opt("New league", "Logo, banner, season and points rules") { vm.newLeague() }
            }
        }
        else -> {
            Tp("Stats", "Season overview")
            HChips(listOf("all" to "All") + vm.leagues.map { it.id to it.name }, vm.statsLeague) { vm.statsLeague = it }
            val cs = vm.clubs.filter { vm.statsLeague == "all" || it.lid == vm.statsLeague }
            val g = cs.sumOf { it.gf }
            val m = (cs.sumOf { it.played() } / 2f).roundToInt()
            Column(Modifier.bd()) {
                if (cs.isEmpty()) EmptyBlock("No clubs to show yet.")
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
                    Crest(c.color, c.c2, c.logo, ab(c.name, c.short), backdrop = c.logoBg)
                    Tx(vm.nm(c), 14 * u.fs)
                }
                Tx("${fn(c)}", 14 * u.fs, 600)
            }
        }
    }
}

// =============================================================== details
private class Stat(val key: String, val label: String, val field: String, val v: (Club) -> Int)

private val STATS = listOf(
    Stat("P", "Played", "played") { it.played() },
    Stat("W", "Won", "rec") { it.w },
    Stat("D", "Drawn", "rec") { it.d },
    Stat("L", "Lost", "rec") { it.l },
    Stat("GF", "Goals for", "goals") { it.gf },
    Stat("GA", "Goals against", "goals") { it.ga },
    Stat("GD", "Goal difference", "goals") { it.gf - it.ga },
)

private fun ord(n: Int) = n.toString() + (listOf("st", "nd", "rd").getOrNull(n - 1) ?: "th")

@Composable
fun TextBlock(title: String, body: String) {
    val u = LocalU.current
    H3(title, 22.dp)
    Tx(body, 14 * u.fs, c = u.mu, modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp), lh = 14 * u.fs * 1.55f)
}

@Composable
fun ClubDetail(vm: AppVM, id: String) {
    val u = LocalU.current
    val h = LocalHdr.current
    val c = vm.cl(id) ?: return
    val l = vm.lg(c.lid)
    val F = u.s.fields
    val enabled = STATS.filter { it.field in F }
    val sel = STATS.getOrNull(vm.st)?.takeIf { it.field in F } ?: enabled.firstOrNull() ?: STATS[1]
    val sub = listOf(c.city, if (c.founded.isNotBlank()) "founded ${c.founded}" else "").filter { it.isNotBlank() }.joinToString(" · ")

    BackPill(vm.back?.label ?: "Back") { vm.goBack() }
    Ctr(c.name, sub, "Rank ${ord(vm.rank(c))}${if ("points" in F) " · ${vm.pts(c)} pts" else ""}") {
        Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 112.dp, true, backdrop = c.logoBg)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 12.dp)) {
        val n = maxOf(enabled.size, 1)
        val w = minOf(52.dp, (maxWidth - 2.dp * (n - 1)) / n)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
            enabled.forEach { s ->
                val on = s === sel
                Column(
                    Modifier.width(w).clip(RoundedCornerShape(24.dp)).background(if (on) h.fg.copy(alpha = .22f) else Color.Transparent)
                        .clickable { vm.st = STATS.indexOf(s) }.padding(top = 6.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Tx(s.key, 14 * u.fs * .8f, c = h.fg.copy(alpha = .8f))
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (on) h.chipOn else Color.Transparent), contentAlignment = Alignment.Center) {
                        Tx("${s.v(c)}", 14 * u.fs * .92f, 600, if (on) h.chipOnFg else h.fg)
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Hd("${sel.v(c)}", 46f, h.fg)
        Tx(sel.label, 14 * u.fs * .85f, c = h.fg.copy(alpha = .85f))
    }
    Spacer(Modifier.height(26.dp))
    KvPanel(listOf(
        "League" to (l?.name ?: ""),
        "Stadium" to if ("stadium" in F) c.stadium else "",
        "Colours" to if ("colours" in F) "${c.color} / ${c.c2}" else "",
        "League titles" to if ("titles" in F) "${c.titles}" else "",
        "Nickname" to if ("nick" in F) c.nick else "",
        "Manager" to if ("mgr" in F) c.mgr else "",
        "Chairman" to if ("chair" in F) c.chair else "",
        "Capacity" to if ("cap" in F && c.cap > 0) "%,d".format(c.cap) else "",
    ))
    if (c.hon.isNotBlank() && "hon" in F) TextBlock("Honours", c.hon)
    if (c.info.isNotBlank() && "about" in F) TextBlock("About", c.info)
    Btn("Edit club", "s") { vm.editClub(c.id) }
    Spacer(Modifier.height(100.dp))
}

@Composable
private fun TRow(cells: List<String>, head: Boolean, edge: Color?, last: Boolean, club: Club?, vm: AppVM) {
    val u = LocalU.current
    val sz = if (head) 14 * u.fs * .85f else 14 * u.fs * .93f
    Row(
        Modifier.fillMaxWidth()
            .then(if (last) Modifier else Modifier.drawBehind { drawLine(u.ln, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) })
            .then(if (club != null) Modifier.clickable { vm.openClub(club.id) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp).drawBehind { if (edge != null) drawRect(edge, size = Size(3.dp.toPx(), size.height)) }.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center) { Tx(cells[0], sz, if (head) 500 else 600, if (head) u.mu else u.ink) }
        Row(Modifier.weight(1f).padding(horizontal = 3.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (club != null) Box(Modifier.padding(end = 7.dp).size(10.dp).clip(CircleShape).background(hex(club.color)))
            Tx(cells[1], sz, if (head) 500 else 400, if (head) u.mu else u.ink, maxLines = 1)
        }
        for (i in 2 until cells.size) {
            val lastCol = i == cells.lastIndex
            Box(Modifier.width(if (lastCol) 34.dp else 30.dp).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                Tx(cells[i], sz, if (head) 500 else if (lastCol) 700 else 400, if (head) u.mu else u.ink)
            }
        }
    }
}

@Composable
fun LeagueDetail(vm: AppVM, id: String) {
    val u = LocalU.current
    val l = vm.lg(id) ?: return
    val C = u.s.cols
    val cs = vm.table(id)
    BackPill(vm.back?.label ?: "Back") { vm.goBack() }
    Ctr(l.name, "${flag(l.country)}${listOf(l.country, l.season).filter { it.isNotBlank() }.joinToString(" · ")}", "${cs.size} clubs") {
        Crest(l.color, "", l.logo, ab(l.name, ""), 112.dp, true)
    }
    val banner = rememberBitmap(l.banner)
    if (banner != null) Image(banner, null, Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp).fillMaxWidth().height(84.dp)
        .clip(u.rs(4)), contentScale = ContentScale.Crop)
    else Spacer(Modifier.height(150.dp))
    KvPanel(listOf("Points per win" to "${l.pw}", "Points per draw" to "${l.pd}", "Promotion spots" to "${l.promo}", "Relegation spots" to "${l.rel}"))
    Panel(h = 10.dp, v = 6.dp) {
        if (cs.isEmpty()) Box(Modifier.fillMaxWidth().padding(26.dp), contentAlignment = Alignment.Center) { Tx("No clubs yet.", 14 * u.fs, c = u.mu) }
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
    Spacer(Modifier.height(100.dp))
}

// =============================================================== forms
@Composable
fun FRow(gap: Int = 12, content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp), content = content)

@Composable
fun LeagueForm(vm: AppVM, d: Draft) {
    val ed = d.eid != null
    BackPill(vm.back?.label ?: "Back") { vm.goBack() }
    Tp(if (ed) "Edit league" else "New league", "League details")
    Column(Modifier.bd(bottom = 20.dp)) {
        Fld("League name", d["name"], { d["name"] = it }, ph = "Premier League")
        FRow {
            CountryField(d["country"], { d["country"] = it }, Modifier.weight(1f))
            Fld("Season", d["season"], { d["season"] = it }, Modifier.weight(1f), ph = "2026/27")
        }
        Upload("Logo", d, "logo")
        Upload("Banner", d, "banner", 4f)
        ColorField("League colour", d["color"]) { d["color"] = it }
        FRow { Fld("Points per win", d["pw"], { d["pw"] = it }, Modifier.weight(1f), number = true); Fld("Points per draw", d["pd"], { d["pd"] = it }, Modifier.weight(1f), number = true) }
        FRow { Fld("Promotion spots", d["promo"], { d["promo"] = it }, Modifier.weight(1f), number = true); Fld("Relegation spots", d["rel"], { d["rel"] = it }, Modifier.weight(1f), number = true) }
    }
    if (ed) DeleteBtn("Delete league and clubs") { vm.deleteLeague(d.eid!!) }
    Spacer(Modifier.height(100.dp))
}

@Composable
fun ClubForm(vm: AppVM, d: Draft) {
    val u = LocalU.current
    val ed = d.eid != null
    BackPill(vm.back?.label ?: "Back") { vm.goBack() }
    Tp(if (ed) "Edit club" else "New club", "Club details")
    Column(Modifier.bd(bottom = 20.dp)) {
        Fld("Club name", d["name"], { d["name"] = it }, ph = "Metalist")
        FRow { Fld("Short name", d["short"], { d["short"] = it }, Modifier.weight(1f), ph = "ME"); Fld("Nickname", d["nick"], { d["nick"] = it }, Modifier.weight(1f)) }
        LeagueSelect(d, vm.leagues)
        FRow { Fld("City", d["city"], { d["city"] = it }, Modifier.weight(1f)); Fld("Founded", d["founded"], { d["founded"] = it }, Modifier.weight(1f), number = true, ph = "1925") }
        FRow { Fld("Stadium", d["stadium"], { d["stadium"] = it }, Modifier.weight(1f)); Fld("Capacity", d["cap"], { d["cap"] = it }, Modifier.weight(1f), number = true) }
        FRow { Fld("Manager", d["mgr"], { d["mgr"] = it }, Modifier.weight(1f)); Fld("Chairman", d["chair"], { d["chair"] = it }, Modifier.weight(1f)) }
        Upload("Crest image (optional)", d, "logo")
        if (d["logo"].isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Panel(side = 0.dp, bottom = 0.dp) {
                KvRow(true) {
                    Tx("Show club colours behind logo", 14 * u.fs, c = u.mu, modifier = Modifier.weight(1f))
                    Toggle(d["logoBg"] == "1") { d["logoBg"] = if (d["logoBg"] == "1") "0" else "1" }
                }
            }
        }
        ColorField("Main colour", d["color"]) { d["color"] = it }
        ColorField("Second colour", d["c2"]) { d["c2"] = it }
        FRow(10) { for ((k, t) in listOf("w" to "Won", "d" to "Drawn", "l" to "Lost")) Fld(t, d[k], { d[k] = it }, Modifier.weight(1f), number = true) }
        FRow(10) { for ((k, t) in listOf("gf" to "Goals for", "ga" to "Against", "titles" to "Titles")) Fld(t, d[k], { d[k] = it }, Modifier.weight(1f), number = true) }
        Fld("Honours", d["hon"], { d["hon"] = it }, ph = "One per line", multi = true)
        Fld("About", d["info"], { d["info"] = it }, multi = true)
    }
    if (ed) DeleteBtn("Delete club") { vm.deleteClub(d.eid!!) }
    Spacer(Modifier.height(100.dp))
}

// =============================================================== settings
@Composable
private fun Seg(vm: AppVM, label: String, key: String, opts: List<Pair<String, String>>) {
    val u = LocalU.current
    val cur = u.s.at(key)
    Lbl(label)
    Row(Modifier.fillMaxWidth().clip(u.rs(4)).background(u.bg).border(1.dp, u.ln, u.rs(4)).padding(3.dp)) {
        opts.forEach { (v, t) ->
            val on = v == cur
            Box(Modifier.weight(1f).clip(u.rs(8)).background(if (on) u.ink else Color.Transparent).clickable { vm.set(key, v) }
                .padding(horizontal = 3.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Tx(t, 14 * u.fs * .93f, 500, if (on) u.bg else u.mu, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Rg(label: String, v: Int, max: Int, onV: (Int) -> Unit) {
    val u = LocalU.current
    KvRow(true, Modifier.padding(bottom = 0.dp)) { Tx(label, 14 * u.fs, c = u.mu); Tx("$v", 14 * u.fs, 600) }
    Slider(v.toFloat(), { onV(it.roundToInt()) }, valueRange = 0f..max.toFloat(),
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = u.acc, inactiveTrackColor = u.ln))
}

@Composable
private fun Sec(t: String, content: @Composable ColumnScope.() -> Unit) {
    H3(t)
    Panel(side = 0.dp, bottom = 6.dp) { content(); Spacer(Modifier.height(8.dp)) }
}

@Composable
private fun Toggles(items: List<Pair<String, String>>, on: (String) -> Boolean, flip: (String) -> Unit) {
    val u = LocalU.current
    items.forEachIndexed { i, (k, t) ->
        KvRow(i == items.lastIndex) { Tx(t, 14 * u.fs, c = u.mu); Toggle(on(k)) { flip(k) } }
    }
}

private val ACCENTS = listOf("#ffffff", "#b6f24b", "#ff8a3d", "#38bdf8", "#f472b6", "#a78bfa", "#16161a")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppVM) {
    val u = LocalU.current
    val s = u.s
    val pc = vm.clubs.firstOrNull() ?: Club("x", "", "Sample FC", "SFC", color = "#0ea5e9", c2 = "#ffffff", w = 3, d = 1)
    BackPill(vm.back?.label ?: "Back") { vm.goBack() }
    Tp("Customise", "Make it yours")
    HChips(PRESETS.map { it to it }, null) { vm.applyPreset(it) }
    Column(Modifier.bd(top = 14.dp)) {
        Panel(side = 0.dp, bottom = 6.dp, v = 12.dp) { Row(Modifier.fillMaxWidth()) { ClubRow(vm, pc, plain = true) } }
        Sec("Colour") {
            Seg(vm, "Theme", "theme", listOf("auto" to "Auto", "dark" to "Dark", "light" to "Light"))
            Lbl("Accent")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENTS.forEach { c -> Swatch(c, s.acc.equals(c, true)) { vm.set("acc", c) } }
                CustomSwatch(s.acc) { vm.set("acc", it) }
            }
            Seg(vm, "Background glow", "g", listOf("bold" to "Bold", "soft" to "Soft", "mono" to "Mono"))
        }
        Sec("Shape") {
            Rg("Corner radius", s.r, 32) { v -> vm.update { it.copy(r = v) } }
            Seg(vm, "Crest shape", "c", listOf("circle" to "Circle", "square" to "Soft", "shield" to "Shield", "hex" to "Hex"))
            Spacer(Modifier.height(8.dp))
            Rg("Crest ring", s.ring, 14) { v -> vm.update { it.copy(ring = v) } }
        }
        Sec("Type") {
            Seg(vm, "Typeface", "f", listOf("sans" to "Condensed", "rounded" to "Rounded", "serif" to "Serif", "mono" to "Mono"))
            Seg(vm, "Text size", "fs", listOf("s" to "Small", "m" to "Medium", "l" to "Large"))
        }
        Sec("Layout") {
            Seg(vm, "Spacing", "d", listOf("cozy" to "Cozy", "compact" to "Compact"))
            Seg(vm, "Navigation", "navs", listOf("float" to "Floating", "bar" to "Bar"))
            Seg(vm, "Active tab label", "lbl", listOf("on" to "Show", "off" to "Hide"))
            Seg(vm, "League list", "lay", listOf("cards" to "Cards", "list" to "List"))
            Seg(vm, "Club list", "cl", listOf("list" to "List", "grid" to "Grid"))
            Seg(vm, "Club names", "nm", listOf("full" to "Full", "short" to "Short"))
        }
        Sec("Table columns") {
            Toggles(listOf("w" to "Wins", "d" to "Draws", "l" to "Losses", "gd" to "Goal difference"), { it in s.cols }) { vm.toggleCol(it) }
        }
        Sec("Club page") {
            Toggles(
                listOf("stadium" to "Stadium", "colours" to "Colours", "titles" to "League titles", "points" to "Points", "nick" to "Nickname",
                    "mgr" to "Manager", "chair" to "Chairman", "cap" to "Capacity", "played" to "Played", "rec" to "Won / drawn / lost",
                    "goals" to "Goals", "hon" to "Honours", "about" to "About"),
                { it in s.fields }) { vm.toggleField(it) }
        }
        H3("Data")
        Btn("Reset style", "s", 0.dp) { vm.reset() }
        Btn("Load sample data", "s", 0.dp) { vm.loadSample() }
        DeleteBtn("Clear all data", 0.dp) { vm.clearAll() }
    }
}
