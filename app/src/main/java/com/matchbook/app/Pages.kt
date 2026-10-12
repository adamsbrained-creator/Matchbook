package com.matchbook.app

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlin.math.roundToInt

// =============================================================== shell
/** Loads res/font/<name>.ttf if you add it (manrope, anton, inter, nunito); otherwise uses the fallback. */
@Composable
private fun rememberFont(res: String, fallback: FontFamily): FontFamily {
    val ctx = LocalContext.current
    return remember(res) {
        val id = ctx.resources.getIdentifier(res, "font", ctx.packageName)
        if (id != 0) FontFamily(Font(id)) else fallback
    }
}

/** A variable font file (res/font/<name>.ttf) used for every weight, or the system font when it is missing. */
@Composable
private fun rememberVarFont(name: String): FontFamily {
    val ctx = LocalContext.current
    return remember(name) {
        val id = ctx.resources.getIdentifier(name, "font", ctx.packageName)
        if (id == 0) FontFamily.Default
        else FontFamily(
            Font(id, FontWeight.Normal), Font(id, FontWeight.Medium), Font(id, FontWeight.SemiBold),
            Font(id, FontWeight.Bold), Font(id, FontWeight.ExtraBold),
        )
    }
}

fun pageHex(vm: AppVM, sc: Screen?): String {
    val c: String? = when (sc) {
        is Screen.ClubS -> vm.cl(sc.id)?.color
        is Screen.LeagueS -> vm.lg(sc.id)?.color
        is Screen.TableS -> vm.lg(sc.id)?.color
        is Screen.SquadS -> vm.cl(sc.cid)?.color
        is Screen.LineupS -> vm.cl(sc.cid)?.color
        is Screen.FormP -> vm.cl(sc.draft["cid"])?.color
        is Screen.FormC -> sc.draft["color"]
        is Screen.FormL -> sc.draft["color"]
        else -> null
    }
    return c ?: vm.pickColor()
}

@Composable
fun MatchbookApp(vm: AppVM) {
    val s = vm.settings
    val ctx = LocalContext.current
    val dark = when (s.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val manrope = rememberVarFont("manrope")
    val outfit = rememberVarFont("outfit")
    val anton = rememberFont("anton", FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD)))
    val inter = rememberFont("inter", FontFamily.Default)
    val nunito = rememberFont("nunito", FontFamily.SansSerif)
    val u = remember(s, dark, manrope, anton, inter, nunito, outfit) { U(s, dark, manrope, anton, inter, nunito, outfit) }
    val cfg = LocalConfiguration.current
    val land = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE && cfg.screenWidthDp >= 640

    // Status bar follows the page header: light icons on dark glow, dark icons on light glow
    val hdr = u.hdrFor(pageC(if (land) vm.pickColor() else pageHex(vm, vm.screen)))
    val view = LocalView.current
    val showSplash = s.splash && !vm.splashDone
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = if (showSplash) false else !hdr.light
            isAppearanceLightNavigationBars = if (showSplash) false else !dark
        }
    }
    BackHandler(enabled = vm.screen != null || vm.navOpen) { if (vm.navOpen) vm.navOpen = false else vm.goBack() }
    LaunchedEffect(vm.msg) { vm.msg?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.msg = null } }

    CompositionLocalProvider(LocalU provides u, LocalVm provides vm) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = u.acc) else lightColorScheme(primary = u.acc)) {
            Box(Modifier.fillMaxSize().background(u.bg), contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier.widthIn(max = 1180.dp).fillMaxWidth().fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .imePadding(),
                ) { if (land) Landscape(vm) else Portrait(vm) }
            }
            vm.pending?.let { RestoreDialog(vm, it) }
            if (showSplash) LaunchSplash { vm.splashDone = true }
        }
    }
}

@Composable
fun Portrait(vm: AppVM) {
    val u = LocalU.current
    val sc = vm.screen
    val form = sc is Screen.FormL || sc is Screen.FormC || sc is Screen.FormF || sc is Screen.FormP
    val rail = !u.coral && u.s.navs == "rail" && !form
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavPad provides (if (rail) 64.dp else 0.dp)) { SectionPane(vm, Modifier.fillMaxSize()) }
        if (!form) { if (u.coral) CoralOverlay(vm) else NavHost(vm) }
    }
}

@Composable
fun Landscape(vm: AppVM) {
    val u = LocalU.current
    Row(Modifier.fillMaxSize()) {
        NavBar(vm, true)
        Box(Modifier.width(320.dp).fillMaxHeight().drawBehind {
            drawLine(u.ln, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
        }) {
            key(vm.tab) { Page(vm.pickColor(), false, Modifier.fillMaxSize()) { ListV(vm) } }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (vm.screen == null) Page(vm.pickColor(), false, Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = 140.dp), contentAlignment = Alignment.Center) {
                    Tx("Pick a club or league\nto see it here", 14 * u.fs, 500, u.mu, align = TextAlign.Center, lh = 14 * u.fs * 1.6f)
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
    val hh = u.glowHeight(big) + top
    val navPad = LocalNavPad.current
    if (u.coral) {
        // Coral: flat page, no colour glow
        Box(modifier.background(u.bg)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CompositionLocalProvider(LocalHdr provides hdr) {
                    Column(Modifier.fillMaxWidth().padding(top = top, end = navPad), content = content)
                }
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
            overlay()
        }
        return
    }
    Box(modifier.background(u.pageBg(c))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(hh)
                    .background(Brush.verticalGradient(0f to stops[0], .30f to stops[1], .62f to stops[2], 1f to stops[3])))
                CompositionLocalProvider(LocalHdr provides hdr) {
                    Column(Modifier.fillMaxWidth().padding(top = top, end = navPad), content = content)
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
    val big = (sc is Screen.ClubS || sc is Screen.LeagueS)
    key(vm.tab, sc) {
        Page(pageHex(vm, sc), big, modifier, overlay = {
            when (sc) {
                is Screen.FormL -> Box(Modifier.align(Alignment.BottomCenter)) { SaveBar("Save league", { vm.goBack() }) { if (vm.saveLeague(sc.draft)) null else "Add a name first" } }
                is Screen.FormC -> Box(Modifier.align(Alignment.BottomCenter)) { SaveBar("Save club", { vm.goBack() }) { if (vm.saveClub(sc.draft)) null else "Add a name and a league" } }
                is Screen.FormF -> Box(Modifier.align(Alignment.BottomCenter)) { SaveBar("Save fixture", { vm.goBack() }) { vm.saveFixture(sc.draft) } }
                is Screen.FormP -> Box(Modifier.align(Alignment.BottomCenter)) { SaveBar("Save player", { vm.goBack() }) { vm.savePlayer(sc.draft) } }
                else -> {}
            }
        }) { if (sc == null) ListV(vm) else Detail(vm, sc) }
    }
}

@Composable
fun SaveBar(label: String, onCancel: () -> Unit, onSave: () -> String?) {
    val u = LocalU.current
    Row(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, .35f to u.bg, 1f to u.bg))
            .windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) { Btn("Cancel", "s", 0.dp, 0.dp) { onCancel() } }
        Box(Modifier.weight(1f)) { SaveBtn(label, 0.dp, onSave) }
    }
}

@Composable
fun Detail(vm: AppVM, sc: Screen) {
    when (sc) {
        is Screen.ClubS -> ClubDetail(vm, sc.id)
        is Screen.LeagueS -> LeagueDetail(vm, sc.id)
        is Screen.TableS -> TablePage(vm, sc.id)
        is Screen.FixturesS -> FixturesPage(vm, sc.lid)
        Screen.SettingsS -> SettingsScreen(vm)
        is Screen.SettingsSubS -> SettingsSub(vm, sc.page)
        Screen.CompareS -> ComparePage(vm)
        is Screen.FormL -> LeagueForm(vm, sc.draft)
        is Screen.FormC -> ClubForm(vm, sc.draft)
        is Screen.FormF -> FixtureForm(vm, sc.draft)
        is Screen.FormP -> PlayerForm(vm, sc.draft)
        is Screen.SquadS -> SquadPage(vm, sc.cid)
        is Screen.LineupS -> LineupPage(vm, sc.cid)
        Screen.MenuS -> MenuPage(vm)
    }
}

// =============================================================== header pieces
@Composable
fun CircleBtn(icon: String, onClick: () -> Unit, on: Boolean = false) {
    val h = LocalHdr.current
    val u = LocalU.current
    val lift = if (u.coral && !u.dark) Modifier.shadow(8.dp, CircleShape, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000)) else Modifier
    Box(
        Modifier.size(48.dp).then(lift).clip(CircleShape).background(if (on) u.pri else h.chip)
            .border(1.dp, h.chipEdge, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, 22.dp, if (on) u.onPri else h.fg, 1.9f) }
}

/**
 * Page title block. [lead] marks a top-level page (Clubs, Leagues, ...): there Coral adds its menu bar and the
 * Glass navigation variants add their header pieces (drawer button, tabs, extra buttons).
 */
@Composable
fun Tp(title: String, sub: String, trailing: (@Composable () -> Unit)? = null, lead: Boolean = false) {
    val u = LocalU.current
    val h = LocalHdr.current
    val vm = LocalVm.current
    if (u.coral) {
        if (lead) CoralBar(left = { CircleBtn("menu", { vm.openMenu() }) }, right = trailing)
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = if (lead) 24.dp else 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Hd(title, 38f, h.fg, maxLines = 1)
            Tx(sub, 14 * u.fs, 500, u.mu, Modifier.padding(bottom = 7.dp), maxLines = 1)
        }
        return
    }
    val nv = u.s.navs
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (lead && nv == "drawer") {
                CircleBtn("menu", { vm.navOpen = true })
                Spacer(Modifier.height(12.dp))
            }
            Tx(sub, 13 * u.fs, 700, h.fg.copy(alpha = .92f))
            Spacer(Modifier.height(2.dp))
            Hd(if (u.cond) title.uppercase() else title, if (u.cond) 38f else 34f, h.fg, maxLines = 1)
        }
        trailing?.invoke()
        if (lead && nv == "tabs") { Spacer(Modifier.width(8.dp)); CircleBtn("gear", { vm.openSettings() }, navOn(vm, "settings")) }
        if (lead && nv == "dots") { Spacer(Modifier.width(8.dp)); CircleBtn("plus", { vm.selectTab("add") }, navOn(vm, "add")) }
    }
    if (lead && nv == "tabs") HeaderTabs(vm)
}

@Composable
fun SearchBtn(vm: AppVM) = CircleBtn("search", { vm.toggleSearch() }, vm.searchOpen)

/** Back pill on the left, optional round action (for example edit) on the right. Coral shows a round back button. */
@Composable
fun TopRow(label: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val u = LocalU.current
    val h = LocalHdr.current
    if (u.coral) {
        CoralBar(left = { CircleBtn("back", onBack) }, right = trailing)
        return
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.height(44.dp).clip(CircleShape).background(h.chip).border(1.dp, h.chipEdge, CircleShape).clickable(onClick = onBack)
                .padding(start = 10.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Ico("back", 22.dp, h.fg, 2f)
            Tx(label, 14 * u.fs, 700, h.fg, Modifier.widthIn(max = 190.dp), maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** Centered title block used by the club and league pages. */
@Composable
fun Ctr(title: String, sub: String, size: Float, pill: String? = null, crest: @Composable () -> Unit) {
    val u = LocalU.current
    val h = LocalHdr.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        crest()
        Spacer(Modifier.height(14.dp))
        Hd(if (u.cond) title.uppercase() else title, if (u.cond) size + 8 else size, h.fg, align = TextAlign.Center)
        if (sub.isNotBlank()) Tx(sub, 13 * u.fs, 600, if (u.coral) u.mu else h.fg.copy(alpha = .9f), Modifier.padding(top = 6.dp), align = TextAlign.Center)
        if (pill != null) {
            val lift = if (u.coral && !u.dark) Modifier.shadow(8.dp, CircleShape, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000)) else Modifier
            Row(
                Modifier.padding(top = 14.dp).then(lift).clip(CircleShape).background(h.chip).border(1.dp, h.chipEdge, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (u.coral) u.acc else h.fg))
                Tx(pill, 13 * u.fs, 800, h.fg)
            }
        }
    }
}

// =============================================================== shared cards
fun ordinal(n: Int): String = n.toString() + (if (n in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" })

@Composable
fun FixCrest(c: Club?) {
    val u = LocalU.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        if (c != null) {
            Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 36.dp, backdrop = c.logoBg)
            Tx(c.short.ifBlank { c.name.take(3).uppercase() }, 11 * u.fs, 700, u.mu, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
        } else Spacer(Modifier.size(36.dp))
    }
}

@Composable
fun NextFixtureCard(vm: AppVM, f: Fixture, title: String = "Next fixture") {
    val u = LocalU.current
    Glass(
        Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = minOf(u.r, 26.dp),
        pad = PaddingValues(horizontal = 18.dp, vertical = 14.dp), onClick = { vm.openFixtures(f.lid) },
    ) {
        Tx(title, 12 * u.fs, 500, u.mu)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FixCrest(vm.cl(f.home))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Tx(fmtFixture(f.at), 16 * u.fs, 800)
                Tx(f.venue.ifBlank { vm.lg(f.lid)?.name ?: "" }, 12 * u.fs, 500, u.mu, maxLines = 1)
            }
            FixCrest(vm.cl(f.away))
        }
    }
}

// =============================================================== clubs
@Composable
fun ClubCard(vm: AppVM, c: Club, style: String, modifier: Modifier = Modifier) {
    val u = LocalU.current
    val league = vm.lg(c.lid)?.name ?: ""
    val shape = RoundedCornerShape(if (style == "comp") minOf(u.r, 22.dp) else u.r)
    val base = modifier.glass(shape).clickable { vm.openClub(c.id) }
    when (style) {
        "list" -> Row(base.padding(horizontal = 16.dp, vertical = u.pad), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 48.dp, backdrop = c.logoBg)
            Column(Modifier.weight(1f)) {
                Tx(vm.nm(c), 16 * u.fs, 800, maxLines = 1)
                Tx(league, 12 * u.fs, 500, u.mu, maxLines = 1)
            }
            if (u.coral) Tx("${vm.pts(c)} pts", 14 * u.fs, 500, u.mu) else Hd("${vm.pts(c)}", 26f, w = 600)
        }
        "comp" -> Row(base.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 34.dp, backdrop = c.logoBg)
            Tx(vm.nm(c), 14 * u.fs, 800, modifier = Modifier.weight(1f), maxLines = 1)
            Tx(league, 11 * u.fs, 500, u.mu, maxLines = 1, modifier = Modifier.widthIn(max = 90.dp))
            Hd("${vm.pts(c)}", 18f, w = 700)
        }
        "g3" -> Column(base.padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 40.dp, backdrop = c.logoBg)
            Tx(vm.nm(c), 13 * u.fs, 800, align = TextAlign.Center, maxLines = 2)
            Hd("${vm.pts(c)}", 22f, w = 500)
        }
        else -> Column(base.padding(horizontal = 12.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 48.dp, backdrop = c.logoBg)
            Spacer(Modifier.height(12.dp))
            Tx(vm.nm(c), 15 * u.fs, 800, align = TextAlign.Center, maxLines = 2)
            Tx(league, 12 * u.fs, 500, u.mu, align = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            Hd("${vm.pts(c)}", 28f, w = 500)
        }
    }
}

@Composable
fun ClubsGrid(vm: AppVM, list: List<Club>) {
    val style = vm.settings.cg
    val cols = when (style) { "g2" -> 2; "g3" -> 3; else -> 1 }
    val gap = if (style == "g3") 12.dp else 16.dp
    if (cols == 1) {
        list.forEach { ClubCard(vm, it, style, Modifier.fillMaxWidth().padding(bottom = if (style == "comp") 10.dp else 14.dp)) }
    } else list.chunked(cols).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = gap).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
            row.forEach { ClubCard(vm, it, style, Modifier.weight(1f).fillMaxHeight()) }
            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun LeadersCard(vm: AppVM) {
    val u = LocalU.current
    val rows = vm.leagues.mapNotNull { l -> vm.table(l.id).firstOrNull()?.let { l to it } }
    if (rows.isEmpty()) return
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Tx("League leaders", 12 * u.fs, 500, u.mu)
        rows.forEachIndexed { i, (l, c) ->
            KvRow(i == rows.lastIndex, Modifier.clickable { vm.openClub(c.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 34.dp, backdrop = c.logoBg)
                    Column {
                        Tx(vm.nm(c), 14 * u.fs, 800, maxLines = 1)
                        Tx(l.name, 12 * u.fs, 500, u.mu, maxLines = 1)
                    }
                }
                Tx("${vm.pts(c)} pts", 14 * u.fs, 800)
            }
        }
    }
}

// =============================================================== leagues
@Composable
fun LeagueSummary(vm: AppVM, l: League) {
    val u = LocalU.current
    val lead = vm.table(l.id).firstOrNull()
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), radius = minOf(u.r, 26.dp), pad = PaddingValues(vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            @Composable fun cell(v: String, k: String) = Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Tx(v, 20 * u.fs, 800, maxLines = 1)
                Tx(k, 12 * u.fs, 500, u.mu, maxLines = 1)
            }
            cell(l.season.ifBlank { "-" }, "Season")
            cell("${l.pw} · ${l.pd}", "Win · draw pts")
            cell(if (lead != null) "1st" else "-", lead?.let { vm.nm(it) } ?: "No clubs")
        }
    }
}

@Composable
fun LeagueCard(vm: AppVM, l: League, style: String, modifier: Modifier = Modifier) {
    val u = LocalU.current
    val n = vm.clubs.count { it.lid == l.id }
    val sub = listOf(flag(l.country) + l.country, "$n clubs").filter { it.isNotBlank() }.joinToString(" · ")
    val c = hex(l.color)
    val m = modifier.glass().clickable { vm.openLeague(l.id) }
    when (style) {
        "cards" -> Column(m) {
            val bmp = rememberBitmap(l.banner)
            Box(Modifier.fillMaxWidth().height(104.dp).background(Brush.linearGradient(listOf(lerp(c, Color.White, .12f), c, lerp(Color.Black, c, .55f))))) {
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Crest(l.color, "", l.logo, ab(l.name, ""), 52.dp)
                Column(Modifier.weight(1f)) {
                    Tx(l.name, 16 * u.fs, 800, maxLines = 1)
                    Tx(sub, 13 * u.fs, 500, u.mu, maxLines = 1)
                }
            }
        }
        "g2" -> Column(m.padding(horizontal = 12.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Crest(l.color, "", l.logo, ab(l.name, ""), 56.dp)
            Spacer(Modifier.height(12.dp))
            Tx(l.name, 15 * u.fs, 800, align = TextAlign.Center, maxLines = 2)
            Tx(sub, 12 * u.fs, 500, u.mu, align = TextAlign.Center, maxLines = 2)
        }
        else -> Row(m.padding(horizontal = 16.dp, vertical = u.pad), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Crest(l.color, "", l.logo, ab(l.name, ""), 48.dp)
            Column(Modifier.weight(1f)) {
                Tx(l.name, 16 * u.fs, 800, maxLines = 1)
                Tx(sub, 13 * u.fs, 500, u.mu, maxLines = 1)
            }
        }
    }
}

@Composable
fun MiniTable(vm: AppVM, l: League) {
    val u = LocalU.current
    val rows = vm.table(l.id).take(3)
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tx(l.name, 14 * u.fs, 800, modifier = Modifier.weight(1f), maxLines = 1)
            Box(Modifier.clip(CircleShape).background(u.acc).clickable { vm.openTable(l.id) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Tx("Full table", 12 * u.fs, 800, u.on)
            }
        }
        if (rows.isEmpty()) Tx("No clubs yet", 13 * u.fs, 500, u.mu, Modifier.padding(top = 12.dp))
        rows.forEachIndexed { i, c ->
            KvRow(i == rows.lastIndex, Modifier.clickable { vm.openClub(c.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Tx("${i + 1}", 14 * u.fs, 800, modifier = Modifier.width(18.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(hex(c.color)))
                    Tx(vm.nm(c), 14 * u.fs, 600, maxLines = 1)
                }
                Tx("${vm.pts(c)}", 14 * u.fs, 800)
            }
        }
    }
}

/** Shows how leagues feed into each other through promotion and relegation links. */
@Composable
fun PyramidCard(vm: AppVM) {
    val u = LocalU.current
    val ls = vm.leagues
    if (ls.size < 2) return
    val above = HashMap<String, MutableSet<String>>()           // league id -> leagues directly above it
    for (l in ls) {
        if (l.promoTo.isNotBlank() && vm.lg(l.promoTo) != null) above.getOrPut(l.id) { mutableSetOf() }.add(l.promoTo)
        if (l.relTo.isNotBlank() && vm.lg(l.relTo) != null) above.getOrPut(l.relTo) { mutableSetOf() }.add(l.id)
    }
    val depth = HashMap<String, Int>()
    repeat(ls.size + 1) {
        for (l in ls) depth[l.id] = (above[l.id]?.maxOfOrNull { (depth[it] ?: 0) + 1 } ?: 0).coerceAtMost(ls.size)
    }
    val levels = ls.groupBy { depth[it.id] ?: 0 }.toSortedMap()
    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Tx("League pyramid", 12 * u.fs, 500, u.mu)
        levels.entries.forEachIndexed { i, (lv, list) ->
            if (i > 0) Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico("up", 14.dp, u.acc, 2f); Ico("down", 14.dp, u.red, 2f)
                Tx("promotion / relegation", 11 * u.fs, 500, u.mu)
            }
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tx("L${lv + 1}", 12 * u.fs, 800, u.mu, Modifier.align(Alignment.CenterVertically))
                list.forEach { l ->
                    Row(
                        Modifier.clip(CircleShape).border(1.dp, u.outline.copy(alpha = u.outline.alpha * .7f), CircleShape).clickable { vm.openLeague(l.id) }
                            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Crest(l.color, "", l.logo, ab(l.name, ""), 28.dp)
                        Tx(l.name, 13 * u.fs, 700, maxLines = 1)
                    }
                }
            }
        }
    }
}

// =============================================================== list pages
@Composable
fun ListV(vm: AppVM) {
    val u = LocalU.current
    val B = u.s.blocks
    when (vm.tab) {
        "clubs" -> {
            val leagueWord = if (vm.leagues.size == 1) "league" else "leagues"
            Tp("Clubs", if (u.coral) "${vm.clubs.size} clubs" else "${vm.clubs.size} clubs · ${vm.leagues.size} $leagueWord", { SearchBtn(vm) }, lead = true)
            if (vm.searchOpen) SearchField(vm.query, "Search clubs", { vm.query = it }) { vm.toggleSearch() }
            if (u.coral) CoralClubFilter(vm)
            else HChips(listOf("all" to "All") + vm.leagues.map { it.id to it.name }, vm.clubFilter) { vm.clubFilter = it }
            val shown = vm.shownClubs()
            Column(Modifier.bd(top = 16.dp)) {
                if (shown.isEmpty()) EmptyBlock(if (vm.query.isNotBlank()) "No clubs match \"${vm.query.trim()}\"." else "No clubs here yet.") {
                    if (vm.query.isBlank()) {
                        SmallBtn("Add a club") { vm.newClub() }
                        SmallBtn("Load sample data", "s") { vm.loadSample() }
                    }
                } else ClubsGrid(vm, shown)
                if ("c_fixture" in B) vm.nextFixture(vm.clubFilter.takeIf { it != "all" })?.let { NextFixtureCard(vm, it) }
                if ("c_leaders" in B) LeadersCard(vm)
                if ("c_actions" in B) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DashedBtn("Add club", "plus", Modifier.weight(1f)) { vm.newClub(vm.clubFilter.takeIf { it != "all" }) }
                    GlassBtn("Compare", "compare", Modifier.weight(1f)) { vm.openCompare() }
                }
            }
        }
        "leagues" -> {
            Tp("Leagues", if (u.coral) "${vm.leagues.size} leagues" else "Your competitions", { SearchBtn(vm) }, lead = true)
            if (vm.searchOpen) SearchField(vm.query, "Search leagues", { vm.query = it }) { vm.toggleSearch() }
            val shown = vm.shownLeagues()
            val style = u.s.lg
            Column(Modifier.bd(top = 24.dp)) {
                if (shown.isEmpty()) EmptyBlock(if (vm.query.isNotBlank()) "No leagues match \"${vm.query.trim()}\"." else "No leagues yet.") {
                    if (vm.query.isBlank()) {
                        SmallBtn("Create a league") { vm.newLeague() }
                        SmallBtn("Load sample data", "s") { vm.loadSample() }
                    }
                } else if (style == "g2") shown.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { LeagueCard(vm, it, style, Modifier.weight(1f).fillMaxHeight()) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                } else shown.forEach { l ->
                    LeagueCard(vm, l, style, Modifier.fillMaxWidth().padding(bottom = 14.dp))
                    if ("l_summary" in B) LeagueSummary(vm, l)
                }
                if ("l_tables" in B) shown.forEach { MiniTable(vm, it) }
                if ("l_pyramid" in B) PyramidCard(vm)
                if ("l_add" in B) DashedBtn("Add league", "plus", Modifier.fillMaxWidth()) { vm.newLeague() }
            }
        }
        "add" -> {
            Tp("Add", "Create something new", lead = true)
            Column(Modifier.bd(top = 24.dp)) {
                @Composable
                fun opt(icon: String, t: String, sub: String, go: () -> Unit) =
                    Glass(Modifier.fillMaxWidth().padding(bottom = 14.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 16.dp), onClick = go) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(u.acc.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Ico(icon, 22.dp, u.acc, 1.9f) }
                            Column(Modifier.weight(1f)) {
                                Tx(t, 16 * u.fs, 800)
                                Tx(sub, 13 * u.fs, 500, u.mu)
                            }
                            Ico("chev", 18.dp, u.mu, 2f)
                        }
                    }
                opt("shield", "New club", "Crest, colours, stadium, stats and history") { vm.newClub() }
                opt("trophy", "New league", "Logo, banner, season, rules and links") { vm.newLeague() }
                opt("cal", "New fixture", "Schedule a match between two clubs") { vm.newFixture() }
                opt("compare", "Compare clubs", "Put two clubs side by side") { vm.openCompare() }
            }
        }
        else -> {
            Tp("Stats", "Season overview", lead = true)
            HChips(listOf("all" to "All") + vm.leagues.map { it.id to it.name }, vm.statsLeague) { vm.statsLeague = it }
            val cs = vm.clubs.filter { vm.statsLeague == "all" || it.lid == vm.statsLeague }
            val g = cs.sumOf { it.gf }
            val m = (cs.sumOf { it.played() } / 2f).roundToInt()
            Column(Modifier.bd(top = 16.dp)) {
                if (cs.isEmpty()) EmptyBlock("No clubs to show yet.")
                else {
                    if ("s_overview" in B) TileGrid(listOf("Clubs" to "${cs.size}", "Matches played" to "$m", "Goals scored" to "$g",
                        "Goals per match" to (if (m > 0) "%.2f".format(g.toFloat() / m) else "0")), 2)
                    if ("s_top" in B) {
                        val tops = listOf(
                            Triple("Most points", -1, { c: Club -> vm.pts(c) }), Triple("Best attack", -1, { c: Club -> c.gf }),
                            Triple("Best defence", 1, { c: Club -> c.ga }), Triple("Most wins", -1, { c: Club -> c.w }),
                        )
                        if (u.s.sg == "g2") tops.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                row.forEach { (t, dir, fn) -> TopTile(vm, t, cs, dir, fn, Modifier.weight(1f).fillMaxHeight()) }
                            }
                        } else tops.forEach { (t, dir, fn) -> TopList(vm, t, cs, dir, fn) }
                    }
                }
            }
        }
    }
}

@Composable
fun TopList(vm: AppVM, title: String, cs: List<Club>, dir: Int, fn: (Club) -> Int) {
    val u = LocalU.current
    H3(title)
    val rows = cs.sortedBy { dir * fn(it) }.take(3)
    Glass(Modifier.fillMaxWidth().padding(bottom = 6.dp), pad = PaddingValues(horizontal = 18.dp, vertical = 2.dp)) {
        rows.forEachIndexed { i, c ->
            KvRow(i == rows.lastIndex, Modifier.clickable { vm.openClub(c.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 38.dp, backdrop = c.logoBg)
                    Tx(vm.nm(c), 14 * u.fs, 700)
                }
                Tx("${fn(c)}", 15 * u.fs, 800)
            }
        }
    }
}

@Composable
fun TopTile(vm: AppVM, title: String, cs: List<Club>, dir: Int, fn: (Club) -> Int, modifier: Modifier) {
    val u = LocalU.current
    val c = cs.minByOrNull { dir * fn(it) } ?: return
    Column(modifier.glass().clickable { vm.openClub(c.id) }.padding(horizontal = 12.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Tx(title, 12 * u.fs, 700, u.mu, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        Crest(c.color, c.c2, c.logo, ab(c.name, c.short), 48.dp, backdrop = c.logoBg)
        Spacer(Modifier.height(10.dp))
        Tx(vm.nm(c), 14 * u.fs, 800, align = TextAlign.Center, maxLines = 2)
        Hd("${fn(c)}", 24f, w = 600, modifier = Modifier.padding(top = 4.dp))
    }
}
